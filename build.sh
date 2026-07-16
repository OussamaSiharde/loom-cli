#!/usr/bin/env bash
# Builds Loom with nothing but a JDK (17+). No Maven, no dependencies.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out/classes
find src/main/java -name '*.java' > out/sources.txt
javac --release 17 -d out/classes @out/sources.txt
cp -r src/main/resources/. out/classes/
jar --create --file out/loom.jar --main-class dev.loom.Loom -C out/classes .
echo "Built out/loom.jar"
