package com.journal.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OauthMetadataController {

  @GetMapping("/.well-known/oauth-authorization-server")
  public Map<String, Object> metadata(HttpServletRequest request) {
    String issuer = issuerUrl(request);
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("issuer", issuer);
    meta.put("authorization_endpoint", issuer + "/oauth/authorize");
    meta.put("token_endpoint", issuer + "/oauth/token");
    meta.put("registration_endpoint", issuer + "/oauth/register");
    meta.put("response_types_supported", new String[] {"code"});
    meta.put("grant_types_supported", new String[] {"authorization_code"});
    meta.put("code_challenge_methods_supported", new String[] {"S256"});
    return meta;
  }

  @GetMapping("/.well-known/oauth-protected-resource")
  public Map<String, Object> resourceMetadata(HttpServletRequest request) {
    String resource = issuerUrl(request);
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("resource", resource);
    meta.put("authorization_servers", new String[] {resource});
    meta.put("bearer_methods_supported", new String[] {"header"});
    return meta;
  }

  String issuerUrl(HttpServletRequest request) {
    String scheme = request.getScheme();
    String host = request.getServerName();
    int port = request.getServerPort();
    boolean defaultPort =
        ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
  }
}
