package org.freeplane.plugin.ai.model;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AIModelCatalogCacheKeyTest {
    @Test
    public void authenticationFingerprintIsNonReversibleCacheIdentity() {
        AIModelCatalogCacheKey first = new AIModelCatalogCacheKey(
            "openai", "https://models", "https://metadata", "secret-one");
        AIModelCatalogCacheKey equivalent = new AIModelCatalogCacheKey(
            "openai", "https://models", "https://metadata", "secret-one");
        AIModelCatalogCacheKey changed = new AIModelCatalogCacheKey(
            "openai", "https://models", "https://metadata", "secret-two");

        assertThat(first).isEqualTo(equivalent);
        assertThat(first).isNotEqualTo(changed);
        assertThat(first.getAuthenticationFingerprint())
            .doesNotContain("secret-one")
            .hasSize(64);
    }
}
