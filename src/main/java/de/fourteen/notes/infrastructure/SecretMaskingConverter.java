package de.fourteen.notes.infrastructure;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback's %masked(...): replaces the values of the secrets in the environment (name contains
 * PASSWORD, TOKEN, SECRET or KEY, and DATABASE_URL with its password) in everything it wraps,
 * exception stack traces included. A net below the rule that a secret is never logged on purpose.
 */
public class SecretMaskingConverter extends CompositeConverter<ILoggingEvent> {

    static final String MASK = "***";
    private static final int MIN_LENGTH = 6;
    private static final Pattern URL_PASSWORD = Pattern.compile("://[^:/@]+:([^@]+)@");
    private static final List<String> NAME_PARTS = List.of("PASSWORD", "TOKEN", "SECRET", "KEY");

    private List<String> secrets = List.of();

    @Override
    public void start() {
        secrets = secretsIn(System.getenv());
        super.start();
    }

    @Override
    protected String transform(ILoggingEvent event, String in) {
        return mask(in, secrets);
    }

    static List<String> secretsIn(Map<String, String> environment) {
        List<String> secrets = new ArrayList<>();
        environment.forEach((name, value) -> {
            String upper = name.toUpperCase(Locale.ROOT);
            if (NAME_PARTS.stream().anyMatch(upper::contains)) {
                secrets.add(value);
            }
            if (upper.equals("DATABASE_URL")) {
                secrets.add(value);
                Matcher password = URL_PASSWORD.matcher(value);
                if (password.find()) {
                    secrets.add(password.group(1));
                }
            }
        });
        // Longest first, so that a URL goes before the password it contains.
        return secrets.stream()
                .filter(secret -> secret != null && secret.strip().length() >= MIN_LENGTH)
                .distinct()
                .sorted((a, b) -> b.length() - a.length())
                .toList();
    }

    static String mask(String text, List<String> secrets) {
        String masked = text;
        for (String secret : secrets) {
            masked = masked.replace(secret, MASK);
        }
        return masked;
    }
}
