package com.journal.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * RFC 7591 Dynamic Client Registration. Accepts any registration request and returns a client_id.
 * Since this server uses a single API key for auth (entered by the user during the OAuth flow),
 * client registration is lightweight — we just need to issue unique client_ids so the OAuth flow
 * can proceed.
 */
@RestController
public class ClientRegistrationController {

  private final ConcurrentHashMap<String, Map<String, Object>> clients = new ConcurrentHashMap<>();

  @PostMapping("/oauth/register")
  public ResponseEntity<Map<String, Object>> register(
      @RequestBody Map<String, Object> registrationRequest) {
    String clientId = UUID.randomUUID().toString();
    clients.put(clientId, registrationRequest);

    String clientName =
        registrationRequest.containsKey("client_name")
            ? registrationRequest.get("client_name").toString()
            : "unknown";

    @SuppressWarnings("unchecked")
    List<String> redirectUris =
        registrationRequest.containsKey("redirect_uris")
            ? (List<String>) registrationRequest.get("redirect_uris")
            : List.of();

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("client_id", clientId);
    response.put("client_name", clientName);
    response.put("redirect_uris", redirectUris);
    response.put("grant_types", new String[] {"authorization_code"});
    response.put("response_types", new String[] {"code"});
    response.put("token_endpoint_auth_method", "none");

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
