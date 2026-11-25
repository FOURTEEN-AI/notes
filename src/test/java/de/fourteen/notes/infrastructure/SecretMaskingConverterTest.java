package de.fourteen.notes.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SecretMaskingConverterTest {

    private static final Map<String, String> ENVIRONMENT =
            Map.of(
                    "SPRING_SECURITY_USER_PASSWORD", "hunter2hunter2",
                    "GITHUB_TOKEN", "github_pat_abcdef123456",
                    "DATABASE_URL", "postgres://notes:s3cr3tPass@db.internal:5432/notes",
                    "JAVA_HOME", "/usr/lib/jvm",
                    "SHORT_TOKEN", "abc");

    @Test
    void masks_the_secrets_of_the_environment_anywhere_in_the_text() {
        var secrets = SecretMaskingConverter.secretsIn(ENVIRONMENT);

        String masked =
                SecretMaskingConverter.mask(
                        "login hunter2hunter2 failed with github_pat_abcdef123456 at"
                                + " jdbc:postgresql://notes:s3cr3tPass@db.internal\n"
                                + "\tat Foo.bar(postgres://notes:s3cr3tPass@db.internal:5432/notes)",
                        secrets);

        assertThat(masked)
                .doesNotContain("hunter2hunter2", "github_pat_abcdef123456", "s3cr3tPass")
                .contains("login *** failed with *** at");
    }

    @Test
    void leaves_other_variables_and_short_values_alone() {
        var secrets = SecretMaskingConverter.secretsIn(ENVIRONMENT);

        assertThat(SecretMaskingConverter.mask("/usr/lib/jvm and abc", secrets))
                .isEqualTo("/usr/lib/jvm and abc");
    }
}
