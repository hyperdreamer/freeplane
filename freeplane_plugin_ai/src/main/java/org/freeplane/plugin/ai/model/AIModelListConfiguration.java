package org.freeplane.plugin.ai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class AIModelListConfiguration {
    private final AIModelListMode mode;
    private final List<String> literalModelNames;
    private final List<Pattern> wildcardPatterns;

    private AIModelListConfiguration(AIModelListMode mode,
                                     List<String> literalModelNames,
                                     List<Pattern> wildcardPatterns) {
        this.mode = mode;
        this.literalModelNames = Collections.unmodifiableList(literalModelNames);
        this.wildcardPatterns = Collections.unmodifiableList(wildcardPatterns);
    }

    public static AIModelListConfiguration parse(String value) {
        Set<String> literals = new LinkedHashSet<>();
        Set<String> wildcards = new LinkedHashSet<>();
        if (value != null) {
            for (String entry : value.split("[,\\r\\n]+")) {
                String modelName = entry.trim();
                if (modelName.isEmpty()) {
                    continue;
                }
                if (containsWildcard(modelName)) {
                    wildcards.add(modelName);
                }
                else {
                    literals.add(modelName);
                }
            }
        }
        if (!literals.isEmpty()) {
            return new AIModelListConfiguration(
                AIModelListMode.EXPLICIT,
                new ArrayList<>(literals),
                Collections.<Pattern>emptyList());
        }
        List<Pattern> patterns = new ArrayList<>();
        for (String wildcard : wildcards) {
            patterns.add(Pattern.compile(toRegex(wildcard)));
        }
        return new AIModelListConfiguration(
            AIModelListMode.AUTOMATIC,
            Collections.<String>emptyList(),
            patterns);
    }

    public AIModelListMode getMode() {
        return mode;
    }

    public List<String> getLiteralModelNames() {
        return literalModelNames;
    }

    public List<Pattern> getWildcardPatterns() {
        return wildcardPatterns;
    }

    public boolean isExplicit() {
        return mode == AIModelListMode.EXPLICIT;
    }

    public boolean accepts(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return false;
        }
        if (isExplicit()) {
            return literalModelNames.contains(modelName);
        }
        if (wildcardPatterns.isEmpty()) {
            return true;
        }
        for (Pattern pattern : wildcardPatterns) {
            if (pattern.matcher(modelName).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWildcard(String value) {
        return value.indexOf('*') >= 0 || value.indexOf('?') >= 0;
    }

    private static String toRegex(String wildcard) {
        StringBuilder regex = new StringBuilder();
        for (int index = 0; index < wildcard.length(); index++) {
            char character = wildcard.charAt(index);
            if (character == '*') {
                regex.append(".*");
            }
            else if (character == '?') {
                regex.append('.');
            }
            else {
                if ("\\.^$|()[]{}+".indexOf(character) >= 0) {
                    regex.append('\\');
                }
                regex.append(character);
            }
        }
        return regex.toString();
    }
}
