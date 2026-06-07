package org.freeplane.plugin.ai.tools.content;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.freeplane.plugin.ai.tools.search.SearchCaseSensitivity;
import org.freeplane.plugin.ai.tools.search.SearchMatchingMode;

public class NodeContentValueMatcher {
    private final String queryText;
    private final SearchMatchingMode matchingMode;
    private final SearchCaseSensitivity caseSensitivity;
    private final Pattern regularExpression;

    public NodeContentValueMatcher(String queryText, SearchMatchingMode matchingMode,
                                   SearchCaseSensitivity caseSensitivity, Pattern regularExpression) {
        this.queryText = queryText;
        this.matchingMode = Objects.requireNonNull(matchingMode, "matchingMode");
        this.caseSensitivity = Objects.requireNonNull(caseSensitivity, "caseSensitivity");
        this.regularExpression = regularExpression;
    }

    public boolean matchesValue(String value) {
        if (value == null || queryText == null) {
            return false;
        }
        if (matchingMode == SearchMatchingMode.REGULAR_EXPRESSION) {
            return regularExpression != null && regularExpression.matcher(value).find();
        }
        String valueToMatch = value;
        String queryToMatch = queryText;
        if (caseSensitivity == SearchCaseSensitivity.CASE_INSENSITIVE) {
            valueToMatch = value.toLowerCase(Locale.ROOT);
            queryToMatch = queryText.toLowerCase(Locale.ROOT);
        }
        switch (matchingMode) {
            case CONTAINS:
                return valueToMatch.contains(queryToMatch);
            case EQUALS:
                return valueToMatch.equals(queryToMatch);
            default:
                return false;
        }
    }
}
