package de.fourteen.notes.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DatabaseUrlPropertiesTest {

    @Provide
    Arbitrary<String> secret() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(
                        s ->
                                s.equals(
                                        new String(
                                                s.getBytes(StandardCharsets.UTF_8),
                                                StandardCharsets.UTF_8)));
    }

    @Provide
    Arbitrary<String> host() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .numeric()
                .withChars('-')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(host -> Character.isLetter(host.charAt(0)) && !host.endsWith("-"));
    }

    @Provide
    Arbitrary<String> database() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('_')
                .ofMinLength(1)
                .ofMaxLength(15);
    }

    @Property
    void user_and_password_survive_percent_encoding(
            @ForAll("secret") String user,
            @ForAll("secret") String password,
            @ForAll("host") String host,
            @ForAll("database") String database,
            @ForAll("port") int port) {
        var properties =
                DatabaseUrlEnvironmentPostProcessor.toDatasourceProperties(
                        "postgres://%s:%s@%s:%d/%s?sslmode=disable"
                                .formatted(encode(user), encode(password), host, port, database));

        assertThat(properties)
                .containsEntry("spring.datasource.username", user)
                .containsEntry("spring.datasource.password", password)
                .containsEntry(
                        "spring.datasource.url",
                        "jdbc:postgresql://%s:%d/%s?sslmode=disable"
                                .formatted(host, port, database));
    }

    @Property
    void without_port_query_and_password_nothing_is_invented(
            @ForAll("secret") String user,
            @ForAll("host") String host,
            @ForAll("database") String database) {
        var properties =
                DatabaseUrlEnvironmentPostProcessor.toDatasourceProperties(
                        "postgresql://%s@%s/%s".formatted(encode(user), host, database));

        assertThat(properties)
                .containsEntry("spring.datasource.username", user)
                .containsEntry(
                        "spring.datasource.url",
                        "jdbc:postgresql://%s/%s".formatted(host, database))
                .doesNotContainKey("spring.datasource.password");
    }

    @Property
    void a_url_without_credentials_has_none(
            @ForAll("host") String host, @ForAll("database") String database) {
        var properties =
                DatabaseUrlEnvironmentPostProcessor.toDatasourceProperties(
                        "postgres://%s/%s".formatted(host, database));

        assertThat(properties).containsOnlyKeys("spring.datasource.url");
    }

    @Property
    void a_host_that_is_not_valid_is_rejected_instead_of_becoming_null(
            @ForAll("database") String name) {
        assertThatThrownBy(
                        () ->
                                DatabaseUrlEnvironmentPostProcessor.toDatasourceProperties(
                                        "postgres://u:p@my_" + name + ":5432/db"))
                .isInstanceOf(IllegalArgumentException.class)
                .message()
                .doesNotContain("p@");
    }

    @Provide
    Arbitrary<Integer> port() {
        return Arbitraries.integers().between(1, 65535);
    }

    /** Everything but the unreserved characters of RFC 3986, as %XX of the UTF-8 bytes. */
    private static String encode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if (c < 128 && (Character.isLetterOrDigit(c) || "-._~".indexOf(c) >= 0)) {
                encoded.append(c);
            } else {
                encoded.append('%').append("%02X".formatted(b & 0xff));
            }
        }
        return encoded.toString();
    }
}
