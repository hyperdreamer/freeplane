package org.freeplane.features.ai.code;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Objects;

public class CodeStateToken {
    private String codeFingerprint;
    private String argumentsFingerprint;

    public CodeStateToken() {
    }

    public CodeStateToken(String codeFingerprint, String argumentsFingerprint) {
        this.codeFingerprint = codeFingerprint;
        this.argumentsFingerprint = argumentsFingerprint;
    }

    public String getCodeFingerprint() {
        return codeFingerprint;
    }

    public void setCodeFingerprint(String codeFingerprint) {
        this.codeFingerprint = codeFingerprint;
    }

    public String getArgumentsFingerprint() {
        return argumentsFingerprint;
    }

    public void setArgumentsFingerprint(String argumentsFingerprint) {
        this.argumentsFingerprint = argumentsFingerprint;
    }

    public static CodeStateToken fromContent(CodeStateContent content) {
        String sourceText = content == null ? null : content.getSourceText();
        String argumentsJsonText = content == null ? null : content.getArgumentsJsonText();
        String codeFingerprint = fingerprint(sourceText);
        String argumentsFingerprint = fingerprint(argumentsJsonText);
        return new CodeStateToken(codeFingerprint, argumentsFingerprint);
    }

    public static String normalizeFingerprint(String fingerprint) {
        return fingerprint == null || fingerprint.trim().isEmpty() ? null : fingerprint.trim();
    }

    public boolean matches(CodeStateToken other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(normalizeFingerprint(codeFingerprint), normalizeFingerprint(other.codeFingerprint))
            && Objects.equals(normalizeFingerprint(argumentsFingerprint), normalizeFingerprint(other.argumentsFingerprint));
    }

    public static String fingerprint(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(encoded(text));
            Formatter formatter = new Formatter();
            try {
                for (byte value : hash) {
                    formatter.format("%02x", value);
                }
                return formatter.toString();
            } finally {
                formatter.close();
            }
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available.", error);
        }
    }

    private static byte[] encoded(String text) {
        if (text == null) {
            return new byte[] { 0 };
        }
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = new byte[raw.length + 1];
        encoded[0] = 1;
        System.arraycopy(raw, 0, encoded, 1, raw.length);
        return encoded;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CodeStateToken)) {
            return false;
        }
        CodeStateToken that = (CodeStateToken) other;
        return Objects.equals(codeFingerprint, that.codeFingerprint)
            && Objects.equals(argumentsFingerprint, that.argumentsFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codeFingerprint, argumentsFingerprint);
    }

    @Override
    public String toString() {
        return "CodeStateToken{" 
            + "codeFingerprint='" + codeFingerprint + '\''
            + ", argumentsFingerprint='" + argumentsFingerprint + '\''
            + '}';
    }
}
