package org.freeplane.plugin.ai.model;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AIModelListConfigurationTest {
    @Test
    public void emptyAndWildcardOnlyValuesUseAutomaticMode() {
        AIModelListConfiguration empty = AIModelListConfiguration.parse(" \n ");
        AIModelListConfiguration wildcard = AIModelListConfiguration.parse("openai/*, *-tool?");

        assertThat(empty.getMode()).isEqualTo(AIModelListMode.AUTOMATIC);
        assertThat(empty.accepts("anything")).isTrue();
        assertThat(wildcard.getMode()).isEqualTo(AIModelListMode.AUTOMATIC);
        assertThat(wildcard.accepts("openai/gpt-5")).isTrue();
        assertThat(wildcard.accepts("other-tool1")).isTrue();
        assertThat(wildcard.accepts("other-model")).isFalse();
    }

    @Test
    public void literalEntryMakesWholeValueExplicit() {
        AIModelListConfiguration configuration = AIModelListConfiguration.parse(
            "openai/*, exact-model, exact-model, other?model");

        assertThat(configuration.getMode()).isEqualTo(AIModelListMode.EXPLICIT);
        assertThat(configuration.getLiteralModelNames()).containsExactly("exact-model");
        assertThat(configuration.getWildcardPatterns()).isEmpty();
        assertThat(configuration.accepts("exact-model")).isTrue();
        assertThat(configuration.accepts("openai/gpt-5")).isFalse();
    }
}
