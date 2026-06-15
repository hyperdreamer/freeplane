package org.freeplane.plugin.ai.mcpserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.FreeplaneVersion;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ui.ViewController;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevelSettings;
import org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer;
import org.freeplane.plugin.ai.tools.formula.FormulaEditingSettings;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.freeplane.plugin.ai.tools.MapTargetToolCallAuthorizer;

public class ModelContextProtocolServer implements IFreeplanePropertyListener {
    public static final String MCP_SERVER_ENABLED_PROPERTY = "ai_mcp_server_enabled";
    public static final String MCP_SERVER_PORT_PROPERTY = "ai_mcp_server_port";
    public static final String MCP_TOKEN_PROPERTY = "ai_mcp_token";
    public static final String MCP_TOKEN_HEADER = "X-Freeplane-MCP-Token";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    private static final int DEFAULT_PORT = 6298;
    private static final int PORT_MINIMUM = 1024;
    private static final int PORT_MAXIMUM = 65535;
    private static final String SERVER_NAME = "Freeplane AI MCP Server";
    private static final String TOOLS_RESOURCE_URI = "mcp://tools";

    private final ObjectMapper objectMapper;
    private final ModelContextProtocolToolRegistry toolRegistry;
    private final ModelContextProtocolToolDispatcher toolDispatcher;
    private final MCPAuthenticator authenticator;
    private final ResourceController resourceController;
    private final AtomicBoolean running;
    private volatile HttpServer server;

    public ModelContextProtocolServer(AIToolSet toolSet, ViewController viewController) {
        this(Collections.<Object>singletonList(toolSet), new ObjectMapper(), ResourceController.getResourceController(),
            viewController);
    }

    public ModelContextProtocolServer(Collection<?> toolSets, ViewController viewController) {
        this(toolSets, new ObjectMapper(), ResourceController.getResourceController(), viewController);
    }

    public ModelContextProtocolServer(Collection<?> toolSets,
                                      AiCodeOperationAuthorizer aiCodeOperationAuthorizer,
                                      ViewController viewController) {
        this(toolSets, new ObjectMapper(), ResourceController.getResourceController(), viewController,
            aiCodeOperationAuthorizer);
    }

    public ModelContextProtocolServer(Collection<?> toolSets,
                                      AiCodeOperationAuthorizer aiCodeOperationAuthorizer,
                                      ViewController viewController,
                                      ModelContextProtocolAiCodeHostService aiCodeHostService) {
        this(toolSets, new ObjectMapper(), ResourceController.getResourceController(), viewController,
            aiCodeOperationAuthorizer, aiCodeHostService);
    }

    public ModelContextProtocolServer(AIToolSet toolSet, ObjectMapper objectMapper, ViewController viewController) {
        this(Collections.<Object>singletonList(toolSet), objectMapper, ResourceController.getResourceController(),
            viewController);
    }

    ModelContextProtocolServer(Collection<?> toolSets, ObjectMapper objectMapper, ResourceController resourceController,
                               ViewController viewController) {
        this(toolSets, objectMapper, resourceController, viewController, null);
    }

    ModelContextProtocolServer(Collection<?> toolSets, ObjectMapper objectMapper, ResourceController resourceController,
                               ViewController viewController,
                               AiCodeOperationAuthorizer aiCodeOperationAuthorizer) {
        this(toolSets, objectMapper, resourceController, viewController, aiCodeOperationAuthorizer, null);
    }

    ModelContextProtocolServer(Collection<?> toolSets, ObjectMapper objectMapper, ResourceController resourceController,
                               ViewController viewController,
                               AiCodeOperationAuthorizer aiCodeOperationAuthorizer,
                               ModelContextProtocolAiCodeHostService aiCodeHostService) {
        this(toolSets, objectMapper, resourceController, new MCPAuthenticator(
            resourceController,
            viewController,
            MCP_TOKEN_PROPERTY,
            MCP_TOKEN_HEADER), aiCodeOperationAuthorizer, aiCodeHostService);
    }

    ModelContextProtocolServer(AIToolSet toolSet, ObjectMapper objectMapper, ResourceController resourceController,
                               MCPAuthenticator authenticator) {
        this(Collections.<Object>singletonList(toolSet), objectMapper, resourceController, authenticator, null);
    }

    ModelContextProtocolServer(Collection<?> toolSets, ObjectMapper objectMapper, ResourceController resourceController,
                               MCPAuthenticator authenticator) {
        this(toolSets, objectMapper, resourceController, authenticator, null);
    }

    ModelContextProtocolServer(Collection<?> toolSets, ObjectMapper objectMapper, ResourceController resourceController,
                               MCPAuthenticator authenticator,
                               AiCodeOperationAuthorizer aiCodeOperationAuthorizer) {
        this(toolSets, objectMapper, resourceController, authenticator, aiCodeOperationAuthorizer, null);
    }

