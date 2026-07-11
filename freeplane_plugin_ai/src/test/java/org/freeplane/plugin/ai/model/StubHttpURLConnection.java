package org.freeplane.plugin.ai.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

class StubHttpURLConnection extends HttpURLConnection {
    private final int responseCode;
    private final byte[] responseBody;
    private final Map<String, String> requestHeaders = new LinkedHashMap<>();
    private final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();

    StubHttpURLConnection(URL url, int responseCode, String responseBody) {
        super(url);
        this.responseCode = responseCode;
        this.responseBody = responseBody.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void disconnect() {
    }

    @Override
    public boolean usingProxy() {
        return false;
    }

    @Override
    public void connect() {
    }

    @Override
    public int getResponseCode() {
        return responseCode;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (responseCode >= 400) {
            throw new IOException("HTTP " + responseCode);
        }
        return new ByteArrayInputStream(responseBody);
    }

    @Override
    public OutputStream getOutputStream() {
        return requestBody;
    }

    @Override
    public void setRequestProperty(String key, String value) {
        requestHeaders.put(key, value);
    }

    String getRequestHeader(String key) {
        return requestHeaders.get(key);
    }

    String getRequestBody() {
        return new String(requestBody.toByteArray(), StandardCharsets.UTF_8);
    }
}
