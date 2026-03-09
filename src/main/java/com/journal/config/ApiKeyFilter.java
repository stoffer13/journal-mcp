package com.journal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

  private final byte[] expectedKey;

  public ApiKeyFilter(@Value("${journal.api-key}") String apiKey) {
    this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      if (MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), expectedKey)) {
        var auth =
            new UsernamePasswordAuthenticationToken(
                "api-client", null, List.of(new SimpleGrantedAuthority("ROLE_API")));
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    }

    // Wrap the response to add WWW-Authenticate on 401
    var wrappedResponse = new WwwAuthenticateResponseWrapper(response, request);
    filterChain.doFilter(request, wrappedResponse);
  }

  /**
   * Response wrapper that adds WWW-Authenticate header when Spring Security sends a 401. This tells
   * MCP clients like Claude Desktop where to find the OAuth metadata.
   */
  private static class WwwAuthenticateResponseWrapper
      extends jakarta.servlet.http.HttpServletResponseWrapper {

    private final HttpServletRequest request;

    WwwAuthenticateResponseWrapper(HttpServletResponse response, HttpServletRequest request) {
      super(response);
      this.request = request;
    }

    @Override
    public void setStatus(int sc) {
      if (sc == SC_UNAUTHORIZED) {
        addWwwAuthenticate();
      }
      super.setStatus(sc);
    }

    @Override
    public void sendError(int sc) throws IOException {
      if (sc == SC_UNAUTHORIZED) {
        addWwwAuthenticate();
      }
      super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      if (sc == SC_UNAUTHORIZED) {
        addWwwAuthenticate();
      }
      super.sendError(sc, msg);
    }

    private void addWwwAuthenticate() {
      if (!containsHeader("WWW-Authenticate")) {
        String resourceMetadataUrl = buildResourceMetadataUrl();
        setHeader("WWW-Authenticate", "Bearer resource_metadata=\"" + resourceMetadataUrl + "\"");
      }
    }

    private String buildResourceMetadataUrl() {
      String scheme = request.getScheme();
      String host = request.getServerName();
      int port = request.getServerPort();
      boolean defaultPort =
          ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
      String base = defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
      return base + "/.well-known/oauth-protected-resource";
    }
  }
}