    ModelContextProtocolServer(Collection<?> toolSets, ObjectMapper objectMapper, ResourceController resourceController,
                               MCPAuthenticator authenticator,
                               AiCodeOperationAuthorizer aiCodeOperationAuthorizer,
                               ModelContextProtocolAiCodeHostService aiCodeHostService) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        ToolAvailabilityLevelSettings toolAvailabilityLevelSettings = new ToolAvailabilityLevelSettings(resourceController);
        FormulaEditingSettings formulaEditingSettings = new FormulaEditingSettings(resourceController);
        this.toolRegistry = new ModelContextProtocolToolRegistry(
            toolSets,
            this.objectMapper,
            toolAvailabilityLevelSettings::getToolAvailability,
            () -> Boolean.valueOf(formulaEditingSettings.isEnabled()));
        this.toolDispatcher = new ModelContextProtocolToolDispatcher(
            toolSets,
            this.objectMapper,
            createToolCallAuthorizer(resourceController, aiCodeOperationAuthorizer),
            aiCodeHostService);
        this.resourceController = Objects.requireNonNull(resourceController, "resourceController");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        this.running = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        if (this.resourceController.getBooleanProperty(MCP_SERVER_ENABLED_PROPERTY))
            start();
    }

    private ModelContextProtocolToolCallAuthorizer createToolCallAuthorizer(
        ResourceController resourceController,
        AiCodeOperationAuthorizer aiCodeOperationAuthorizer) {
        if (aiCodeOperationAuthorizer == null) {
            return null;
        }
        ToolAvailabilityLevelSettings toolAvailabilityLevelSettings = new ToolAvailabilityLevelSettings(resourceController);
        FormulaEditingSettings formulaEditingSettings = new FormulaEditingSettings(resourceController);
        return new ModelContextProtocolToolCallAuthorizer(
            toolAvailabilityLevelSettings::getToolAvailability,
            () -> Boolean.valueOf(formulaEditingSettings.isEnabled()),
            aiCodeOperationAuthorizer,
            new MapTargetToolCallAuthorizer());
    }

    public void start() {
        int port = resourceController.getIntProperty(MCP_SERVER_PORT_PROPERTY, DEFAULT_PORT);
        start(port);
    }

    public void start(int port) {
        if (!isValidPort(port)) {
            LogUtils.severe("MCP server port is invalid: " + port);
            return;
        }
        if (!isPortAvailable(port)) {
            LogUtils.warn("MCP server port is already in use: " + port);
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            @SuppressWarnings("resource")
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            httpServer.createContext("/", new ModelContextProtocolHandler());
            httpServer.setExecutor(null);
            httpServer.start();
            server = httpServer;
            LogUtils.info("MCP server started on port " + port);
        } catch (Exception error) {
            running.set(false);
            LogUtils.severe("Failed to start MCP server: " + error.getMessage());
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
        LogUtils.info("MCP server stopped.");
    }

    @Override
    public void propertyChanged(String propertyName, String newValue, String oldValue) {
        if (MCP_SERVER_ENABLED_PROPERTY.equals(propertyName)) {
            if (Boolean.parseBoolean(newValue)) {
                start();
            } else {
                stop();
            }
        }
    }

    private boolean isValidPort(int port) {
        return port >= PORT_MINIMUM && port <= PORT_MAXIMUM;
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private class ModelContextProtocolHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendStatus(exchange, 405);
                return;
            }
            Object authFailureResponse = authenticator.authenticateRequest(exchange.getRequestHeaders());
            if (authFailureResponse != null) {
                writeJsonResponse(exchange, authFailureResponse, 401);
                return;
            }
            JsonNode requestNode;
            try {
                byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                if (bodyBytes == null || bodyBytes.length == 0) {
                    writeJsonResponse(exchange, buildErrorResponse(null, -32600, "Invalid request"));
                    return;
                }
                requestNode = objectMapper.readTree(bodyBytes);
            } catch (JsonProcessingException error) {
                writeJsonResponse(exchange, buildErrorResponse(null, -32700, "Parse error"));
                return;
            }
            if (requestNode == null || requestNode.isNull() || requestNode.isMissingNode()) {
                writeJsonResponse(exchange, buildErrorResponse(null, -32600, "Invalid request"));
                return;
            }
            if (requestNode.isArray()) {
                writeJsonResponse(exchange, buildErrorResponse(null, -32600, "Batch requests are not supported"));
                return;
            }
            Object responsePayload = handleRequest(requestNode);
            if (responsePayload == null) {
                sendStatus(exchange, 204);
                return;
            }
            writeJsonResponse(exchange, responsePayload);
        }

        private Object handleRequest(JsonNode requestNode) {
            String method = getText(requestNode.get("method"));
            JsonNode idNode = requestNode.get("id");
            Object idValue = idNode == null || idNode.isNull() ? null : objectMapper.convertValue(idNode, Object.class);
            boolean notification = idValue == null;
            if (method == null || method.isEmpty()) {
                return buildErrorResponse(idValue, -32600, "Invalid request");
            }
            if ("initialize".equals(method)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("protocolVersion", MCP_PROTOCOL_VERSION);
                result.put("capabilities", buildCapabilities());
                result.put("serverInfo", buildServerInfo());
                return buildSuccessResponse(idValue, result, notification);
            }
            if ("notifications/initialized".equals(method)) {
                return null;
            }
            if ("tools/list".equals(method)) {
                return buildSuccessResponse(idValue, buildToolListPayload(), notification);
            }
            if ("resources/list".equals(method)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("resources", Collections.singletonList(buildToolMetadataResource()));
                return buildSuccessResponse(idValue, result, notification);
            }
            if ("resources/templates/list".equals(method)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("resourceTemplates", Collections.emptyList());
                return buildSuccessResponse(idValue, result, notification);
            }
            if ("resources/read".equals(method)) {
                return handleResourceRead(requestNode, idValue, notification);
            }
            if ("tools/call".equals(method)) {
                return handleToolCall(requestNode, idValue, notification);
            }
            return buildErrorResponse(idValue, -32601, "Method not found");
        }

        private Object handleToolCall(JsonNode requestNode, Object idValue, boolean notification) {
            JsonNode params = requestNode.get("params");
            String toolName = params == null ? null : getText(params.get("name"));
            if (toolName == null || toolName.isEmpty()) {
                return buildErrorResponse(idValue, -32602, "Missing tool name");
            }
            JsonNode argumentsNode = params == null ? null : params.get("arguments");
            ToolExecutionResult executionResult;
            try {
            	LogUtils.info("MCP call " + toolName + " " + argumentsNode);
                executionResult = toolDispatcher.dispatch(toolName, argumentsNode);
            } catch (IllegalArgumentException error) {
                return buildErrorResponse(idValue, -32602, error.getMessage());
            } catch (Exception error) {
                return buildErrorResponse(idValue, -32603, error.getMessage());
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", Collections.singletonList(buildTextContent(executionResult.resultText())));
            if (executionResult.isError()) {
                result.put("isError", true);
            }
            return buildSuccessResponse(idValue, result, notification);
        }

        private Object handleResourceRead(JsonNode requestNode, Object idValue, boolean notification) {
            JsonNode params = requestNode.get("params");
            String resourceIdentifier = params == null ? null : getText(params.get("uri"));
            if (!TOOLS_RESOURCE_URI.equals(resourceIdentifier)) {
                return buildErrorResponse(idValue, -32602, "Unknown resource uri");
            }
            String toolListJson;
            try {
                toolListJson = objectMapper.writeValueAsString(buildToolListPayload());
            } catch (JsonProcessingException error) {
                return buildErrorResponse(idValue, -32603, "Failed to serialize resource");
            }
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("uri", TOOLS_RESOURCE_URI);
            content.put("mimeType", "application/json");
            content.put("text", toolListJson);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contents", Collections.singletonList(content));
            return buildSuccessResponse(idValue, result, notification);
        }

        private Map<String, Object> buildCapabilities() {
            Map<String, Object> tools = new LinkedHashMap<>();
            tools.put("listChanged", false);
            Map<String, Object> resources = new LinkedHashMap<>();
            resources.put("listChanged", false);
            Map<String, Object> capabilities = new LinkedHashMap<>();
            capabilities.put("tools", tools);
            capabilities.put("resources", resources);
            return capabilities;
        }

        private Map<String, Object> buildToolListPayload() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tools", toolRegistry.listTools());
            return result;
        }

        private Map<String, Object> buildToolMetadataResource() {
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("uri", TOOLS_RESOURCE_URI);
            resource.put("name", "Tool metadata");
            resource.put("description", "JSON tool list for MCP clients that only support resources.");
            resource.put("mimeType", "application/json");
            return resource;
        }

        private Map<String, Object> buildServerInfo() {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", SERVER_NAME);
            info.put("version", FreeplaneVersion.getVersion().toString());
            return info;
        }

        private Map<String, Object> buildTextContent(String text) {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("type", "text");
            content.put("text", text);
            return content;
        }

        private Object buildSuccessResponse(Object idValue, Object result, boolean notification) {
            if (notification) {
                return null;
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", idValue);
            response.put("result", result);
            return response;
        }

        private Object buildErrorResponse(Object idValue, int code, String message) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", code);
            error.put("message", message);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", idValue);
            response.put("error", error);
            return response;
        }

        private String getText(JsonNode node) {
            return node == null || node.isNull() ? null : node.asText();
        }

        private void writeJsonResponse(HttpExchange exchange, Object responseBody) throws IOException {
            writeJsonResponse(exchange, responseBody, 200);
        }

        private void writeJsonResponse(HttpExchange exchange, Object responseBody, int statusCode) throws IOException {
            LogUtils.info("MCP response " + objectMapper.writeValueAsString(responseBody));
            byte[] responseBytes = objectMapper.writeValueAsBytes(responseBody);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
            exchange.close();
        }

        private void sendStatus(HttpExchange exchange, int status) throws IOException {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        }
    }
}
