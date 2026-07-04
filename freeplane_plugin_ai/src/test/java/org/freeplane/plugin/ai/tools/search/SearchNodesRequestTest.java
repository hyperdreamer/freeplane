package org.freeplane.plugin.ai.tools.search;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SearchNodesRequestTest {
    @Test
    public void defaultsAndPresenceUseNullableRequestFields() {
        SearchNodesRequest request = new SearchNodesRequest(
            "map-identifier",
            "query",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

        assertThat(request.getMatchingMode()).isEqualTo(SearchMatchingMode.CONTAINS);
        assertThat(request.getCaseSensitivity()).isEqualTo(SearchCaseSensitivity.CASE_INSENSITIVE);
        assertThat(request.getOffset()).isEqualTo(0);
        assertThat(request.getLimit()).isEqualTo(200);
        assertThat(request.getMaxCharacters()).isEqualTo(65536);
        assertThat(request.hasMatchingMode()).isFalse();
        assertThat(request.hasCaseSensitivity()).isFalse();
        assertThat(request.hasOffset()).isFalse();
        assertThat(request.hasLimit()).isFalse();
        assertThat(request.getNodeContentRequestForSearch()).isNotNull();
        assertThat(request.getResultSections()).isEmpty();
    }

    @Test
    public void suppliedOptionalValuesArePresentAndNormalized() {
        SearchNodesRequest request = new SearchNodesRequest(
            "map-identifier",
            "query",
            null,
            null,
            SearchMatchingMode.EQUALS,
            SearchCaseSensitivity.CASE_SENSITIVE,
            null,
            -1,
            -2,
            1000);

        assertThat(request.getMatchingMode()).isEqualTo(SearchMatchingMode.EQUALS);
        assertThat(request.getCaseSensitivity()).isEqualTo(SearchCaseSensitivity.CASE_SENSITIVE);
        assertThat(request.getOffset()).isEqualTo(0);
        assertThat(request.getLimit()).isEqualTo(0);
        assertThat(request.getMaxCharacters()).isEqualTo(1000);
        assertThat(request.hasMatchingMode()).isTrue();
        assertThat(request.hasCaseSensitivity()).isTrue();
        assertThat(request.hasOffset()).isTrue();
        assertThat(request.hasLimit()).isTrue();
    }
}
