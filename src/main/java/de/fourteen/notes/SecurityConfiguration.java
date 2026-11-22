package de.fourteen.notes;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * One user with HTTP Basic, set through SPRING_SECURITY_USER_NAME and
 * SPRING_SECURITY_USER_PASSWORD. Only the health check is public, for Fly.
 *
 * <p>CSRF tokens are off. Writes accept JSON only, which a cross-site form cannot send, and a
 * cross-site fetch with JSON needs a CORS preflight that this app never allows.
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
