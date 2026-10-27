# Loom decision tree

Every question, every option, and every rule the resolver applies. Rules are
also printed at generation time under "Resolution notes".

## Questions

| # | Question | Options (default first) | Asked when |
|---|----------|-------------------------|------------|
| 1 | Project name | free text (`demo`) | always |
| 2 | Group id | free text (`com.example`) | always |
| 3 | Language | **java**, kotlin | always |
| 4 | Spring Boot line | **3** (Jakarta), 2 (javax legacy) | always |
| 5 | Java version | **21**, 17 (+ 8, 11 on Boot 2) | always |
| 6 | Web stack | **mvc**, webflux, none | always |
| 7 | Servlet container | **tomcat**, undertow, jetty | web = mvc |
| 8 | HTTP client | **none**, restclient*, webclient, resttemplate | always (*Boot 3 only) |
| 9 | Database | **none**, postgres, mysql, mariadb, mongo, h2 | always |
| 10 | Data access | **jpa**, jdbc, r2dbc* | SQL db (*Boot 3; default on WebFlux) |
| 11 | Migrations | **none**, flyway, liquibase | SQL db |
| 12 | Caching | **none**, caffeine, redis | always |
| 13 | Messaging | **none**, rabbitmq, kafka, artemis | always |
| 14 | JSON mapper | **jackson**, gson | web = mvc |
| 15 | Logging | **logback**, log4j2 | always |
| 16 | Test framework | **junit**, testng | lang = java |
| 17 | Mocking | **mockito**, easymock, none | always |
| 18–24 | Testcontainers, JaCoCo, Lombok, OpenAPI, Actuator, Docker, CI | yes/no toggles | contextual |

## Resolution rules

**Platform**
- Boot 3 + Java 8/11 → Java bumped to 17 (Boot 3 baseline).
- Boot 3 → Jakarta namespace, Spring Boot `3.5.6`; Boot 2 → javax, `2.7.18`. Override with `--boot-version`.
- Kotlin → `kotlin-maven-plugin` with the `spring` compiler plugin (all-open), `jpa` (no-arg) added when JPA is selected; `kotlin-reflect`, `kotlin-stdlib`, `jackson-module-kotlin`; Lombok and TestNG replaced (with a note).

**Web & HTTP**
- MVC + Undertow/Jetty → `spring-boot-starter-tomcat` excluded, replacement starter added.
- WebFlux → Reactor Netty, Jackson enforced (Gson is MVC-only).
- RestClient on Boot 2 → downgraded to RestTemplate (needs Spring 6.1).
- WebClient in an MVC app → `spring-boot-starter-webflux` added; app stays a servlet app.

**Data**
- WebFlux + SQL → R2DBC by default; JPA/JDBC allowed with a blocking-event-loop warning.
- R2DBC drivers per engine (`r2dbc-postgresql`, `io.asyncer:r2dbc-mysql`, `r2dbc-mariadb`, `r2dbc-h2`).
- R2DBC + Flyway or Liquibase → JDBC driver added and `spring.flyway.url` / `spring.liquibase.url` bridged (migrations run over JDBC).
- Boot 3 + Flyway → engine module added (`flyway-database-postgresql` / `flyway-mysql`).
- MySQL coordinates: `com.mysql:mysql-connector-j` (Boot 3 and Boot 2.7.8+, including the 2.7.18 default); `mysql:mysql-connector-java` only when an older 2.x Boot version is pinned.
- MongoDB → blocking or reactive starter depending on the web stack; Flyway/Liquibase skipped.

**Logging & JSON**
- Log4j2 → `spring-boot-starter-logging` excluded from **every** Boot starter, `spring-boot-starter-log4j2` added, `log4j2-spring.xml` generated.
- Gson (MVC) → `spring.mvc.converters.preferred-json-mapper: gson`; sample DTO uses a String timestamp so it serialises cleanly under both mappers.

**Testing**
- TestNG → JUnit Jupiter + `mockito-junit-jupiter` excluded from `starter-test`; context test extends `AbstractTestNGSpringContextTests`; TestNG `7.10.2` (`7.5.1` on Java 8).
- EasyMock → Mockito excluded; EasyMock `5.4.0` (`4.3` on Java 8).
- Testcontainers + Boot 3 + JUnit → `@ServiceConnection` configuration class (DB, Kafka, RabbitMQ, Redis) imported by the context test.
- Testcontainers + Boot 2 → dependencies + explicit `testcontainers-bom` (Boot 2.7 doesn't manage it); tests fall back to H2.
- SQL database without Testcontainers → `src/test/resources/application.yml` pointing at H2 in the engine's compatibility MODE, migrations disabled in tests → `mvn verify` passes with no server running.
- MongoDB/R2DBC without Testcontainers → plain sanity test with an explanatory comment (a context test would need a live instance).

**Infrastructure**
- docker-compose services generated to match `application.yml` exactly (same DB name, `app`/`app` credentials, same ports) with healthchecks; single-node KRaft Kafka.
- Dockerfile is a two-stage build on `maven:3.9-eclipse-temurin-<java>` → `eclipse-temurin:<java>-jre`.
- GitHub Actions workflow: Temurin toolchain matching the project's Java version, Maven cache, `mvn -B verify`, JaCoCo report uploaded as an artifact when enabled.
