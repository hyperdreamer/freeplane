package org.freeplane.features.ai.code;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Objects;

public class CodeStateToken {
    private String codeFingerprint;
    private String inputFingerprint;
    private String stateFingerprint;

    public CodeStateToken() {
    }

    public CodeStateToken(String codeFingerprint, String inputFingerprint, String stateFingerprint) {
        this.codeFingerprint = codeFingerprint;
        this.inputFingerprint = inputFingerprint;
        this.stateFingerprint = stateFingerprint;
    }

    public String getCodeFingerprint() {
        return codeFingerprint;
    }

    public void setCodeFingerprint(String codeFingerprint) {
        this.codeFingerprint = codeFingerprint;
    }

    public String getInputFingerprint() {
        return inputFingerprint;
    }

    public void setInputFingerprint(String inputFingerprint) {
        this.inputFingerprint = inputFingerprint;
    }

    public String getStateFingerprint() {
        return stateFingerprint;
    }

    public void setStateFingerprint(String stateFingerprint) {
        this.stateFingerprint = stateFingerprint;
    }

    public static CodeStateToken fromContent(CodeStateContent content) {
        String sourceText = content == null ? null : content.getSourceText();
        String inputText = content == null ? null : content.getInputText();
        String codeFingerprint = fingerprint(sourceText);
        String inputFingerprint = fingerprint(inputText);
        return new CodeStateToken(codeFingerprint, inputFingerprint, fingerprint(codeFingerprint + "\n" + inputFingerprint));
    }

    public static String normalizeFingerprint(String fingerprint) {
        return fingerprint == null || fingerprint.trim().isEmpty() ? null : fingerprint.trim();
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
            && Objects.equals(inputFingerprint, that.inputFingerprint)
            && Objects.equals(stateFingerprint, that.stateFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codeFingerprint, inputFingerprint, stateFingerprint);
    }

    @Override
    public String toString() {
        return "CodeStateToken{"
            + "codeFingerprint='" + codeFingerprint + '\''
            + ", inputFingerprint='" + inputFingerprint + '\''
            + ", stateFingerprint='" + stateFingerprint + '\''
            + '}';
    }
}
