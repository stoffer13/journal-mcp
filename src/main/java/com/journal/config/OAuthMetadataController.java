package com.journal.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OauthMetadataController {

  @GetMapping("/.well-known/oauth-authorization-server")
  public Map<String, Object> metadata(HttpServletRequest request) {
    String issuer = issuerUrl(request);
    return Map.of(
        "issuer",
        issuer,
        "authorization_endpoint",
        issuer + "/oauth/authorize",
        "token_endpoint",
        issuer + "/oauth/token",
        "response_types_supported",
        new String[] {"code"},
        "grant_types_supported",
        new String[] {"authorization_code"},
        "code_challenge_methods_supported",
        new String[] {"S256"});
  }

  private String issuerUrl(HttpServletRequest request) {
    String scheme = request.getScheme();
    String host = request.getServerName();
    int port = request.getServerPort();
    boolean defaultPort =
        ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
  }
}
