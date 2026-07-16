package dev.loom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loom - weave production-grade Spring Boot projects from your terminal.
 *
 * Interactive by default; fully scriptable with flags. Zero runtime
 * dependencies: everything here is plain JDK.
 */
public final class Loom {

    static final String VERSION = "0.1.0";

    private static final Set<String> BOOL_FLAGS = Set.of(
            "tc", "jacoco", "lombok", "openapi", "actuator", "docker", "ci", "zip", "force");

    public static void main(String[] args) throws Exception {
        Map<String, String> flags = new LinkedHashMap<>();
        boolean yes = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h", "--help" -> { help(); return; }
                case "-V", "--version" -> { System.out.println("loom " + VERSION); return; }
                case "-y", "--yes" -> yes = true;
                default -> {
                    if (!a.startsWith("--")) {
                        System.err.println("Unknown argument: " + a + " (see --help)");
                        System.exit(2);
                    }
                    String k = a.substring(2);
                    String v;
                    int eq = k.indexOf('=');
                    if (eq >= 0) {
                        v = k.substring(eq + 1);
                        k = k.substring(0, eq);
                    } else if (k.startsWith("no-") && BOOL_FLAGS.contains(k.substring(3))) {
                        k = k.substring(3);
                        v = "false";
                    } else if (BOOL_FLAGS.contains(k)) {
                        v = "true";
                    } else {
                        if (i + 1 >= args.length) {
                            System.err.println("Missing value for --" + k);
                            System.exit(2);
                        }
                        v = args[++i];
                    }
                    flags.put(k, v);
                }
            }
        }

        Spec spec = Spec.defaults();
        spec.apply(flags);

        if (!yes) {
            banner();
            new Prompter(spec).run();
        }

        List<String> notes = spec.normalize();
        Resolver.Result result = Resolver.resolve(spec);
        result.warnings.addAll(0, notes);

        Path root = Paths.get(spec.out).resolve(spec.name).toAbsolutePath().normalize();
        if (Files.exists(root)) {
            if (!spec.force) {
                System.err.println("Refusing to overwrite existing directory: " + root);
                System.err.println("Use --force to replace it.");
                System.exit(1);
            }
            Renderer.deleteRecursively(root);
        }

        int count = Renderer.generate(result, root);

        System.out.println();
        System.out.println("Woven " + count + " files into " + root);
        System.out.println();
        @SuppressWarnings("unchecked")
        List<String> stack = (List<String>) result.model.get("stack");
        for (String line : stack) {
            System.out.println("  - " + line);
        }
        if (!result.warnings.isEmpty()) {
            System.out.println();
            System.out.println("Resolution notes:");
            for (String w : result.warnings) {
                System.out.println("  ! " + w);
            }
        }

        if (spec.zip) {
            Path zip = Renderer.zip(root);
            System.out.println();
            System.out.println("Archive: " + zip);
        }

        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  cd " + Paths.get(spec.out).resolve(spec.name));
        if (Boolean.TRUE.equals(result.model.get("anyInfraDocker"))) {
            System.out.println("  docker compose up -d");
        }
        System.out.println("  mvn verify");
        if (!Boolean.TRUE.equals(result.model.get("webNone"))) {
            System.out.println("  mvn spring-boot:run");
        }
    }

    private static void banner() {
        System.out.println();
        System.out.println("  loom " + VERSION + " - weave production-grade Spring Boot projects");
        System.out.println("  Enter accepts the [default]. Ctrl+C aborts.");
    }

    private static void help() {
        System.out.println("""
                loom %s - weave production-grade Spring Boot projects

                Usage:
                  loom                     interactive questionnaire
                  loom -y [flags]          non-interactive, defaults + flags
                  loom [flags]             interactive, flags become the defaults

                Project:
                  --name <artifactId>      project name (default: demo)
                  --group <groupId>        Maven group (default: com.example)
                  --package <pkg>          base package (default: derived)
                  --description <text>     project description
                  --out <dir>              output directory (default: .)
                  --force                  overwrite an existing directory
                  --zip                    also produce <name>.zip

                Stack:
                  --lang java|kotlin
                  --boot 3|2               Boot 3.x (Jakarta) or 2.7 (javax legacy)
                  --boot-version <v>       pin an exact Spring Boot version
                  --java 8|11|17|21
                  --web mvc|webflux|none
                  --server tomcat|undertow|jetty
                  --http none|restclient|webclient|resttemplate
                  --db postgres|mysql|mariadb|mongo|h2|none
                  --data auto|jpa|jdbc|r2dbc
                  --migrations flyway|liquibase|none
                  --cache none|caffeine|redis
                  --mq none|rabbitmq|kafka|artemis
                  --json jackson|gson
                  --logging logback|log4j2

                Quality:
                  --tests junit|testng
                  --mock mockito|easymock|none
                  --tc / --no-tc           Testcontainers (default: off)
                  --jacoco / --no-jacoco   coverage (default: on)
                  --lombok                 Lombok (Java only, default: off)
                  --openapi                springdoc + Swagger UI (default: off)
                  --actuator / --no-actuator   (default: on)
                  --docker / --no-docker   Dockerfile + compose (default: on)
                  --ci / --no-ci           GitHub Actions workflow (default: on)

                Examples:
                  loom
                  loom -y --name orders-api --db postgres --migrations flyway --tc --openapi
                  loom -y --name legacy-svc --boot 2 --java 11 --db mysql --tests testng
                  loom -y --name gateway --lang kotlin --web webflux --db postgres --cache redis
                """.formatted(VERSION));
    }
}
