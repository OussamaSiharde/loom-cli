package dev.loom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link Spec} into everything the renderer needs: a template model,
 * a resolved dependency list (right artifacts, right versions, right
 * exclusions), warnings, and the list of files to generate.
 *
 * All pinned versions live in the catalog below - one place to bump.
 * Everything Spring-managed deliberately carries no version so the Boot BOM
 * stays the single source of truth.
 */
final class Resolver {

    // ------------------------------------------------------------------
    // Version catalog - the only place versions are pinned.
    // ------------------------------------------------------------------
    static final String BOOT3_DEFAULT = "3.5.6";
    static final String BOOT2_DEFAULT = "2.7.18";
    static final String KOTLIN_BOOT3 = "2.1.21";
    static final String KOTLIN_BOOT2 = "1.9.25";
    static final String SPRINGDOC_BOOT3 = "2.8.6";
    static final String SPRINGDOC_BOOT2 = "1.8.0";
    static final String TESTNG = "7.10.2";
    static final String TESTNG_JAVA8 = "7.5.1";   // last TestNG line supporting Java 8
    static final String EASYMOCK = "5.4.0";
    static final String EASYMOCK_JAVA8 = "4.3";   // EasyMock 5 requires Java 11+
    static final String JACOCO = "0.8.12";
    static final String TC_BOM_BOOT2 = "1.19.8";  // Boot 2.7 does not manage Testcontainers

    static final class Result {
        final Map<String, Object> model = new LinkedHashMap<>();
        final List<String> warnings = new ArrayList<>();
        final List<String[]> files = new ArrayList<>(); // {templateName, outputPath}
    }

