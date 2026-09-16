package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The schema lint over the committed Flyway migrations: the bans no off-the-shelf migration linter carries
 * because they need to know which column holds money or which column is the key. Runs over every
 * committed migration, and over the committed negative fixtures so each rule is proven to fire.
 *
 * <ul>
 *   <li>Keys: every table's surrogate key is {@code id uuid ... default uuidv7()}; sequences in every spelling
 *       ({@code serial}, {@code bigserial}, {@code generated ... as identity}, {@code create sequence}) and the
 *       random generators ({@code gen_random_uuid}, {@code uuid_generate_v4}) are banned. A table with a
 *       composite natural key says so with a {@code -- composite-key:} comment on the statement.
 *   <li>Time: {@code timestamptz}, never bare {@code timestamp}; no clock function in column defaults, because
 *       a database default is a wall-clock read the ArchUnit clock ban cannot see.
 *   <li>Money: a column named {@code *_amount} is {@code numeric(19,4)} or {@code numeric(20,4)}, {@code not
 *       null}, carries a {@code check (... <> 'NaN')}, and has a {@code *_currency} sibling that is {@code not
 *       null}. {@code real}, {@code double precision}, {@code float} and the PostgreSQL {@code money} type are
 *       banned everywhere.
 * </ul>
 */
class MigrationConventionsTest {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");
    private static final Path FIXTURES = Path.of("src", "test", "resources", "migration-fixtures");

    record Violation(String file, String rule) {}

    @Test
    void everyCommittedMigrationFollowsTheConventions() throws IOException {
        List<Path> files = sqlFiles(MIGRATIONS);
        assertThat(files).as("no migrations found; check the path").isNotEmpty();
        List<Violation> violations = new ArrayList<>();
        for (Path file : files) {
            violations.addAll(lint(file));
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void everyRuleFiresOnItsNegativeFixture() throws IOException {
        // Each bad_<rule>.sql must trip the rule its name carries; good_*.sql must trip nothing.
        List<Path> files = sqlFiles(FIXTURES);
        assertThat(files).isNotEmpty();
        for (Path file : files) {
            String name = file.getFileName().toString();
            List<Violation> violations = lint(file);
            if (name.startsWith("good_")) {
                assertThat(violations).as(name).isEmpty();
            } else {
                String expectedRule = name.substring("bad_".length(), name.length() - ".sql".length());
                assertThat(violations).as(name).extracting(Violation::rule).contains(expectedRule);
            }
        }
    }

    static List<Violation> lint(Path file) throws IOException {
        String sql = Files.readString(file, StandardCharsets.UTF_8);
        String name = file.getFileName().toString();
        String lower = stripComments(sql).toLowerCase(Locale.ROOT);
        List<Violation> out = new ArrayList<>();

        if (find(
                lower,
                "\\b(big)?serial\\b|generated\\s+(always|by\\s+default)\\s+as\\s+identity|create\\s+sequence\\b")) {
            out.add(new Violation(name, "sequence"));
        }
        if (find(lower, "gen_random_uuid|uuid_generate_v4")) {
            out.add(new Violation(name, "random-uuid"));
        }
        if (find(lower, "\\btimestamp\\b(?!tz)")) {
            out.add(new Violation(name, "bare-timestamp"));
        }
        if (find(
                lower,
                "default\\s+(now\\(\\)|current_timestamp|clock_timestamp\\(\\)|localtimestamp|current_date|statement_timestamp\\(\\))")) {
            out.add(new Violation(name, "clock-default"));
        }
        if (find(lower, "\\b(real|double\\s+precision|float\\d*|money)\\b")) {
            out.add(new Violation(name, "float-or-money-type"));
        }

        Matcher tables = Pattern.compile(
                        "create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([\\w.\"]+)\\s*\\((.*?)\\);", Pattern.DOTALL)
                .matcher(lower);
        while (tables.find()) {
            String body = tables.group(2);
            boolean composite = sql.substring(0, tables.start() + 1)
                            .toLowerCase(Locale.ROOT)
                            .contains("-- composite-key:")
                    || Pattern.compile("--\\s*composite-key:")
                            .matcher(sql.toLowerCase(Locale.ROOT))
                            .find();
            if (!composite && !find(body, "\\bid\\s+uuid\\s+primary\\s+key\\s+default\\s+uuidv7\\(\\)")) {
                out.add(new Violation(name, "uuidv7-primary-key"));
            }
            Matcher amounts = Pattern.compile("^\\s*(\\w+)_amount\\s+(.+)$", Pattern.MULTILINE)
                    .matcher(body);
            while (amounts.find()) {
                String prefix = amounts.group(1);
                String definition = amounts.group(2);
                if (!find(definition, "^numeric\\((19|20),\\s*4\\)\\s+not\\s+null")) {
                    out.add(new Violation(name, "money-column-type"));
                }
                if (!find(body, "check\\s*\\(\\s*" + prefix + "_amount\\s*<>\\s*'nan'")) {
                    out.add(new Violation(name, "money-nan-check"));
                }
                if (!find(body, "\\b" + prefix + "_currency\\s+(char\\(3\\)|text|varchar\\(3\\))\\s+not\\s+null")) {
                    out.add(new Violation(name, "money-currency-sibling"));
                }
            }
        }
        return out;
    }

    private static boolean find(String text, String regex) {
        return Pattern.compile(regex, Pattern.MULTILINE).matcher(text).find();
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("--[^\\n]*", "");
    }

    private static List<Path> sqlFiles(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
        }
    }
}
