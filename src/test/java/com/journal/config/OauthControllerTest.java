package com.journal.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OauthController.class)
@Import({SecurityConfig.class, ApiKeyFilter.class, AuthorizationCodeStore.class})
@TestPropertySource(properties = "journal.api-key=test-key-12345")
class OauthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private AuthorizationCodeStore codeStore;

  private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
  private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
  private static final String REDIRECT_URI = "http://localhost:3000/callback";
  private static final String STATE = "random-state-value";
  private static final String CLIENT_ID = "test-client";

  // --- GET /oauth/authorize ---

  @Test
  void authorizeForm_validParams_returns200Html() throws Exception {
    mockMvc
        .perform(
            get("/oauth/authorize")
                .param("response_type", "code")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", STATE)
                .param("code_challenge", CHALLENGE)
                .param("code_challenge_method", "S256"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Journal MCP")));
  }

  @Test
  void authorizeForm_badResponseType_returns400() throws Exception {
    mockMvc
        .perform(
            get("/oauth/authorize")
                .param("response_type", "token")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", STATE)
                .param("code_challenge", CHALLENGE)
                .param("code_challenge_method", "S256"))
        .andExpect(status().isBadRequest())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Unsupported response_type")));
  }

  @Test
  void authorizeForm_badChallengeMethod_returns400() throws Exception {
    mockMvc
        .perform(
            get("/oauth/authorize")
                .param("response_type", "code")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("state", STATE)
                .param("code_challenge", CHALLENGE)
                .param("code_challenge_method", "plain"))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.containsString("Unsupported code_challenge_method")));
  }

  // --- POST /oauth/authorize ---

  @Test
  void authorizeSubmit_correctKey_redirectsWithCode() throws Exception {
    mockMvc
        .perform(
            post("/oauth/authorize")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("api_key", "test-key-12345")
                .param("redirect_uri", REDIRECT_URI)
                .param("state", STATE)
                .param("code_challenge", CHALLENGE)
                .param("client_id", CLIENT_ID))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(REDIRECT_URI)))
        .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code=")))
        .andExpect(
            header().string("Location", org.hamcrest.Matchers.containsString("state=" + STATE)));
  }

  @Test
  void authorizeSubmit_wrongKey_returnsErrorHtml() throws Exception {
    mockMvc
        .perform(
            post("/oauth/authorize")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("api_key", "wrong-key")
                .param("redirect_uri", REDIRECT_URI)
                .param("state", STATE)
                .param("code_challenge", CHALLENGE)
                .param("client_id", CLIENT_ID))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid API key")));
  }

  // --- POST /oauth/token ---

  @Test
  void token_validPkce_returnsAccessToken() throws Exception {
    codeStore.store("test-code", CHALLENGE, REDIRECT_URI);

    mockMvc
        .perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", "test-code")
                .param("redirect_uri", REDIRECT_URI)
                .param("code_verifier", VERIFIER)
                .param("client_id", CLIENT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("test-key-12345"))
        .andExpect(jsonPath("$.token_type").value("bearer"));
  }

  @Test
  void token_invalidCode_returnsError() throws Exception {
    mockMvc
        .perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", "bad-code")
                .param("redirect_uri", REDIRECT_URI)
                .param("code_verifier", VERIFIER)
                .param("client_id", CLIENT_ID))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_grant"));
  }

  @Test
  void token_redirectUriMismatch_returnsError() throws Exception {
    codeStore.store("code-redir", CHALLENGE, REDIRECT_URI);

    mockMvc
        .perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", "code-redir")
                .param("redirect_uri", "http://evil.com/callback")
                .param("code_verifier", VERIFIER)
                .param("client_id", CLIENT_ID))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error_description").value("redirect_uri mismatch"));
  }

  @Test
  void token_pkceFailure_returnsError() throws Exception {
    codeStore.store("code-pkce", CHALLENGE, REDIRECT_URI);

    mockMvc
        .perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", "code-pkce")
                .param("redirect_uri", REDIRECT_URI)
                .param("code_verifier", "wrong-verifier")
                .param("client_id", CLIENT_ID))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error_description").value("PKCE verification failed"));
  }

  @Test
  void token_codeReuse_returnsError() throws Exception {
    codeStore.store("code-reuse", CHALLENGE, REDIRECT_URI);

    // First use succeeds
    mockMvc
        .perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", "code-reuse")
                .param("redirect_uri", REDIRECT_URI)
                .param("code_verifier", VERIFIER)
                .param("client_id", CLIENT_ID))
        .andExpect(status().isOk());

    // Second use fails
    mockMvc
        .perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", "code-reuse")
                .param("redirect_uri", REDIRECT_URI)
                .param("code_verifier", VERIFIER)
                .param("client_id", CLIENT_ID))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_grant"));
  }

  @Test
  void token_unsupportedGrantType_returnsError() throws Exception {
    mockMvc
        .perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("code", "any")
                .param("redirect_uri", REDIRECT_URI)
                .param("code_verifier", VERIFIER)
                .param("client_id", CLIENT_ID))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
  }
}
