package org.freeplane.plugin.ai.mcpserver;

import com.sun.net.httpserver.Headers;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JOptionPane;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.ui.ViewController;

class MCPAuthenticator {
    private static final int UNAUTHORIZED_ERROR_CODE = -32001;
    private static final String UNAUTHORIZED_MESSAGE = "Unauthorized";
    private static final String GENERATED_MCP_TOKEN_MESSAGE = "ai_mcp_token_generated_message";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ResourceController resourceController;
    private final String apiKeyPropertyName;
    private final String apiKeyHeaderName;
    private final EventDispatchInvoker eventDispatchInvoker;

    MCPAuthenticator(ResourceController resourceController, ViewController viewController,
                     String apiKeyPropertyName, String apiKeyHeaderName) {
        this(resourceController, apiKeyPropertyName, apiKeyHeaderName, viewController::invokeAndWait);
    }

    MCPAuthenticator(ResourceController resourceController, String apiKeyPropertyName,
                     String apiKeyHeaderName, EventDispatchInvoker eventDispatchInvoker) {
        this.resourceController = resourceController;
        this.apiKeyPropertyName = apiKeyPropertyName;
        this.apiKeyHeaderName = apiKeyHeaderName;
        this.eventDispatchInvoker = eventDispatchInvoker;
    }

    Object authenticateRequest(Headers requestHeaders) {
        AtomicReference<Object> responseReference = new AtomicReference<>();
        AtomicReference<Throwable> errorReference = new AtomicReference<>();
        Runnable runnable = () -> {
            try {
                responseReference.set(authenticateOnEdt(requestHeaders));
            } catch (Throwable throwable) {
                errorReference.set(throwable);
            }
        };
        try {
            eventDispatchInvoker.invokeAndWait(runnable);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP authentication was interrupted.", interruptedException);
        } catch (InvocationTargetException invocationTargetException) {
            throw toRuntimeException(invocationTargetException.getCause(), "MCP authentication failed.");
        }
        Throwable error = errorReference.get();
        if (error != null) {
            throw toRuntimeException(error, "MCP authentication failed.");
        }
        return responseReference.get();
    }

    private Object authenticateOnEdt(Headers requestHeaders) {
        String configuredApiKey = trimToEmpty(resourceController.getProperty(apiKeyPropertyName, ""));
        if (configuredApiKey.isEmpty()) {
            String generatedApiKey = generateToken();
            resourceController.setProperty(apiKeyPropertyName, generatedApiKey);
            notifyTokenGenerated(generatedApiKey);
            return buildUnauthorizedResponse();
        }
        String legacyHeaderToken = trimToEmpty(requestHeaders == null ? null : requestHeaders.getFirst(apiKeyHeaderName));
        BearerToken bearerToken = parseBearerToken(requestHeaders);
        if (!bearerToken.valid) {
            return buildUnauthorizedResponse();
        }
        if (!legacyHeaderToken.isEmpty() && !bearerToken.token.isEmpty() && !legacyHeaderToken.equals(bearerToken.token)) {
            return buildUnauthorizedResponse();
        }
        String providedApiKey = !bearerToken.token.isEmpty() ? bearerToken.token : legacyHeaderToken;
        if (!configuredApiKey.equals(providedApiKey)) {
            return buildUnauthorizedResponse();
        }
        return null;
    }

    private BearerToken parseBearerToken(Headers requestHeaders) {
        String authorizationHeader = trimToEmpty(requestHeaders == null ? null : requestHeaders.getFirst(AUTHORIZATION_HEADER));
        if (authorizationHeader.isEmpty()) {
            return BearerToken.missing();
        }
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            return BearerToken.invalid();
        }
        String tokenValue = trimToEmpty(authorizationHeader.substring(BEARER_PREFIX.length()));
        if (tokenValue.isEmpty()) {
            return BearerToken.invalid();
        }
        return BearerToken.valid(tokenValue);
    }

    String generateToken() {
        return UUID.randomUUID().toString();
    }

    void notifyTokenGenerated(String generatedApiKey) {
        UITools.showMessage(
            TextUtils.format(GENERATED_MCP_TOKEN_MESSAGE, generatedApiKey),
            JOptionPane.INFORMATION_MESSAGE);
    }

    private RuntimeException toRuntimeException(Throwable throwable, String fallbackMessage) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(fallbackMessage, throwable);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private Object buildUnauthorizedResponse() {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", UNAUTHORIZED_ERROR_CODE);
        error.put("message", UNAUTHORIZED_MESSAGE);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", null);
        response.put("error", error);
        return response;
    }

    @FunctionalInterface
    interface EventDispatchInvoker {
        void invokeAndWait(Runnable runnable) throws InterruptedException, InvocationTargetException;
    }

    private static class BearerToken {
        private final boolean valid;
        private final String token;

        private BearerToken(boolean valid, String token) {
            this.valid = valid;
            this.token = token;
        }

        static BearerToken missing() {
            return new BearerToken(true, "");
        }

        static BearerToken valid(String token) {
            return new BearerToken(true, token);
        }

        static BearerToken invalid() {
            return new BearerToken(false, "");
        }
    }
}
