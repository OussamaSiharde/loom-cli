package dev.loom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Interactive questionnaire. Every question shows its options numbered with
 * the current default highlighted; Enter accepts the default, so power users
 * can hammer through in seconds while newcomers read the labels.
 */
final class Prompter {

    private final Spec s;
    private final BufferedReader in =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    Prompter(Spec s) {
        this.s = s;
    }

    void run() throws IOException {
        s.name = text("Project name (artifactId)", s.name);
        s.groupId = text("Group id", s.groupId);
        s.lang = choose("Language", List.of("java", "kotlin"), s.lang,
                Map.of("java", "Java", "kotlin", "Kotlin"));
        s.boot = choose("Spring Boot line", List.of("3", "2"), s.boot,
                Map.of("3", "Spring Boot 3.x - Jakarta EE, Java 17+",
                       "2", "Spring Boot 2.7 - legacy javax, Java 8+"));

        List<String> javas = s.boot3() ? List.of("17", "21") : List.of("8", "11", "17", "21");
        if (!javas.contains(s.javaVersion)) s.javaVersion = javas.get(javas.size() - 1);
        s.javaVersion = choose("Java version", javas, s.javaVersion, null);

        s.web = choose("Web stack", List.of("mvc", "webflux", "none"), s.web,
                Map.of("mvc", "Spring MVC - servlet, blocking",
                       "webflux", "Spring WebFlux - reactive, Netty",
                       "none", "None - library or worker"));
        if (s.web.equals("mvc")) {
            String def = s.server.equals("netty") ? "tomcat" : s.server;
            s.server = choose("Servlet container", List.of("tomcat", "undertow", "jetty"), def, null);
        }

        List<String> https = s.boot3()
                ? List.of("none", "restclient", "webclient", "resttemplate")
                : List.of("none", "webclient", "resttemplate");
        if (!https.contains(s.http)) s.http = "none";
        s.http = choose("HTTP client", https, s.http,
                Map.of("none", "None",
                       "restclient", "RestClient - Spring 6.1+, fluent and synchronous",
                       "webclient", "WebClient - reactive",
                       "resttemplate", "RestTemplate - classic"));

        s.db = choose("Database", List.of("postgres", "mysql", "mariadb", "mongo", "h2", "none"), s.db,
                Map.of("postgres", "PostgreSQL", "mysql", "MySQL", "mariadb", "MariaDB",
                       "mongo", "MongoDB", "h2", "H2 in-memory", "none", "None"));
        if (s.sqlDb()) {
            List<String> datas = s.boot3()
                    ? (s.web.equals("webflux") ? List.of("r2dbc", "jpa", "jdbc") : List.of("jpa", "jdbc", "r2dbc"))
                    : List.of("jpa", "jdbc");
            String def = datas.contains(s.data) ? s.data : datas.get(0);
            s.data = choose("Data access", datas, def,
                    Map.of("jpa", "Spring Data JPA - Hibernate",
                           "jdbc", "Plain JDBC - JdbcTemplate",
                           "r2dbc", "R2DBC - reactive SQL"));
            s.migrations = choose("Schema migrations", List.of("flyway", "liquibase", "none"), s.migrations,
                    Map.of("flyway", "Flyway", "liquibase", "Liquibase", "none", "None"));
        }

        s.cache = choose("Caching", List.of("none", "caffeine", "redis"), s.cache,
                Map.of("none", "None", "caffeine", "Caffeine - in-memory", "redis", "Redis"));
        s.mq = choose("Messaging", List.of("none", "rabbitmq", "kafka", "artemis"), s.mq,
                Map.of("none", "None", "rabbitmq", "RabbitMQ - AMQP",
                       "kafka", "Apache Kafka", "artemis", "ActiveMQ Artemis - JMS"));

        if (s.web.equals("mvc")) {
            s.json = choose("JSON mapper", List.of("jackson", "gson"), s.json,
                    Map.of("jackson", "Jackson - Boot default", "gson", "Gson"));
        }
        s.logging = choose("Logging", List.of("logback", "log4j2"), s.logging,
                Map.of("logback", "Logback - Boot default", "log4j2", "Log4j2"));
        if (!s.lang.equals("kotlin")) {
            s.tests = choose("Test framework", List.of("junit", "testng"), s.tests,
                    Map.of("junit", "JUnit 5", "testng", "TestNG"));
        }
        s.mock = choose("Mocking", List.of("mockito", "easymock", "none"), s.mock,
                Map.of("mockito", "Mockito", "easymock", "EasyMock", "none", "None"));

        s.tc = yesNo("Testcontainers integration tests", s.tc);
        s.jacoco = yesNo("JaCoCo code coverage", s.jacoco);
        if (s.lang.equals("java")) s.lombok = yesNo("Lombok", s.lombok);
        if (!s.web.equals("none")) s.openapi = yesNo("OpenAPI + Swagger UI (springdoc)", s.openapi);
        s.actuator = yesNo("Spring Boot Actuator", s.actuator);
        s.docker = yesNo("Dockerfile + docker-compose for infrastructure", s.docker);
        s.ci = yesNo("GitHub Actions CI workflow", s.ci);
        System.out.println();
    }

    private String text(String label, String def) throws IOException {
        System.out.printf("%s [%s]: ", label, def);
        String line = in.readLine();
        return line == null || line.isBlank() ? def : line.trim();
    }

    private String choose(String label, List<String> options, String def,
                          Map<String, String> labels) throws IOException {
        System.out.println();
        System.out.println(label + ":");
        for (int i = 0; i < options.size(); i++) {
            String opt = options.get(i);
            String desc = labels != null && labels.containsKey(opt) ? labels.get(opt) : opt;
            String marker = opt.equals(def) ? "*" : " ";
            System.out.printf(" %s %d) %s%n", marker, i + 1, desc);
        }
        System.out.printf("Choice [%d]: ", options.indexOf(def) + 1);
        while (true) {
            String line = in.readLine();
            if (line == null || line.isBlank()) return def;
            String trimmed = line.trim();
            try {
                int n = Integer.parseInt(trimmed);
                if (n >= 1 && n <= options.size()) return options.get(n - 1);
            } catch (NumberFormatException ignored) {
                if (options.contains(trimmed.toLowerCase())) return trimmed.toLowerCase();
            }
            System.out.printf("Enter 1-%d [%d]: ", options.size(), options.indexOf(def) + 1);
        }
    }

    private boolean yesNo(String label, boolean def) throws IOException {
        System.out.printf("%s? [%s]: ", label, def ? "Y/n" : "y/N");
        String line = in.readLine();
        if (line == null || line.isBlank()) return def;
        return switch (line.trim().toLowerCase()) {
            case "y", "yes", "true", "1" -> true;
            case "n", "no", "false", "0" -> false;
            default -> def;
        };
    }
}
