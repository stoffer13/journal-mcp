package com.journal.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PkceUtilTest {

  @Test
  void computeS256Challenge_rfcTestVector() {
    // RFC 7636 Appendix B test vector
    String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    String expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    assertThat(PkceUtil.computeS256Challenge(verifier)).isEqualTo(expected);
  }

  @Test
  void computeS256Challenge_noPadding() {
    String challenge = PkceUtil.computeS256Challenge("test-verifier-value");
    assertThat(challenge).doesNotContain("=");
  }

  @Test
  void computeS256Challenge_urlSafeEncoding() {
    String challenge = PkceUtil.computeS256Challenge("another-test-verifier");
    assertThat(challenge).matches("^[A-Za-z0-9_-]+$");
  }

  @Test
  void computeS256Challenge_differentVerifiersDifferentChallenges() {
    String challenge1 = PkceUtil.computeS256Challenge("verifier-one");
    String challenge2 = PkceUtil.computeS256Challenge("verifier-two");
    assertThat(challenge1).isNotEqualTo(challenge2);
  }
}
