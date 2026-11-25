package de.fourteen.notes.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * One user with HTTP Basic, set through SPRING_SECURITY_USER_NAME and
 * SPRING_SECURITY_USER_PASSWORD. Only the health check is public, for Fly.
 *
 * <p>CSRF tokens are off. Every write is a POST that must declare {@code consumes = JSON}, even
 * without a body, or a DELETE. A cross-site form cannot send that content type, and a cross-site
 * fetch with it needs a CORS preflight that this app never allows. A new write without it would be
 * open to CSRF, because the browser sends the cached Basic login along.
 */
@Configuration
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers("/actuator/health")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
