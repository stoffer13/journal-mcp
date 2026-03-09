package com.journal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "journal.api-key=integration-test-key")
class OauthFlowIntegrationTest {

  @TempDir static Path tempDir;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("journal.data-dir", () -> tempDir.toString());
  }

  @Autowired private MockMvc mockMvc;

  private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
  private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
  private static final String REDIRECT_URI = "http://localhost:3000/callback";
  private static final String STATE = "integration-state";
  private static final String CLIENT_ID = "integration-client";

  @Test
  void fullOAuthFlow_metadataThroughTokenExchange() throws Exception {
    // Step 1: Fetch metadata
    mockMvc
        .perform(get("/.well-known/oauth-authorization-server"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authorization_endpoint").isString())
        .andExpect(jsonPath("$.token_endpoint").isString());

    // Step 2: GET authorize form
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
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

    // Step 3: POST authorize with correct API key
    MvcResult authorizeResult =
        mockMvc
            .perform(
                post("/oauth/authorize")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("api_key", "integration-test-key")
                    .param("redirect_uri", REDIRECT_URI)
                    .param("state", STATE)
                    .param("code_challenge", CHALLENGE)
                    .param("client_id", CLIENT_ID))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    // Extract authorization code from redirect
    String location = authorizeResult.getResponse().getHeader("Location");
    assertThat(location).isNotNull();
    UriComponents uri = UriComponentsBuilder.fromUriString(location).build();
    String code = uri.getQueryParams().getFirst("code");
    assertThat(code).isNotNull().isNotEmpty();
    assertThat(uri.getQueryParams().getFirst("state")).isEqualTo(STATE);

    // Step 4: Exchange code for token
    mockMvc
        .perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("code_verifier", VERIFIER)
                .param("client_id", CLIENT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("integration-test-key"))
        .andExpect(jsonPath("$.token_type").value("bearer"));

    // Step 5: Verify code is single-use
    mockMvc
        .perform(
            post("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", REDIRECT_URI)
                .param("code_verifier", VERIFIER)
                .param("client_id", CLIENT_ID))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_grant"));
  }
}
