package com.journal.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OauthMetadataController.class)
@Import({SecurityConfig.class, ApiKeyFilter.class})
@TestPropertySource(properties = "journal.api-key=test-key-12345")
class OauthMetadataControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void metadata_returnsCorrectStructure() throws Exception {
    mockMvc
        .perform(get("/.well-known/oauth-authorization-server"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuer").isString())
        .andExpect(
            jsonPath("$.authorization_endpoint")
                .value(org.hamcrest.Matchers.endsWith("/oauth/authorize")))
        .andExpect(
            jsonPath("$.token_endpoint").value(org.hamcrest.Matchers.endsWith("/oauth/token")))
        .andExpect(jsonPath("$.response_types_supported[0]").value("code"))
        .andExpect(jsonPath("$.grant_types_supported[0]").value("authorization_code"))
        .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"));
  }
}
