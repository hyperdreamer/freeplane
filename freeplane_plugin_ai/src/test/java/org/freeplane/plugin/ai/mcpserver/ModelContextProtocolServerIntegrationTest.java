package org.freeplane.plugin.ai.mcpserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModelContextProtocolServerIntegrationTest {
    private static final String TEST_API_KEY = "integration-test-api-key";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ModelContextProtocolServer uut;
    private String serverUrl;

    @Before
    public void setUp() throws Exception {
        int port = findAvailablePort();
        AIToolSet toolSet = mock(AIToolSet.class);
        ResourceController resourceController = mock(ResourceController.class);
        when(resourceController.getProperty(ModelContextProtocolServer.MCP_TOKEN_PROPERTY, "")).thenReturn(TEST_API_KEY);
        MCPAuthenticator authenticator = new MCPAuthenticator(
            resourceController,
            ModelContextProtocolServer.MCP_TOKEN_PROPERTY,
            ModelContextProtocolServer.MCP_TOKEN_HEADER,
            Runnable::run);
        uut = new ModelContextProtocolServer(toolSet, objectMapper, resourceController, authenticator);
        uut.start(port);
        serverUrl = "http://127.0.0.1:" + port + "/";
    }

    @After
    public void tearDown() {
        if (uut != null) {
            uut.stop();
        }
    }

    @Test
    public void initializeAndListRequests_returnExpectedPayloads() throws Exception {
        Response initializeResponse = sendJsonRequest("initialize", 1, null);
        assertThat(initializeResponse.status).isEqualTo(200);
        JsonNode initializeNode = objectMapper.readTree(initializeResponse.body);
        assertThat(initializeNode.path("result").path("protocolVersion").isTextual()).isTrue();
        assertThat(initializeNode.path("result").path("capabilities").isObject()).isTrue();
        assertThat(initializeNode.path("result").path("serverInfo").isObject()).isTrue();

        Response toolsListResponse = sendJsonRequest("tools/list", 2, null);
        assertThat(toolsListResponse.status).isEqualTo(200);
        JsonNode toolsListNode = objectMapper.readTree(toolsListResponse.body);
        assertThat(toolsListNode.path("result").path("tools").isArray()).isTrue();

        Map<String, Object> readParameters = new LinkedHashMap<>();
        readParameters.put("uri", "mcp://tools");
        Response resourcesReadResponse = sendJsonRequest("resources/read", 3, readParameters);
        assertThat(resourcesReadResponse.status).isEqualTo(200);
        JsonNode resourcesReadNode = objectMapper.readTree(resourcesReadResponse.body);
        assertThat(resourcesReadNode.path("result").path("contents").isArray()).isTrue();
        assertThat(resourcesReadNode.path("result").path("contents").get(0).path("uri").asText())
            .isEqualTo("mcp://tools");
    }

    @Test
    public void nonPostRequests_returnMethodNotAllowed() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(serverUrl).openConnection();
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        connection.disconnect();
        assertThat(status).isEqualTo(405);
    }

    @Test
    public void notificationRequest_returnsNoContent() throws Exception {
        Response response = sendJsonRequest("initialize", null, null);
        assertThat(response.status).isEqualTo(204);
        assertThat(response.body).isEmpty();
    }

    @Test
    public void requestWithoutApiKeyHeader_returnsUnauthorized() throws Exception {
        Response response = sendJsonRequest("initialize", 1, null, false);
        assertThat(response.status).isEqualTo(401);
    }

    @Test
    public void requestWithBearerHeader_returnsSuccess() throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(ModelContextProtocolServer.AUTHORIZATION_HEADER, "Bearer " + TEST_API_KEY);
        Response response = sendJsonRequest("initialize", 1, null, headers);
        assertThat(response.status).isEqualTo(200);
    }

    @Test
    public void requestWithMismatchedBearerAndLegacyHeaders_returnsUnauthorized() throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(ModelContextProtocolServer.AUTHORIZATION_HEADER, "Bearer " + TEST_API_KEY);
        headers.put(ModelContextProtocolServer.MCP_TOKEN_HEADER, "different-token");
        Response response = sendJsonRequest("initialize", 1, null, headers);
        assertThat(response.status).isEqualTo(401);
    }

    private Response sendJsonRequest(String methodName, Object id, Map<String, Object> parameters) throws Exception {
        return sendJsonRequest(methodName, id, parameters, true);
    }

    private Response sendJsonRequest(String methodName, Object id, Map<String, Object> parameters, boolean includeApiKeyHeader)
        throws Exception {
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        if (includeApiKeyHeader) {
            requestHeaders.put(ModelContextProtocolServer.MCP_TOKEN_HEADER, TEST_API_KEY);
        }
        return sendJsonRequest(methodName, id, parameters, requestHeaders);
    }

    private Response sendJsonRequest(String methodName, Object id, Map<String, Object> parameters, Map<String, String> requestHeaders)
        throws Exception {
        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("jsonrpc", "2.0");
        requestPayload.put("method", methodName);
        if (id != null) {
            requestPayload.put("id", id);
        }
        if (parameters != null) {
            requestPayload.put("params", parameters);
        }
        byte[] payloadBytes = objectMapper.writeValueAsBytes(requestPayload);
        HttpURLConnection connection = (HttpURLConnection) new URL(serverUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        for (Map.Entry<String, String> requestHeader : requestHeaders.entrySet()) {
            connection.setRequestProperty(requestHeader.getKey(), requestHeader.getValue());
        }
        connection.setFixedLengthStreamingMode(payloadBytes.length);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payloadBytes);
        }
        int status = connection.getResponseCode();
        byte[] responseBody = readResponseBody(connection);
        connection.disconnect();
        return new Response(status, responseBody);
    }

    private byte[] readResponseBody(HttpURLConnection connection) throws IOException {
        InputStream inputStream = connection.getResponseCode() >= 400
            ? connection.getErrorStream()
            : connection.getInputStream();
        if (inputStream == null) {
            return new byte[0];
        }
        try (InputStream responseStream = inputStream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = responseStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private int findAvailablePort() throws IOException {
        int port = 0;
        while (port < 1024) {
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
        }
        return port;
    }

    private static class Response {
        private final int status;
        private final byte[] body;

        private Response(int status, byte[] body) {
            this.status = status;
            this.body = body == null ? new byte[0] : body;
        }

    }
}
