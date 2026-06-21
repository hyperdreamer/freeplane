package org.freeplane.plugin.ai.chat.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.freeplane.plugin.ai.prompt.AiPrompt;

class PromptReferenceResolver {

    List<AiPrompt> completionCandidates(String text, int caretPosition, List<AiPrompt> prompts) {
        String safeText = safe(text);
        if (!safeText.startsWith("/")) {
            return Collections.emptyList();
        }
        int safeCaret = Math.max(1, Math.min(caretPosition, safeText.length()));
        if (safeCaret > leadingScanWindowEnd(safeText, prompts)) {
            return Collections.emptyList();
        }
        String query = safeText.substring(1, safeCaret);
        List<AiPrompt> candidates = new ArrayList<AiPrompt>();
        for (AiPrompt prompt : safePrompts(prompts)) {
            String promptName = safe(prompt.getName()).trim();
            if (promptName.isEmpty()) {
                continue;
            }
            if (query.isEmpty() || matchesQueryAtWordStart(promptName, query)) {
                candidates.add(prompt.copy());
            }
        }
        return candidates;
    }

    PromptReferenceMatch resolveLeadingReference(String text, List<AiPrompt> prompts) {
        String visibleText = safe(text);
        if (!visibleText.startsWith("/")) {
            return null;
        }
        AiPrompt bestPrompt = null;
        String bestName = "";
        for (AiPrompt prompt : safePrompts(prompts)) {
            String promptName = safe(prompt.getName()).trim();
            if (promptName.isEmpty() || promptName.length() < bestName.length()) {
                continue;
            }
            int referenceEndOffset = 1 + promptName.length();
            if (referenceEndOffset > visibleText.length()) {
                continue;
            }
            if (!visibleText.regionMatches(true, 1, promptName, 0, promptName.length())) {
                continue;
            }
            if (referenceEndOffset < visibleText.length()
                && !Character.isWhitespace(visibleText.charAt(referenceEndOffset))) {
                continue;
            }
            if (promptName.length() > bestName.length()) {
                bestPrompt = prompt;
                bestName = promptName;
            }
        }
        if (bestPrompt == null) {
            return null;
        }
        int referenceEndOffset = 1 + bestName.length();
        String promptText = safe(bestPrompt.getPrompt());
        String modelFacingText = promptText + visibleText.substring(referenceEndOffset);
        return new PromptReferenceMatch(
            visibleText,
            modelFacingText,
            bestName,
            promptText,
            0,
            referenceEndOffset);
    }

    int maximumPromptNameLength(List<AiPrompt> prompts) {
        int maximumLength = 0;
        for (AiPrompt prompt : safePrompts(prompts)) {
            maximumLength = Math.max(maximumLength, safe(prompt.getName()).trim().length());
        }
        return maximumLength;
    }

    int leadingScanWindowEnd(String text, List<AiPrompt> prompts) {
        String safeText = safe(text);
        int maximumPromptNameLength = maximumPromptNameLength(prompts);
        if (maximumPromptNameLength == 0) {
            return Math.min(safeText.length(), 1);
        }
        return Math.min(safeText.length(), 1 + maximumPromptNameLength + 1);
    }

    boolean isPromptRelevantChange(String oldText,
                                   String newText,
                                   int changeOffset,
                                   List<AiPrompt> prompts) {
        String safeOldText = safe(oldText);
        String safeNewText = safe(newText);
        if (changeOffset <= 0) {
            return true;
        }
        boolean oldStartsWithSlash = safeOldText.startsWith("/");
        boolean newStartsWithSlash = safeNewText.startsWith("/");
        if (!oldStartsWithSlash && !newStartsWithSlash) {
            return false;
        }
        int oldWindowEnd = leadingScanWindowEnd(safeOldText, prompts);
        int newWindowEnd = leadingScanWindowEnd(safeNewText, prompts);
        return changeOffset <= Math.max(oldWindowEnd, newWindowEnd);
    }

    private boolean matchesQueryAtWordStart(String promptName, String query) {
        if (query.length() > promptName.length()) {
            return false;
        }
        int lastPossibleStart = promptName.length() - query.length();
        for (int index = 0; index <= lastPossibleStart; index++) {
            if (isWordStart(promptName, index)
                && promptName.regionMatches(true, index, query, 0, query.length())) {
                return true;
            }
        }
        return false;
    }

    private boolean isWordStart(String text, int index) {
        return index == 0 || Character.isWhitespace(text.charAt(index - 1));
    }

    private List<AiPrompt> safePrompts(List<AiPrompt> prompts) {
        if (prompts == null) {
            return Collections.emptyList();
        }
        List<AiPrompt> safePrompts = new ArrayList<AiPrompt>();
        for (AiPrompt prompt : prompts) {
            if (prompt != null) {
                safePrompts.add(prompt);
            }
        }
        return safePrompts;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
