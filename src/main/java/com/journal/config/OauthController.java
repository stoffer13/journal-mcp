package com.journal.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class OauthController {

  private final byte[] expectedKey;
  private final String apiKey;
  private final AuthorizationCodeStore codeStore;

  public OauthController(
      @Value("${journal.api-key}") String apiKey, AuthorizationCodeStore codeStore) {
    this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
    this.apiKey = apiKey;
    this.codeStore = codeStore;
  }

  @GetMapping("/oauth/authorize")
  @ResponseBody
  public ResponseEntity<String> authorizeForm(
      @RequestParam("response_type") String responseType,
      @RequestParam("client_id") String clientId,
      @RequestParam("redirect_uri") String redirectUri,
      @RequestParam("state") String state,
      @RequestParam("code_challenge") String codeChallenge,
      @RequestParam("code_challenge_method") String codeChallengeMethod) {

    if (!"code".equals(responseType)) {
      return ResponseEntity.badRequest().body("Unsupported response_type");
    }
    if (!"S256".equals(codeChallengeMethod)) {
      return ResponseEntity.badRequest().body("Unsupported code_challenge_method");
    }

    String html =
        """
        <!DOCTYPE html>
        <html>
        <head><title>Journal MCP — Sign In</title></head>
        <body>
        <h2>Journal MCP — Sign In</h2>
        <form method="POST" action="/oauth/authorize">
          <label for="api_key">API Key:</label><br>
          <input type="password" id="api_key" name="api_key" required><br><br>
          <input type="hidden" name="redirect_uri" value="%s">
          <input type="hidden" name="state" value="%s">
          <input type="hidden" name="code_challenge" value="%s">
          <input type="hidden" name="client_id" value="%s">
          <button type="submit">Authorize</button>
        </form>
        </body>
        </html>
        """
            .formatted(
                escapeHtml(redirectUri),
                escapeHtml(state),
                escapeHtml(codeChallenge),
                escapeHtml(clientId));

    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
  }

  @PostMapping("/oauth/authorize")
  public void authorizeSubmit(
      @RequestParam("api_key") String submittedKey,
      @RequestParam("redirect_uri") String redirectUri,
      @RequestParam("state") String state,
      @RequestParam("code_challenge") String codeChallenge,
      @RequestParam("client_id") String clientId,
      HttpServletResponse response)
      throws IOException {

    if (!MessageDigest.isEqual(submittedKey.getBytes(StandardCharsets.UTF_8), expectedKey)) {
      response.setContentType("text/html");
      response.setStatus(HttpServletResponse.SC_OK);
      response
          .getWriter()
          .write(
              """
              <!DOCTYPE html>
              <html>
              <head><title>Journal MCP — Sign In</title></head>
              <body>
              <h2>Journal MCP — Sign In</h2>
              <p style="color:red;">Invalid API key. Please try again.</p>
              <form method="POST" action="/oauth/authorize">
                <label for="api_key">API Key:</label><br>
                <input type="password" id="api_key" name="api_key" required><br><br>
                <input type="hidden" name="redirect_uri" value="%s">
                <input type="hidden" name="state" value="%s">
                <input type="hidden" name="code_challenge" value="%s">
                <input type="hidden" name="client_id" value="%s">
                <button type="submit">Authorize</button>
              </form>
              </body>
              </html>
              """
                  .formatted(
                      escapeHtml(redirectUri),
                      escapeHtml(state),
                      escapeHtml(codeChallenge),
                      escapeHtml(clientId)));
      return;
    }

    String code = UUID.randomUUID().toString();
    codeStore.store(code, codeChallenge, redirectUri);

    String redirect =
        UriComponentsBuilder.fromUriString(redirectUri)
            .queryParam("code", code)
            .queryParam("state", state)
            .build()
            .toUriString();

    response.sendRedirect(redirect);
  }

  @PostMapping("/oauth/token")
  @ResponseBody
  public ResponseEntity<?> token(
      @RequestParam("grant_type") String grantType,
      @RequestParam("code") String code,
      @RequestParam("redirect_uri") String redirectUri,
      @RequestParam("code_verifier") String codeVerifier,
      @RequestParam("client_id") String clientId) {

    if (!"authorization_code".equals(grantType)) {
      return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
    }

    var pending = codeStore.consume(code);
    if (pending == null) {
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "error",
                  "invalid_grant",
                  "error_description",
                  "Invalid or expired authorization code"));
    }

    if (!pending.redirectUri().equals(redirectUri)) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "invalid_grant", "error_description", "redirect_uri mismatch"));
    }

    String computedChallenge = PkceUtil.computeS256Challenge(codeVerifier);
    if (!MessageDigest.isEqual(
        computedChallenge.getBytes(StandardCharsets.UTF_8),
        pending.codeChallenge().getBytes(StandardCharsets.UTF_8))) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "invalid_grant", "error_description", "PKCE verification failed"));
    }

    return ResponseEntity.ok(
        Map.of("access_token", apiKey, "token_type", "bearer", "expires_in", 86400));
  }

  private static String escapeHtml(String input) {
    if (input == null) {
      return "";
    }
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;");
  }
}
