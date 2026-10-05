package com.example.appointmentsystem.config;

import com.example.appointmentsystem.security.JwtAuthenticationFilter;
import com.example.appointmentsystem.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   UserDetailsService userDetailsService)
            throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtService, userDetailsService);

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                        // ===== v1.5 后台写权限加固 =====
                        // Service 写操作：仅 ADMIN
                        .requestMatchers(HttpMethod.POST, "/services").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/services/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/services/*").hasRole("ADMIN")
                        // Staff 写操作：仅 ADMIN
                        .requestMatchers(HttpMethod.POST, "/staff").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/staff/*").hasRole("ADMIN")
                        // StaffServiceMapping 写操作：仅 ADMIN
                        .requestMatchers(HttpMethod.POST, "/staff-services/*/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/staff-services/*/*").hasRole("ADMIN")
                        // ===== 既有 appointments / users 权限规则 =====
                        .requestMatchers(HttpMethod.GET, "/appointments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/appointments/status/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/appointments/time-range").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/appointments/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/appointments/*/confirm").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/appointments/*/complete").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/users/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/users/*").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"未认证或 token 无效\"}");
                        }))
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
