package org.freeplane.plugin.ai.mcpserver;

import com.sun.net.httpserver.Headers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.core.resources.ResourceController;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MCPAuthenticatorTest {
    private static final String API_KEY_PROPERTY = "ai_mcp_token";
    private static final String API_KEY_HEADER = "X-Freeplane-MCP-Token";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Test
    public void blankConfiguredApiKey_generatesAndPersistsAndRejectsCurrentRequest() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty(API_KEY_PROPERTY, "")).thenReturn("  ");
        MCPAuthenticator uut = new MCPAuthenticator(
            resourceController,
            API_KEY_PROPERTY,
            API_KEY_HEADER,
            Runnable::run) {
            @Override
            String generateToken() {
                return "generated-key";
            }

            @Override
            void notifyTokenGenerated(String generatedApiKey) {
            }
        };

        Object response = uut.authenticateRequest(new Headers());

        assertUnauthorized(response);
        verify(resourceController).setProperty(API_KEY_PROPERTY, "generated-key");
    }

    @Test
    public void matchingHeader_allowsRequest() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty(API_KEY_PROPERTY, "")).thenReturn("expected-key");
        Headers requestHeaders = new Headers();
        requestHeaders.add(API_KEY_HEADER, "expected-key");
        MCPAuthenticator uut = new MCPAuthenticator(
            resourceController,
            API_KEY_PROPERTY,
            API_KEY_HEADER,
            Runnable::run);

        Object response = uut.authenticateRequest(requestHeaders);

        assertThat(response).isNull();
        verify(resourceController, never()).setProperty(API_KEY_PROPERTY, "generated-key");
    }

    @Test
    public void missingHeader_rejectsRequestWhenApiKeyIsConfigured() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty(API_KEY_PROPERTY, "")).thenReturn("expected-key");
        MCPAuthenticator uut = new MCPAuthenticator(
            resourceController,
            API_KEY_PROPERTY,
            API_KEY_HEADER,
            Runnable::run);

        Object response = uut.authenticateRequest(new Headers());

        assertUnauthorized(response);
        verify(resourceController, never()).setProperty(API_KEY_PROPERTY, "generated-key");
    }

    @Test
    public void matchingBearerHeader_allowsRequest() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty(API_KEY_PROPERTY, "")).thenReturn("expected-key");
        Headers requestHeaders = new Headers();
        requestHeaders.add(AUTHORIZATION_HEADER, "Bearer expected-key");
        MCPAuthenticator uut = new MCPAuthenticator(
            resourceController,
            API_KEY_PROPERTY,
            API_KEY_HEADER,
            Runnable::run);

        Object response = uut.authenticateRequest(requestHeaders);

        assertThat(response).isNull();
    }

    @Test
    public void invalidBearerHeader_rejectsRequestWhenApiKeyIsConfigured() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty(API_KEY_PROPERTY, "")).thenReturn("expected-key");
        Headers requestHeaders = new Headers();
        requestHeaders.add(AUTHORIZATION_HEADER, "Token expected-key");
        MCPAuthenticator uut = new MCPAuthenticator(
            resourceController,
            API_KEY_PROPERTY,
            API_KEY_HEADER,
            Runnable::run);

        Object response = uut.authenticateRequest(requestHeaders);

        assertUnauthorized(response);
    }

    @Test
    public void mismatchedBearerAndLegacyHeaders_rejectRequestWhenApiKeyIsConfigured() {
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty(API_KEY_PROPERTY, "")).thenReturn("expected-key");
        Headers requestHeaders = new Headers();
        requestHeaders.add(AUTHORIZATION_HEADER, "Bearer expected-key");
        requestHeaders.add(API_KEY_HEADER, "different-key");
        MCPAuthenticator uut = new MCPAuthenticator(
            resourceController,
            API_KEY_PROPERTY,
            API_KEY_HEADER,
            Runnable::run);

        Object response = uut.authenticateRequest(requestHeaders);

        assertUnauthorized(response);
    }

    @Test
    public void parallelRequests_generateAndNotifyOnlyOnce_whenValidationRunsInSingleEventThread() throws Exception {
        ResourceController resourceController = mock(ResourceController.class);
        AtomicReference<String> tokenReference = new AtomicReference<>(" ");
        AtomicInteger generatedCount = new AtomicInteger();
        AtomicInteger notificationCount = new AtomicInteger();
        when(resourceController.getProperty(API_KEY_PROPERTY, "")).thenAnswer(invocation -> {
            String value = tokenReference.get();
            if (value.trim().isEmpty()) {
                Thread.sleep(5);
            }
            return value;
        });
        doAnswer(invocation -> {
                tokenReference.set(invocation.getArgument(1));
                return null;
            }).when(resourceController).setProperty(eq(API_KEY_PROPERTY), anyString());
        MCPAuthenticator.EventDispatchInvoker serialInvoker = runnable -> {
            synchronized (this) {
                runnable.run();
            }
        };
        MCPAuthenticator uut = new MCPAuthenticator(
            resourceController,
            API_KEY_PROPERTY,
            API_KEY_HEADER,
            serialInvoker) {
            @Override
            String generateToken() {
                return "generated-key-" + generatedCount.incrementAndGet();
            }

            @Override
            void notifyTokenGenerated(String generatedApiKey) {
                notificationCount.incrementAndGet();
            }
        };

        int threadCount = 12;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> errors = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    Object response = uut.authenticateRequest(new Headers());
                    assertUnauthorized(response);
                } catch (Throwable throwable) {
                    synchronized (errors) {
                        errors.add(throwable);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
            thread.start();
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        assertThat(errors).isEmpty();
        assertThat(generatedCount.get()).isEqualTo(1);
        assertThat(notificationCount.get()).isEqualTo(1);
        assertThat(tokenReference.get()).isEqualTo("generated-key-1");
    }

    @SuppressWarnings("unchecked")
    private void assertUnauthorized(Object response) {
        assertThat(response).isInstanceOf(Map.class);
        Map<String, Object> responseMap = (Map<String, Object>) response;
        assertThat(responseMap.get("jsonrpc")).isEqualTo("2.0");
        assertThat(responseMap.get("id")).isNull();
        assertThat(responseMap.get("error")).isInstanceOf(Map.class);
        Map<String, Object> error = (Map<String, Object>) responseMap.get("error");
        assertThat(error.get("code")).isEqualTo(-32001);
        assertThat(error.get("message")).isEqualTo("Unauthorized");
    }
}
