package org.freeplane.plugin.ai.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public final class AIModelCatalogCacheKey {
    private final String providerName;
    private final String modelsAddress;
    private final String metadataAddress;
    private final String authenticationFingerprint;

    public AIModelCatalogCacheKey(String providerName,
                                  String modelsAddress,
                                  String metadataAddress,
                                  String authentication) {
        this.providerName = normalize(providerName);
        this.modelsAddress = normalize(modelsAddress);
        this.metadataAddress = normalize(metadataAddress);
        this.authenticationFingerprint = fingerprint(authentication);
    }

    public String getProviderName() {
        return providerName;
    }

    public String getModelsAddress() {
        return modelsAddress;
    }

    public String getMetadataAddress() {
        return metadataAddress;
    }

    public String getAuthenticationFingerprint() {
        return authenticationFingerprint;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AIModelCatalogCacheKey)) {
            return false;
        }
        AIModelCatalogCacheKey other = (AIModelCatalogCacheKey) object;
        return providerName.equals(other.providerName)
            && modelsAddress.equals(other.modelsAddress)
            && metadataAddress.equals(other.metadataAddress)
            && authenticationFingerprint.equals(other.authenticationFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerName, modelsAddress, metadataAddress, authenticationFingerprint);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String fingerprint(String authentication) {
        String normalizedAuthentication = normalize(authentication);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedAuthentication.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
