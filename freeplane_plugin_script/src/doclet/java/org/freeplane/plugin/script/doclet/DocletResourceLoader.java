package org.freeplane.plugin.script.doclet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class DocletResourceLoader {
    private DocletResourceLoader() {
    }

    public static InputStream openRequiredResource(String resourcePath) {
        InputStream inputStream = DocletResourceLoader.class.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IllegalStateException("Missing required doclet resource: " + resourcePath);
        }
        return inputStream;
    }

    public static String readUtf8Resource(String resourcePath) {
        try (InputStream inputStream = openRequiredResource(resourcePath)) {
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return normalizeTrailingLineBreaks(text.replace("\r\n", "\n").replace('\r', '\n'));
        }
        catch (IOException error) {
            throw new IllegalStateException("Failed to read doclet resource: " + resourcePath, error);
        }
    }

    private static String normalizeTrailingLineBreaks(String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '\n') {
            end -= 1;
        }
        return text.substring(0, end);
    }
}
