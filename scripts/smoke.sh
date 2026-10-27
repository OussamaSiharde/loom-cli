#!/usr/bin/env bash
# Smoke-tests the generation matrix: five very different stacks, then checks
# that every file rendered fully (no leftover mustache tags, no "null" values)
# and that all generated XML parses.
set -euo pipefail
cd "$(dirname "$0")/.."
LOOM="java -jar out/loom.jar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

gen() {
  echo "== $*"
  $LOOM -y --out "$WORK" "$@" > /dev/null
}

# 1. Modern defaults
gen --name default-api

# 2. Enterprise Java: the works
gen --name enterprise-api --java 21 --web mvc --server undertow \
    --db postgres --data jpa --migrations flyway --cache redis --mq kafka \
    --logging log4j2 --http restclient --tc --lombok --openapi

# 3. Kotlin reactive
gen --name reactive-gw --lang kotlin --web webflux --db postgres --data r2dbc \
    --migrations liquibase --cache caffeine --mq rabbitmq --http webclient --tc

# 4. Legacy javax line
gen --name legacy-svc --boot 2 --java 11 --db mysql --data jpa --migrations flyway \
    --mq artemis --tests testng --mock easymock --http resttemplate --no-tc

# 5. Minimal library
gen --name tiny-lib --web none --db none --cache none --mq none \
    --no-jacoco --no-actuator --no-docker --no-ci --http none

fail=0
for d in "$WORK"/*/; do
  # unrendered template tags?
  if grep -R -n '{{' "$d" > /tmp/smoke-grep.txt 2>/dev/null; then
    echo "FAIL: unrendered tags in $d"; cat /tmp/smoke-grep.txt; fail=1
  fi
  # null leaked into any file?
  if grep -R -n '>null<\|: null$\|"null"' "$d" > /tmp/smoke-null.txt 2>/dev/null; then
    echo "FAIL: literal null in $d"; cat /tmp/smoke-null.txt; fail=1
  fi
  # all XML well-formed?
  while IFS= read -r x; do
    python3 - "$x" <<'PY' || { echo "FAIL: bad XML $x"; fail=1; }
import sys, xml.dom.minidom
xml.dom.minidom.parse(sys.argv[1])
PY
  done < <(find "$d" -name '*.xml')
  # all YAML parseable? (best effort, skipped if PyYAML missing)
  if python3 -c 'import yaml' 2>/dev/null; then
    while IFS= read -r y; do
      python3 - "$y" <<'PY' || { echo "FAIL: bad YAML $y"; fail=1; }
import sys, yaml
yaml.safe_load(open(sys.argv[1]))
PY
    done < <(find "$d" \( -name '*.yml' -o -name '*.yaml' \))
  fi
done

# spot-check resolution logic
grep -q 'spring-boot-starter-undertow' "$WORK/enterprise-api/pom.xml"
grep -q 'spring-boot-starter-logging' "$WORK/enterprise-api/pom.xml"   # exclusions present
grep -q 'flyway-database-postgresql' "$WORK/enterprise-api/pom.xml"
grep -q 'TestcontainersConfiguration' "$WORK/enterprise-api/src/test/java/com/example/enterpriseapi/EnterpriseApiApplicationTests.java"
grep -q 'kotlin-maven-plugin' "$WORK/reactive-gw/pom.xml"
grep -q 'r2dbc-postgresql' "$WORK/reactive-gw/pom.xml"
grep -q 'com.mysql' "$WORK/legacy-svc/pom.xml"                         # Boot 2.7.18 BOM manages mysql-connector-j only
grep -q 'testcontainers' "$WORK/legacy-svc/pom.xml" && { echo "FAIL: TC leaked into no-tc build"; fail=1; } || true
grep -q 'AbstractTestNGSpringContextTests' "$WORK/legacy-svc/src/test/java/com/example/legacysvc/LegacySvcApplicationTests.java"
test ! -f "$WORK/tiny-lib/Dockerfile"

if [ "$fail" -ne 0 ]; then echo "SMOKE FAILED"; exit 1; fi
echo "SMOKE OK: 5 stacks generated and validated."
