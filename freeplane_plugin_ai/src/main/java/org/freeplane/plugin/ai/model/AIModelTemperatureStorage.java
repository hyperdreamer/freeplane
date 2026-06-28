package org.freeplane.plugin.ai.model;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.freeplane.api.ai.AiTemperature;

public class AIModelTemperatureStorage {
    public static final String MODEL_DEFAULT_VALUE = "model_default";
    private static final List<String> PRESET_VALUES = Arrays.asList("0", "0.2", "0.5", "0.7", "1.0");

    private AIModelTemperatureStorage() {
    }

    public static AiTemperature fromStoredValue(Object value) {
        String normalized = normalizeStoredValue(value);
        if (normalized == null) {
            return null;
        }
        if (MODEL_DEFAULT_VALUE.equalsIgnoreCase(normalized)) {
            return AiTemperature.modelDefault();
        }
        return parseNumeric(normalized);
    }

    public static AiTemperature fromGlobalPreferenceValue(String value) {
        AiTemperature temperature = fromStoredValue(value);
        return temperature == null ? AiTemperature.modelDefault() : temperature;
    }

    public static Object toStoredValue(AiTemperature temperature) {
        if (temperature == null) {
            return null;
        }
        if (temperature.isModelDefault()) {
            return MODEL_DEFAULT_VALUE;
        }
        return temperature.getValue();
    }

    public static String toPreferenceValue(AiTemperature temperature) {
        if (temperature == null || temperature.isModelDefault()) {
            return MODEL_DEFAULT_VALUE;
        }
        return formatNumber(temperature.getValue().doubleValue());
    }

    public static boolean isFiniteNumericValue(String value) {
        return parseNumeric(value) != null;
    }

    public static String formatNumber(double value) {
        for (String presetValue : PRESET_VALUES) {
            double preset = Double.valueOf(presetValue).doubleValue();
            if (Double.compare(preset, value) == 0) {
                return presetValue;
            }
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    public static boolean sameNumericValue(String left, String right) {
        AiTemperature leftTemperature = parseNumeric(left);
        AiTemperature rightTemperature = parseNumeric(right);
        return leftTemperature != null
            && rightTemperature != null
            && Double.compare(leftTemperature.getValue().doubleValue(), rightTemperature.getValue().doubleValue()) == 0;
    }

    private static String normalizeStoredValue(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static AiTemperature parseNumeric(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            double parsed = Double.valueOf(normalized).doubleValue();
            return Double.isNaN(parsed) || Double.isInfinite(parsed) ? null : AiTemperature.of(parsed);
        }
        catch (NumberFormatException ignored) {
            return null;
        }
    }
}
