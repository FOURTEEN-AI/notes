package de.fourteen.notes.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Dry run of {@code schema.sql}, which the app applies on every start: it must apply twice, and on
 * top of the {@code schema.sql} of {@code origin/main} with existing rows it must give the schema
 * of a fresh install. Catches a change that {@code create table if not exists} would silently skip
 * on a database that exists already (a new column, a changed type or constraint).
 */
class SchemaMigrationTest {

    private static final String SCHEMA = "src/main/resources/schema.sql";

    private static PostgreSQLContainer postgres;

    @BeforeAll
    static void startDatabase() {
        postgres = new PostgreSQLContainer("postgres:17-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() {
        postgres.stop();
    }

    @Test
    void appliesTwice() throws Exception {
        String branch = read(Path.of(SCHEMA));
        try (Connection db = createDatabase("twice")) {
            apply(db, branch);
            apply(db, branch);
            assertThat(describe(db)).isEqualTo(describeFresh(branch));
        }
    }

    @Test
    void appliesOnTopOfMainWithExistingRows() throws Exception {
        String branch = read(Path.of(SCHEMA));
        try (Connection db = createDatabase("upgraded")) {
            apply(db, mainSchema());
            try (Statement statement = db.createStatement()) {
                statement.execute("insert into note (text) values ('first'), ('second')");
            }
            apply(db, branch);

            assertThat(describe(db)).isEqualTo(describeFresh(branch));
            assertThat(query(db, "select text from note order by id"))
                    .containsExactly("first", "second");
        }
    }

    /** The schema of main, as the CI checkout (full history) and a local clone have it. */
    private static String mainSchema() throws IOException, InterruptedException {
        Process git =
                new ProcessBuilder("git", "show", "origin/main:" + SCHEMA)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start();
        String schema = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(git.waitFor())
                .as("git show origin/main:%s (a shallow checkout lacks origin/main)", SCHEMA)
                .isZero();
        return schema;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file);
    }

    private static List<String> describeFresh(String schema) throws SQLException {
        try (Connection db = createDatabase("fresh")) {
            apply(db, schema);
            return describe(db);
        }
    }

    private static Connection createDatabase(String name) throws SQLException {
        try (Connection admin =
                        DriverManager.getConnection(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword());
                Statement statement = admin.createStatement()) {
            statement.execute("drop database if exists " + name);
            statement.execute("create database " + name);
        }
        return DriverManager.getConnection(
                postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + name),
                postgres.getUsername(),
                postgres.getPassword());
    }

    private static void apply(Connection db, String schema) {
        ScriptUtils.executeSqlScript(
                db, new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Columns, constraints and indexes, without the column position. The not-null constraints are
     * named by oid and are covered by {@code null=} of the column.
     */
    private static List<String> describe(Connection db) throws SQLException {
        List<String> description = new ArrayList<>();
        description.addAll(
                query(
                        db,
                        """
                        select 'column ' || table_name || '.' || column_name || ' ' || data_type
                            || ' null=' || is_nullable || ' default=' || coalesce(column_default, '')
                            || ' identity=' || is_identity || '/' || coalesce(identity_generation, '')
                            || ' length=' || coalesce(character_maximum_length::text, '')
                        from information_schema.columns where table_schema = 'public'
                        order by 1
                        """));
        description.addAll(
                query(
                        db,
                        """
                        select 'constraint ' || tc.table_name || ' ' || tc.constraint_name || ' '
                            || tc.constraint_type || ' ' || coalesce(
                                (select string_agg(kcu.column_name, ',' order by kcu.ordinal_position)
                                 from information_schema.key_column_usage kcu
                                 where kcu.constraint_schema = tc.constraint_schema
                                   and kcu.constraint_name = tc.constraint_name), '')
                            || ' ' || coalesce(
                                (select cc.check_clause from information_schema.check_constraints cc
                                 where cc.constraint_schema = tc.constraint_schema
                                   and cc.constraint_name = tc.constraint_name), '')
                        from information_schema.table_constraints tc where tc.table_schema = 'public'
                          and tc.constraint_name !~ '^[0-9]+_[0-9]+_[0-9]+_not_null$'
                        order by 1
                        """));
        description.addAll(
                query(
                        db,
                        "select 'index ' || indexdef from pg_indexes where schemaname = 'public' order by 1"));
        return description;
    }

    private static List<String> query(Connection db, String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Statement statement = db.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                rows.add(result.getString(1));
            }
        }
        return rows;
    }
}
