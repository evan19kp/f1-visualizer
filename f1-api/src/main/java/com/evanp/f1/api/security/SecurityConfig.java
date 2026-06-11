package com.evanp.f1.api.security;

import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        if (!isProduction(environment)) {
            http.csrf(csrf -> csrf.disable());
        }
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**", "/api/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    private static boolean isProduction(Environment environment) {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
