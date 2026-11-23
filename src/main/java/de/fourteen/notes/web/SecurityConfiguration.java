package de.fourteen.notes.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

/**
 * One user with HTTP Basic, set through SPRING_SECURITY_USER_NAME and
 * SPRING_SECURITY_USER_PASSWORD. Only the health check is public, for Fly.
 *
 * <p>CSRF tokens are off. Writes accept JSON only, which a cross-site form cannot send, and a
 * cross-site fetch with JSON needs a CORS preflight that this app never allows.
 *
 * <p>Content-Security-Policy: only the own origin, no inline script or style element, no framing.
 * Style attributes are allowed (the tour places its highlight with one). The JGiven report under
 * {@code /jgiven/} is AngularJS, which needs more, so it gets no policy; it is a test report.
 */
@Configuration
class SecurityConfiguration {

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; style-src-attr 'unsafe-inline'; img-src 'self' data:;"
                    + " frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers("/actuator/health")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .headers(
                        headers ->
                                headers.addHeaderWriter(
                                        new DelegatingRequestMatcherHeaderWriter(
                                                new NegatedRequestMatcher(
                                                        PathPatternRequestMatcher.withDefaults()
                                                                .matcher("/jgiven/**")),
                                                new StaticHeadersWriter(
                                                        "Content-Security-Policy",
                                                        CONTENT_SECURITY_POLICY))))
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
