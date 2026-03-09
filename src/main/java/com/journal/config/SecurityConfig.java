package com.journal.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final ApiKeyFilter apiKeyFilter;

  public SecurityConfig(ApiKeyFilter apiKeyFilter) {
    this.apiKeyFilter = apiKeyFilter;
  }

  @Bean
  public UserDetailsService userDetailsService() {
    return username -> {
      throw new UsernameNotFoundException("Not used");
    };
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .anonymous(Customizer.withDefaults())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health",
                        "/.well-known/oauth-authorization-server",
                        "/.well-known/oauth-protected-resource",
                        "/oauth/authorize",
                        "/oauth/token",
                        "/oauth/register")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) -> {
                      String scheme = request.getScheme();
                      String host = request.getServerName();
                      int port = request.getServerPort();
                      boolean defaultPort =
                          ("http".equals(scheme) && port == 80)
                              || ("https".equals(scheme) && port == 443);
                      String base =
                          defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
                      response.setHeader(
                          "WWW-Authenticate",
                          "Bearer resource_metadata=\""
                              + base
                              + "/.well-known/oauth-protected-resource\"");
                      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    }))
        .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
