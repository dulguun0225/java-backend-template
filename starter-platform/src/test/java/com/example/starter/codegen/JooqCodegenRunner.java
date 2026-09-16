package com.example.starter.codegen;

import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * jOOQ code generation from the real schema: stand up a PostgreSQL, run the committed Flyway migrations,
 * generate jOOQ classes from the live schema into {@code src/main/java} under {@code com.example.starter.db},
 * committed. Run only via the {@code codegen} Maven profile ({@code mvn -Pcodegen -pl starter-platform
 * process-test-classes}); CI regenerates and diffs against the committed tree, and drift fails the build.
 * Build tooling: excluded from the compile wall and the formatter.
 */
public final class JooqCodegenRunner {

    private JooqCodegenRunner() {}

    public static void main(String[] args) throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            Configuration configuration = new Configuration()
                    .withJdbc(new Jdbc()
                            .withDriver("org.postgresql.Driver")
                            .withUrl(postgres.getJdbcUrl())
                            .withUser(postgres.getUsername())
                            .withPassword(postgres.getPassword()))
                    .withGenerator(new Generator()
                            .withDatabase(new Database()
                                    .withName("org.jooq.meta.postgres.PostgresDatabase")
                                    .withInputSchema("public")
                                    .withOutputSchemaToDefault(true)
                                    .withExcludes("flyway_schema_history"))
                            .withTarget(new Target()
                                    .withPackageName("com.example.starter.db")
                                    .withDirectory("src/main/java")));

            GenerationTool.generate(configuration);
        }
    }
}
