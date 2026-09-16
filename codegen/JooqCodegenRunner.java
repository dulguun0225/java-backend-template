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
 * committed. Run only via the {@code codegen} Maven profile ({@code mvn -Pcodegen generate-sources}), which
 * launches this file in the java launcher's source-file mode on the test classpath before anything compiles, so
 * a fresh schema regenerates even while main code references tables that do not exist yet. CI regenerates
 * twice and diffs against the committed tree; drift fails the build. Build tooling, not part of the compiled
 * project: outside src/, so neither the compile wall nor the formatter reads it.
 */
public final class JooqCodegenRunner {

    private JooqCodegenRunner() {}

    public static void main(String[] args) throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("filesystem:src/main/resources/db/migration")
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
