package de.fourteen.notes.infrastructure;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Maps DATABASE_URL, which `fly postgres attach` sets as
 * postgres://user:password@host:5432/db?sslmode=disable, to Spring's datasource properties. An
 * explicit spring.datasource.url wins.
 */
class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null
                || databaseUrl.isBlank()
                || environment.containsProperty("spring.datasource.url")) {
            return;
        }
        environment
                .getPropertySources()
                .addLast(new MapPropertySource("databaseUrl", toDatasourceProperties(databaseUrl)));
    }

    static Map<String, Object> toDatasourceProperties(String databaseUrl) {
        URI uri;
        try {
            uri = URI.create(databaseUrl.trim());
        } catch (IllegalArgumentException e) {
            // The original message would contain the password.
            throw new IllegalArgumentException("DATABASE_URL is not a valid URI");
        }
        if (!"postgres".equals(uri.getScheme()) && !"postgresql".equals(uri.getScheme())) {
            throw new IllegalArgumentException("DATABASE_URL must start with postgres://");
        }
        String jdbcUrl = jdbcUrl(uri);
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", jdbcUrl);
        addCredentials(properties, uri.getRawUserInfo());
        return properties;
    }

    private static String jdbcUrl(URI uri) {
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("DATABASE_URL has no valid host");
        }
        return "jdbc:postgresql://"
                + uri.getHost()
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                + uri.getRawPath()
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    private static void addCredentials(Map<String, Object> properties, @Nullable String userInfo) {
        if (userInfo == null) {
            return;
        }
        String[] parts = userInfo.split(":", 2);
        properties.put("spring.datasource.username", decode(parts[0]));
        if (parts.length == 2) {
            properties.put("spring.datasource.password", decode(parts[1]));
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
