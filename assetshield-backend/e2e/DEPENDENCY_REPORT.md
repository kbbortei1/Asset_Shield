# Dependency report

Direct-dependency updates from `mvn versions:display-dependency-updates` per
service, captured 2026-06-14. Only the project's **direct** dependencies
are shown (the managed Spring BOM is intentionally omitted). **Nothing is upgraded
for the graded submission** — this is a roadmap input for the post-submission
startup phase: review, test, then bump deliberately. Java 25 · Spring Boot 4.0.x
· Spring Cloud 2025.1.1 are pinned and stay pinned for the submission.

## gateway
```
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-test ..... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-webtestclient .... 4.0.7 -> 4.1.0
                                                        5.0.1 -> 5.0.2
```

## auth-service
```
org.flywaydb:flyway-database-postgresql ............ 11.14.1 -> 12.8.1
org.springframework.boot:spring-boot-data-jpa-test .... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-jdbc-test ........ 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-flyway ... 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-test ..... 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-webmvc ... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-testcontainers ... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-webmvc-test ...... 4.0.7 -> 4.1.0
org.springframework.security:spring-security-test ..... 7.0.6 -> 7.1.0
```

## property-service
```
org.flywaydb:flyway-database-postgresql ............ 11.14.1 -> 12.8.1
org.springframework.boot:spring-boot-data-jpa-test .... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-jdbc-test ........ 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-flyway ... 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-test ..... 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-webmvc ... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-testcontainers ... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-webmvc-test ...... 4.0.7 -> 4.1.0
org.springframework.security:spring-security-test ..... 7.0.6 -> 7.1.0
```

## damage-service
```
org.apache.pdfbox:pdfbox .............................. 3.0.5 -> 3.0.7
org.flywaydb:flyway-database-postgresql ............ 11.14.1 -> 12.8.1
org.springframework.boot:spring-boot-data-jpa-test .... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-jdbc-test ........ 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-flyway ... 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-test ..... 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-webmvc ... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-testcontainers ... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-webmvc-test ...... 4.0.7 -> 4.1.0
org.springframework.security:spring-security-test ..... 7.0.6 -> 7.1.0
```

## marketplace-service
```
org.flywaydb:flyway-database-postgresql ............ 11.14.1 -> 12.8.1
org.springframework.boot:spring-boot-data-jpa-test .... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-jdbc-test ........ 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-flyway ... 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-test ..... 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-webmvc ... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-testcontainers ... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-webmvc-test ...... 4.0.7 -> 4.1.0
org.springframework.security:spring-security-test ..... 7.0.6 -> 7.1.0
```

## notification-service
```
com.google.firebase:firebase-admin .................... 9.4.3 -> 9.9.0
org.flywaydb:flyway-database-postgresql ............ 11.14.1 -> 12.8.1
org.springframework.boot:spring-boot-data-jpa-test .... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-jdbc-test ........ 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-flyway ... 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-test ..... 4.0.7 -> 4.1.0
                                                        4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-starter-webmvc ... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-testcontainers ... 4.0.7 -> 4.1.0
org.springframework.boot:spring-boot-webmvc-test ...... 4.0.7 -> 4.1.0
org.springframework.security:spring-security-test ..... 7.0.6 -> 7.1.0
```

