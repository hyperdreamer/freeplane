package org.freeplane.plugin.ai.chat;

import java.util.Locale;
import org.freeplane.api.ai.AiRequestStatus;

public class AiRequestStatusMapper {
    private AiRequestStatusMapper() {
    }

    public static AiRequestStatus fromFailure(Throwable error) {
        Throwable rootCause = rootCause(error);
        String message = normalizeMessage(rootCause.getMessage());
        if (isAuthenticationError(message)) {
            return AiRequestStatus.AUTHENTICATION_ERROR;
        }
        if (isModelUnavailable(message)) {
            return AiRequestStatus.MODEL_UNAVAILABLE;
        }
        if (isProviderError(message)) {
            return AiRequestStatus.PROVIDER_ERROR;
        }
        return AiRequestStatus.FAILED;
    }

    public static String detailMessage(Throwable error) {
        Throwable rootCause = rootCause(error);
        String message = rootCause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return rootCause.getClass().getSimpleName();
        }
        return message;
    }

    public static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current == null ? new RuntimeException("Unknown AI request failure") : current;
    }

    private static String normalizeMessage(String message) {
        return message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isAuthenticationError(String message) {
        return containsAny(message,
            "unauthorized",
            "invalid api key",
            "invalid key",
            "authentication",
            "authentication failed",
            "api key",
            "forbidden",
            "permission denied",
            "status code 401",
            "status code 403",
            "http 401",
            "http 403");
    }

    private static boolean isModelUnavailable(String message) {
        return containsAny(message,
            "model not found",
            "unknown model",
            "model unavailable",
            "does not exist",
            "unsupported model",
            "no such model");
    }

    private static boolean isProviderError(String message) {
        return containsAny(message,
            "http ",
            "timed out",
            "timeout",
            "connection reset",
            "connection refused",
            "service unavailable",
            "server error",
            "bad gateway",
            "gateway timeout",
            "network",
            "io exception",
            "i/o error",
            "connection");
    }

    private static boolean containsAny(String message, String... needles) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (message.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
