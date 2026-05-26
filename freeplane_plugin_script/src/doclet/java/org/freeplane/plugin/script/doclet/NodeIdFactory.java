package org.freeplane.plugin.script.doclet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class NodeIdFactory {
    private NodeIdFactory() {
    }

    public static String createId(String logicalKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(logicalKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("ID_");
            for (int index = 0; index < 12 && index < hash.length; index += 1) {
                int value = hash[index] & 0xFF;
                if (value < 0x10) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(value));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-1 is not available.", error);
        }
    }
}