    static Result resolve(Spec s) {
        Result r = new Result();
        Map<String, Object> m = r.model;

        boolean boot3 = s.boot3();
        boolean kotlin = s.lang.equals("kotlin");
        boolean webMvc = s.web.equals("mvc");
        boolean webFlux = s.web.equals("webflux");
        boolean webNone = s.web.equals("none");
        boolean sqlDb = s.sqlDb();
        boolean dataJpa = s.data.equals("jpa");
        boolean dataJdbc = s.data.equals("jdbc");
        boolean dataR2dbc = s.data.equals("r2dbc");
        boolean dataMongo = s.data.equals("mongo");
        boolean flyway = s.migrations.equals("flyway");
        boolean liquibase = s.migrations.equals("liquibase");
        boolean cacheRedis = s.cache.equals("redis");
        boolean cacheCaffeine = s.cache.equals("caffeine");
        boolean gson = s.json.equals("gson");
        boolean log4j2 = s.logging.equals("log4j2");
        boolean testng = s.tests.equals("testng");
        boolean easymock = s.mock.equals("easymock");
        boolean java8 = s.javaVersion.equals("8");

        String bootVersion = s.bootVersion != null ? s.bootVersion
                : (boot3 ? BOOT3_DEFAULT : BOOT2_DEFAULT);

        // --- Testcontainers wiring strategy ---
        boolean tcTestWired = s.tc && boot3 && !testng; // @ServiceConnection config + @Import in the test
        // When JPA/JDBC hits a real SQL server and containers aren't wired,
        // tests run against H2 in a Postgres/MySQL compatibility mode.
        boolean h2Override = (dataJpa || dataJdbc)
                && sqlDb && !s.db.equals("h2")
                && !tcTestWired;
        boolean contextTest = tcTestWired
                || s.db.equals("none") || s.db.equals("h2")
                || h2Override;

        // ------------------------------------------------------------------
        // Coordinates & identity
        // ------------------------------------------------------------------
        String pkg = s.packageName;
        m.put("name", s.name);
        m.put("groupId", s.groupId);
        m.put("package", pkg);
        m.put("packagePath", pkg.replace('.', '/'));
        m.put("appClass", s.appClass());
        m.put("description", s.description);
        m.put("javaVersion", s.javaVersion);
        m.put("bootVersion", bootVersion);
        m.put("kotlinVersion", boot3 ? KOTLIN_BOOT3 : KOTLIN_BOOT2);
        m.put("jacocoVersion", JACOCO);

        // ------------------------------------------------------------------
        // Booleans the templates branch on
        // ------------------------------------------------------------------
        m.put("boot3", boot3);
        m.put("boot2", !boot3);
        m.put("kotlin", kotlin);
        m.put("javaLang", !kotlin);
        m.put("webMvc", webMvc);
        m.put("webFlux", webFlux);
        m.put("webNone", webNone);
        m.put("webAny", !webNone);
        m.put("useRecords", boot3 && !kotlin);
        m.put("dataJpa", dataJpa);
        m.put("dataJdbc", dataJdbc);
        m.put("dataJpaOrJdbc", dataJpa || dataJdbc);
        m.put("dataR2dbc", dataR2dbc);
        m.put("dataMongo", dataMongo);
        m.put("flyway", flyway);
        m.put("liquibase", liquibase);
        // Flyway and Liquibase both migrate over JDBC, even in an R2DBC app
        boolean migrationJdbcBridge = (flyway || liquibase) && dataR2dbc;
        m.put("flywayJdbcBridge", flyway && dataR2dbc);
        m.put("liquibaseJdbcBridge", liquibase && dataR2dbc);
        m.put("cacheAny", !s.cache.equals("none"));
        m.put("cacheRedis", cacheRedis);
        m.put("cacheCaffeine", cacheCaffeine);
        m.put("cacheType", s.cache);
        m.put("redisBoot3", cacheRedis && boot3);
        m.put("redisBoot2", cacheRedis && !boot3);
        m.put("springDataBlock", dataMongo || (cacheRedis && boot3));
        m.put("mqRabbit", s.mq.equals("rabbitmq"));
        m.put("mqKafka", s.mq.equals("kafka"));
        m.put("mqArtemis", s.mq.equals("artemis"));
        m.put("gson", gson);
        m.put("gsonMvc", gson && webMvc);
        m.put("log4j2", log4j2);
        m.put("testng", testng);
        m.put("junit", !testng);
        m.put("tc", s.tc);
        m.put("tcImport", tcTestWired);
        m.put("contextTest", contextTest);
        m.put("h2Override", h2Override);
        m.put("jacoco", s.jacoco);
        m.put("lombok", s.lombok);
        m.put("openapi", s.openapi);
        m.put("actuator", s.actuator);
        m.put("actuatorWeb", s.actuator && !webNone);
        m.put("docker", s.docker);
        m.put("ci", s.ci);
        m.put("httpRestClient", s.http.equals("restclient"));
        m.put("httpWebClient", s.http.equals("webclient"));
        m.put("httpRestTemplate", s.http.equals("resttemplate"));
        m.put("httpAny", !s.http.equals("none"));
        m.put("h2db", s.db.equals("h2"));
        m.put("h2console", s.db.equals("h2") && webMvc);
        m.put("dbPostgres", s.db.equals("postgres"));
        m.put("dbMysql", s.db.equals("mysql"));
        m.put("dbMariadb", s.db.equals("mariadb"));
        m.put("dbMongo", s.db.equals("mongo"));
        m.put("serverPort", "8080");
        m.put("testMethod", contextTest ? "contextLoads" : "sanityCheck");
        m.put("testComment", contextTest
                ? "Boots the full Spring context; fails fast on any wiring problem."
                : "Context test skipped: " + s.db + " needs a running instance. "
                  + "Enable Testcontainers (--tc) for full-context integration tests.");

        // ------------------------------------------------------------------
        // Database connectivity values
        // ------------------------------------------------------------------
        String dbName = s.name.replace('-', '_');
        m.put("dbName", dbName);
        switch (s.db) {
            case "postgres" -> {
                m.put("jdbcUrl", "jdbc:postgresql://localhost:5432/" + dbName);
                m.put("r2dbcUrl", "r2dbc:postgresql://localhost:5432/" + dbName);
                m.put("dbUser", "app");
                m.put("dbPass", "app");
                m.put("h2Mode", "PostgreSQL");
            }
            case "mysql" -> {
                m.put("jdbcUrl", "jdbc:mysql://localhost:3306/" + dbName);
                m.put("r2dbcUrl", "r2dbc:mysql://localhost:3306/" + dbName);
                m.put("dbUser", "app");
                m.put("dbPass", "app");
                m.put("h2Mode", "MySQL");
            }
            case "mariadb" -> {
                m.put("jdbcUrl", "jdbc:mariadb://localhost:3306/" + dbName);
                m.put("r2dbcUrl", "r2dbc:mariadb://localhost:3306/" + dbName);
                m.put("dbUser", "app");
                m.put("dbPass", "app");
                m.put("h2Mode", "MariaDB");
            }
            case "h2" -> {
                m.put("jdbcUrl", "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
                m.put("r2dbcUrl", "r2dbc:h2:mem:///" + dbName + "?options=DB_CLOSE_DELAY=-1");
                m.put("dbUser", "sa");
                m.put("dbPass", "");
                m.put("h2Mode", "REGULAR");
            }
            default -> { /* mongo handled via uri; none needs nothing */ }
        }
        m.put("ddlAuto", s.migrations.equals("none") ? "update" : "validate");

        // ------------------------------------------------------------------
        // Dependencies
        // ------------------------------------------------------------------
        List<Map<String, Object>> deps = new ArrayList<>();

        // Web layer
        if (webMvc) {
            Map<String, Object> web = dep("org.springframework.boot", "spring-boot-starter-web");
            if (!s.server.equals("tomcat")) {
                excl(web, "org.springframework.boot", "spring-boot-starter-tomcat");
                deps.add(web);
                deps.add(dep("org.springframework.boot", "spring-boot-starter-" + s.server));
            } else {
                deps.add(web);
            }
        } else if (webFlux) {
            deps.add(dep("org.springframework.boot", "spring-boot-starter-webflux"));
        } else {
            deps.add(dep("org.springframework.boot", "spring-boot-starter"));
        }

        // WebClient in a servlet app still needs the reactive stack on the classpath
        if (s.http.equals("webclient") && !webFlux) {
            deps.add(dep("org.springframework.boot", "spring-boot-starter-webflux"));
            r.warnings.add("WebClient pulls in spring-boot-starter-webflux; Boot keeps the app "
                    + "running as a servlet application because Spring MVC is present.");
        }

        // Data access
        if (dataJpa) deps.add(dep("org.springframework.boot", "spring-boot-starter-data-jpa"));
        if (dataJdbc) deps.add(dep("org.springframework.boot", "spring-boot-starter-jdbc"));
        if (dataR2dbc) deps.add(dep("org.springframework.boot", "spring-boot-starter-data-r2dbc"));
        if (dataMongo) {
            deps.add(dep("org.springframework.boot",
                    webFlux ? "spring-boot-starter-data-mongodb-reactive" : "spring-boot-starter-data-mongodb"));
        }

        // Drivers (runtime scope)
        switch (s.db) {
            case "postgres" -> {
                if (dataR2dbc) {
                    deps.add(dep("org.postgresql", "r2dbc-postgresql", null, "runtime"));
                    if (migrationJdbcBridge) deps.add(dep("org.postgresql", "postgresql", null, "runtime"));
                } else {
                    deps.add(dep("org.postgresql", "postgresql", null, "runtime"));
                }
            }
            case "mysql" -> {
                // Boot manages com.mysql:mysql-connector-j since 2.7.8; only older 2.x BOMs know the old coordinates
                boolean legacyMysql = !boot3 && isBefore(bootVersion, 2, 7, 8);
                String g = legacyMysql ? "mysql" : "com.mysql";
                String a = legacyMysql ? "mysql-connector-java" : "mysql-connector-j";
                if (dataR2dbc) {
                    deps.add(dep("io.asyncer", "r2dbc-mysql", null, "runtime"));
                    if (migrationJdbcBridge) deps.add(dep(g, a, null, "runtime"));
                } else {
                    deps.add(dep(g, a, null, "runtime"));
                }
            }
            case "mariadb" -> {
                if (dataR2dbc) {
                    deps.add(dep("org.mariadb", "r2dbc-mariadb", null, "runtime"));
                    if (migrationJdbcBridge) deps.add(dep("org.mariadb.jdbc", "mariadb-java-client", null, "runtime"));
                } else {
                    deps.add(dep("org.mariadb.jdbc", "mariadb-java-client", null, "runtime"));
                }
            }
            case "h2" -> {
                deps.add(dep("com.h2database", "h2", null, "runtime"));
                if (dataR2dbc) deps.add(dep("io.r2dbc", "r2dbc-h2", null, "runtime"));
            }
            default -> { }
        }

        // Migrations
        if (flyway && sqlDb) {
            deps.add(dep("org.flywaydb", "flyway-core"));
            if (boot3) {
                switch (s.db) {
                    case "postgres" -> deps.add(dep("org.flywaydb", "flyway-database-postgresql"));
                    case "mysql", "mariadb" -> deps.add(dep("org.flywaydb", "flyway-mysql"));
                    default -> { }
                }
            }
        }
        if (liquibase && sqlDb) {
            deps.add(dep("org.liquibase", "liquibase-core"));
        }

        // Caching
        if (cacheCaffeine) {
            deps.add(dep("org.springframework.boot", "spring-boot-starter-cache"));
            deps.add(dep("com.github.ben-manes.caffeine", "caffeine"));
        }
        if (cacheRedis) {
            deps.add(dep("org.springframework.boot", "spring-boot-starter-cache"));
            deps.add(dep("org.springframework.boot", "spring-boot-starter-data-redis"));
        }

        // Messaging
        if (s.mq.equals("rabbitmq")) deps.add(dep("org.springframework.boot", "spring-boot-starter-amqp"));
        if (s.mq.equals("kafka")) deps.add(dep("org.springframework.kafka", "spring-kafka"));
        if (s.mq.equals("artemis")) deps.add(dep("org.springframework.boot", "spring-boot-starter-artemis"));

        // JSON
        if (gson) deps.add(dep("com.google.code.gson", "gson"));

        // Kotlin runtime
        if (kotlin) {
            deps.add(dep("org.jetbrains.kotlin", "kotlin-reflect"));
            deps.add(dep("org.jetbrains.kotlin", "kotlin-stdlib"));
            if (!gson) deps.add(dep("com.fasterxml.jackson.module", "jackson-module-kotlin"));
        }

        // Lombok
        if (s.lombok) {
            Map<String, Object> lombok = dep("org.projectlombok", "lombok");
            lombok.put("optional", true);
            deps.add(lombok);
        }

        // OpenAPI
        if (s.openapi) {
            if (boot3) {
                deps.add(dep("org.springdoc",
                        webFlux ? "springdoc-openapi-starter-webflux-ui" : "springdoc-openapi-starter-webmvc-ui",
                        SPRINGDOC_BOOT3, null));
            } else {
                deps.add(dep("org.springdoc",
                        webFlux ? "springdoc-openapi-webflux-ui" : "springdoc-openapi-ui",
                        SPRINGDOC_BOOT2, null));
            }
        }

        // Actuator
        if (s.actuator) deps.add(dep("org.springframework.boot", "spring-boot-starter-actuator"));

        // ------------------------------------------------------------------
        // Test dependencies
        // ------------------------------------------------------------------
        Map<String, Object> starterTest = dep("org.springframework.boot", "spring-boot-starter-test", null, "test");
        if (testng) {
            excl(starterTest, "org.junit.jupiter", "junit-jupiter");
            excl(starterTest, "org.mockito", "mockito-junit-jupiter");
        }
        if (easymock) {
            excl(starterTest, "org.mockito", "mockito-core");
            if (!testng) excl(starterTest, "org.mockito", "mockito-junit-jupiter");
        }
        deps.add(starterTest);

        if (testng) deps.add(dep("org.testng", "testng", java8 ? TESTNG_JAVA8 : TESTNG, "test"));
        if (easymock) deps.add(dep("org.easymock", "easymock", java8 ? EASYMOCK_JAVA8 : EASYMOCK, "test"));
        if (webFlux) deps.add(dep("io.projectreactor", "reactor-test", null, "test"));

        boolean tcBomImport = s.tc && !boot3;
        m.put("tcBomImport", tcBomImport);
        m.put("tcBomVersion", TC_BOM_BOOT2);

        if (s.tc) {
            if (boot3) deps.add(dep("org.springframework.boot", "spring-boot-testcontainers", null, "test"));
            deps.add(dep("org.testcontainers", "junit-jupiter", null, "test"));
            switch (s.db) {
                case "postgres" -> deps.add(dep("org.testcontainers", "postgresql", null, "test"));
                case "mysql" -> deps.add(dep("org.testcontainers", "mysql", null, "test"));
                case "mariadb" -> deps.add(dep("org.testcontainers", "mariadb", null, "test"));
                case "mongo" -> deps.add(dep("org.testcontainers", "mongodb", null, "test"));
                default -> { }
            }
            // @ServiceConnection for R2DBC needs the Testcontainers r2dbc module on the test classpath
            if (dataR2dbc && tcTestWired) deps.add(dep("org.testcontainers", "r2dbc", null, "test"));
            if (s.mq.equals("kafka")) deps.add(dep("org.testcontainers", "kafka", null, "test"));
            if (s.mq.equals("rabbitmq")) deps.add(dep("org.testcontainers", "rabbitmq", null, "test"));
        }
        if (h2Override && !s.db.equals("h2")) {
            deps.add(dep("com.h2database", "h2", null, "test"));
        }

        // Log4j2: swap the default Logback starter out of every Boot starter
        if (log4j2) {
            for (Map<String, Object> d : deps) {
                String a = (String) d.get("a");
                String g = (String) d.get("g");
                if (g.equals("org.springframework.boot") && a.startsWith("spring-boot-starter")
                        && !a.equals("spring-boot-starter-log4j2")) {
                    excl(d, "org.springframework.boot", "spring-boot-starter-logging");
                }
            }
            deps.add(dep("org.springframework.boot", "spring-boot-starter-log4j2"));
        }

        m.put("deps", deps);

        // Testcontainers config flags (which @ServiceConnection beans to emit)
        m.put("tcPostgres", tcTestWired && s.db.equals("postgres"));
        m.put("tcMysql", tcTestWired && s.db.equals("mysql"));
        m.put("tcMariadb", tcTestWired && s.db.equals("mariadb"));
        m.put("tcMongo", tcTestWired && s.db.equals("mongo"));
        m.put("tcKafka", tcTestWired && s.mq.equals("kafka"));
        m.put("tcRabbit", tcTestWired && s.mq.equals("rabbitmq"));
        m.put("tcRedis", tcTestWired && cacheRedis);

        // ------------------------------------------------------------------
        // Human-readable stack summary (generated README + console)
        // ------------------------------------------------------------------
        List<String> stack = new ArrayList<>();
        stack.add((kotlin ? "Kotlin " + m.get("kotlinVersion") + " / " : "") + "Java " + s.javaVersion);
        stack.add("Spring Boot " + bootVersion + (boot3 ? " (Jakarta EE)" : " (javax, legacy line)"));
        if (webMvc) stack.add("Spring MVC on " + cap(s.server));
        if (webFlux) stack.add("Spring WebFlux on Reactor Netty");
        if (webNone) stack.add("No web layer (library / worker)");
        if (!s.http.equals("none")) stack.add("HTTP client: " + httpLabel(s.http));
        if (!s.db.equals("none")) {
            String access = switch (s.data) {
                case "jpa" -> "Spring Data JPA";
                case "jdbc" -> "JdbcTemplate";
                case "r2dbc" -> "R2DBC";
                case "mongo" -> webFlux ? "Reactive MongoDB" : "Spring Data MongoDB";
                default -> s.data;
            };
            String mig = s.migrations.equals("none") ? "" : " + " + cap(s.migrations);
            stack.add(dbLabel(s.db) + " via " + access + mig);
        }
        if (cacheCaffeine) stack.add("Caffeine in-memory cache");
        if (cacheRedis) stack.add("Redis cache");
        if (s.mq.equals("rabbitmq")) stack.add("RabbitMQ (AMQP)");
        if (s.mq.equals("kafka")) stack.add("Apache Kafka");
        if (s.mq.equals("artemis")) stack.add("ActiveMQ Artemis (JMS)");
        if (gson) stack.add("Gson JSON mapper");
        if (log4j2) stack.add("Log4j2 logging");
        String testing = (testng ? "TestNG" : "JUnit 5")
                + (s.mock.equals("none") ? "" : " + " + (easymock ? "EasyMock" : "Mockito"))
                + (s.tc ? " + Testcontainers" : "");
        stack.add(testing);
        if (s.jacoco) stack.add("JaCoCo coverage");
        if (s.lombok) stack.add("Lombok");
        if (s.openapi) stack.add("springdoc OpenAPI + Swagger UI");
        if (s.actuator) stack.add("Spring Boot Actuator");
        if (s.docker) stack.add("Dockerfile" + (anyInfra(s) ? " + docker-compose infrastructure" : ""));
        if (s.ci) stack.add("GitHub Actions CI");
        m.put("stack", stack);
        m.put("anyInfra", anyInfra(s));
        m.put("anyInfraDocker", s.docker && anyInfra(s));

        // ------------------------------------------------------------------
        // File plan
        // ------------------------------------------------------------------
        String srcExt = kotlin ? "kt" : "java";
        String srcMain = "src/main/" + (kotlin ? "kotlin" : "java") + "/" + m.get("packagePath");
        String srcTest = "src/test/" + (kotlin ? "kotlin" : "java") + "/" + m.get("packagePath");

        r.files.add(new String[]{"gitignore.mustache", ".gitignore"});
        r.files.add(new String[]{"pom.xml.mustache", "pom.xml"});
        r.files.add(new String[]{"README.md.mustache", "README.md"});
        r.files.add(new String[]{"application.yml.mustache", "src/main/resources/application.yml"});
        r.files.add(new String[]{"Application." + srcExt + ".mustache",
                srcMain + "/" + m.get("appClass") + "." + srcExt});
        if (!webNone) {
            r.files.add(new String[]{"HelloController." + srcExt + ".mustache",
                    srcMain + "/web/HelloController." + srcExt});
        }
        if (!s.http.equals("none")) {
            r.files.add(new String[]{"HttpClientConfig." + srcExt + ".mustache",
                    srcMain + "/config/HttpClientConfig." + srcExt});
        }
        if (log4j2) {
            r.files.add(new String[]{"log4j2-spring.xml.mustache", "src/main/resources/log4j2-spring.xml"});
        }
        if (flyway && sqlDb) {
            r.files.add(new String[]{"V1__init.sql.mustache", "src/main/resources/db/migration/V1__init.sql"});
        }
        if (liquibase && sqlDb) {
            r.files.add(new String[]{"db.changelog-master.yaml.mustache",
                    "src/main/resources/db/changelog/db.changelog-master.yaml"});
        }
        r.files.add(new String[]{"ApplicationTests." + srcExt + ".mustache",
                srcTest + "/" + m.get("appClass") + "Tests." + srcExt});
        if (tcTestWired) {
            r.files.add(new String[]{"TestcontainersConfiguration." + srcExt + ".mustache",
                    srcTest + "/TestcontainersConfiguration." + srcExt});
        }
        if (h2Override) {
            r.files.add(new String[]{"application-test.yml.mustache", "src/test/resources/application.yml"});
        }
        if (s.docker) {
            r.files.add(new String[]{"Dockerfile.mustache", "Dockerfile"});
            r.files.add(new String[]{"dockerignore.mustache", ".dockerignore"});
            if (anyInfra(s)) {
                r.files.add(new String[]{"docker-compose.yml.mustache", "docker-compose.yml"});
            }
        }
        if (s.ci) {
            r.files.add(new String[]{"ci.yml.mustache", ".github/workflows/ci.yml"});
        }
        return r;
    }

    private static boolean anyInfra(Spec s) {
        boolean externalDb = !s.db.equals("none") && !s.db.equals("h2");
        return externalDb || s.cache.equals("redis") || !s.mq.equals("none");
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String dbLabel(String db) {
        return switch (db) {
            case "postgres" -> "PostgreSQL";
            case "mysql" -> "MySQL";
            case "mariadb" -> "MariaDB";
            case "mongo" -> "MongoDB";
            case "h2" -> "H2 (in-memory)";
            default -> db;
        };
    }

    private static String httpLabel(String http) {
        return switch (http) {
            case "restclient" -> "RestClient";
            case "webclient" -> "WebClient";
            case "resttemplate" -> "RestTemplate";
            default -> http;
        };
    }

    private static Map<String, Object> dep(String g, String a) {
        return dep(g, a, null, null);
    }

    private static Map<String, Object> dep(String g, String a, String v, String scope) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("g", g);
        d.put("a", a);
        if (v != null) d.put("v", v);
        if (scope != null) d.put("scope", scope);
        d.put("exclusions", new ArrayList<Map<String, String>>());
        d.put("hasExclusions", false);
        return d;
    }

    @SuppressWarnings("unchecked")
    private static void excl(Map<String, Object> dep, String g, String a) {
        List<Map<String, String>> ex = (List<Map<String, String>>) dep.get("exclusions");
        ex.add(Map.of("g", g, "a", a));
        dep.put("hasExclusions", true);
    }

    /** True when a dotted version like "2.7.3" sorts before major.minor.patch. */
    static boolean isBefore(String version, int major, int minor, int patch) {
        String[] p = version.split("[.-]");
        int[] want = {major, minor, patch};
        for (int i = 0; i < 3; i++) {
            int v = i < p.length && p[i].matches("\\d+") ? Integer.parseInt(p[i]) : 0;
            if (v != want[i]) return v < want[i];
        }
        return false;
    }
}
