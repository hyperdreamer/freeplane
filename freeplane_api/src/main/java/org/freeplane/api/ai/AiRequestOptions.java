package org.freeplane.api.ai;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable public options for script-facing AI requests.
 *
 * <p>The builder requires a positive {@link #getTimeout() timeout}. All other
 * options are request overrides. Leaving an option unset lets the request
 * execution context choose its default; for saved prompts, unset fields inherit
 * the saved prompt configuration where such a value exists.</p>
 *
 * <p>{@link AiRequestService#askAi(String, AiRequestOptions, AiRequestCallback) askAi}
 * requires {@link Builder#mode(AiRequestMode)}. For
 * {@link AiRequestService#runAiPrompt(String, AiRequestOptions, AiRequestCallback) runAiPrompt},
 * a missing mode uses the saved prompt's visibility setting.</p>
 *
 * @since 1.13.3
 */
public class AiRequestOptions {
    private final Duration timeout;
    private final AiRequestMode mode;
    private final AiModelConfiguration modelConfiguration;
    private final AiToolAvailability toolAvailability;
    private final AiSelectionOverride selectionOverride;
    private final String systemMessage;
    private final boolean isSystemMessageExact;
    private final String profileName;
    private final String profileMessage;

    private AiRequestOptions(Builder builder) {
        this.timeout = requirePositiveTimeout(builder.timeout);
        this.mode = builder.mode;
        this.modelConfiguration = builder.modelConfiguration;
        this.toolAvailability = builder.toolAvailability;
        this.selectionOverride = builder.selectionOverride;
        this.systemMessage = normalizeNullable(builder.systemMessage);
        this.isSystemMessageExact = builder.isSystemMessageExact && this.systemMessage != null;
        this.profileName = normalizeNullable(builder.profileName);
        this.profileMessage = normalizeNullable(builder.profileMessage);
    }

    /**
     * Creates a new request-options builder.
     *
     * @return a builder whose {@link Builder#timeout(Duration) timeout} must be
     *         set before {@link Builder#build()} is called
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the request timeout.
     *
     * <p>The timeout is required and must be positive. If it expires before the
     * request completes, the callback receives {@link AiRequestStatus#TIMED_OUT}.</p>
     *
     * @return the positive timeout
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Returns the requested execution mode, or {@code null} when unset.
     *
     * <p>{@code askAi} requires a non-null mode. {@code runAiPrompt} accepts
     * {@code null} and derives the mode from the saved prompt.</p>
     *
     * @return the requested mode, or {@code null}
     */
    public AiRequestMode getMode() {
        return mode;
    }

    /**
     * Returns model overrides, or {@code null} when all model fields are inherited.
     *
     * @return model-configuration overrides, or {@code null}
     */
    public AiModelConfiguration getModelConfiguration() {
        return modelConfiguration;
    }

    /**
     * Returns the requested tool-availability level, or {@code null} when unset.
     *
     * <p>For direct {@code askAi} requests, unset tool availability is treated as
     * {@link AiToolAvailability#CURRENT}. Saved prompt requests inherit the saved
     * prompt value when this option is unset.</p>
     *
     * @return requested tool availability, or {@code null}
     */
    public AiToolAvailability getToolAvailability() {
        return toolAvailability;
    }

    /**
     * Returns a prompt-time selection override, or {@code null} to use the current
     * Freeplane selection.
     *
     * @return selection override, or {@code null}
     */
    public AiSelectionOverride getSelectionOverride() {
        return selectionOverride;
    }

    /**
     * Returns the base system message override, or {@code null} to use the
     * configured Freeplane AI system message.
     *
     * <p>The builder trims this value. A blank but non-null value therefore becomes
     * {@code ""}; this deliberately suppresses the configured base system message.
     * Unless {@link #isSystemMessageExact()} is true, Freeplane still appends
     * generated system guidance for the active tool level and other applicable chat
     * context, such as map-selection handling, profile control, response format, or
     * code-host use.</p>
     *
     * @return the trimmed base system message override, or {@code null}
     */
    public String getSystemMessage() {
        return systemMessage;
    }

    /**
     * Returns whether {@link #getSystemMessage()} is the complete system message.
     *
     * <p>When this flag is true, Freeplane sends the trimmed system-message value
     * as the full system instruction and does not append generated guidance. Use
     * {@link Builder#exactSystemMessage(String)} only when the caller supplies all
     * required instruction text explicitly. A blank exact system message is allowed
     * and produces an empty system instruction. Passing {@code null} to
     * {@code exactSystemMessage} clears both the override and this flag.</p>
     *
     * @return true when the system message is exact
     */
    public boolean isSystemMessageExact() {
        return isSystemMessageExact;
    }

    /**
     * Returns the requested assistant profile name, or {@code null} when no
     * profile request was configured.
     *
     * @return trimmed profile name, possibly empty, or {@code null}
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Returns the inline assistant profile instruction, or {@code null} when
     * {@link #getProfileName()} should be resolved as a saved profile name.
     *
     * @return trimmed inline profile instruction, possibly empty, or {@code null}
     */
    public String getProfileMessage() {
        return profileMessage;
    }

    private static Duration requirePositiveTimeout(Duration timeout) {
        Duration requiredTimeout = Objects.requireNonNull(timeout, "timeout");
        if (requiredTimeout.isZero() || requiredTimeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return requiredTimeout;
    }

    private static String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Mutable builder for {@link AiRequestOptions}.
     *
     * <p>The builder records requested overrides only. It does not validate saved
     * prompt names, profile names, provider names, or model availability; those are
     * resolved when the request starts.</p>
     */
    public static class Builder {
        private Duration timeout;
        private AiRequestMode mode;
        private AiModelConfiguration modelConfiguration;
        private AiToolAvailability toolAvailability;
        private AiSelectionOverride selectionOverride;
        private String systemMessage;
        private boolean isSystemMessageExact;
        private String profileName;
        private String profileMessage;

        /**
         * Sets the maximum request duration.
         *
         * @param timeout positive timeout; must not be {@code null}
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the request execution mode.
         *
         * <p>This is mandatory for {@code askAi}. If omitted for {@code runAiPrompt},
         * the saved prompt decides whether the request is shown in chat or hidden.</p>
         *
         * @param mode requested execution mode, or {@code null} to leave it unset
         * @return this builder
         */
        public Builder mode(AiRequestMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Sets model-configuration overrides.
         *
         * <p>Unset fields inside the configuration inherit independently. For a saved
         * prompt, inherited fields come from the saved prompt; otherwise they come
         * from the current model defaults. Use {@link AiModelSelection#defaultModel()}
         * to explicitly request the default model instead of a saved prompt's model.</p>
         *
         * @param modelConfiguration model overrides, or {@code null} to inherit all
         * @return this builder
         */
        public Builder modelConfiguration(AiModelConfiguration modelConfiguration) {
            this.modelConfiguration = modelConfiguration;
            return this;
        }

        /**
         * Sets the AI tool availability requested for this call.
         *
         * @param toolAvailability requested availability, or {@code null} to inherit
         * @return this builder
         */
        public Builder toolAvailability(AiToolAvailability toolAvailability) {
            this.toolAvailability = toolAvailability;
            return this;
        }

        /**
         * Sets the Freeplane map selection injected into the first prompt message.
         *
         * <p>This changes only prompt composition. It does not change the user's UI
         * selection and does not affect later tool results.</p>
         *
         * @param selectionOverride selection override, or {@code null} to use the current selection
         * @return this builder
         */
        public Builder selectionOverride(AiSelectionOverride selectionOverride) {
            this.selectionOverride = selectionOverride;
            return this;
        }

        /**
         * Sets a base system-message override while preserving Freeplane guidance.
         *
         * <p>The value is trimmed when {@link #build()} is called. Passing
         * {@code null} clears the override and makes the request use the configured
         * Freeplane AI system message. Passing a blank string stores {@code ""},
         * which suppresses the configured base message but still lets Freeplane append
         * generated guidance for the active tool level and other applicable chat
         * context, such as map-selection handling, profile control, response format, or
         * code-host use.</p>
         *
         * <p>This method clears the exact-system-message flag set by
         * {@link #exactSystemMessage(String)}; the last system-message builder call
         * wins.</p>
         *
         * @param systemMessage base system message override, blank to suppress the configured base message,
         *        or {@code null} to inherit it
         * @return this builder
         */
        public Builder systemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            this.isSystemMessageExact = false;
            return this;
        }

        /**
         * Sets the complete system message exactly as supplied by the caller.
         *
         * <p>The value is trimmed when {@link #build()} is called. When non-null,
         * Freeplane treats the trimmed value, including {@code ""}, as the complete
         * system instruction and does not append generated guidance. The caller is
         * then responsible for including any required instructions that Freeplane would
         * normally add, such as tool-calling, map-selection, profile-control,
         * response-format, or code-host instructions.</p>
         *
         * <p>Passing {@code null} clears the system-message override and clears the
         * exact flag. {@link #systemMessage(String)} also clears the exact flag; the
         * last system-message builder call wins.</p>
         *
         * @param systemMessage complete system message, blank for an empty exact system instruction,
         *        or {@code null} to inherit the configured Freeplane system message
         * @return this builder
         */
        public Builder exactSystemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            this.isSystemMessageExact = systemMessage != null;
            return this;
        }

        /**
         * Requests a saved assistant profile by name.
         *
         * <p>The name is trimmed when {@link #build()} is called. The profile is
         * looked up when the request starts; missing, blank, or ambiguous names
         * complete the request with a configuration error.</p>
         *
         * @param name saved profile name
         * @return this builder
         */
        public Builder profile(String name) {
            this.profileName = name == null ? "" : name;
            this.profileMessage = null;
            return this;
        }

        /**
         * Requests an inline assistant profile instruction.
         *
         * <p>This overload does not look up a saved profile. The name and message are
         * trimmed when {@link #build()} is called. If the message is blank and the
         * name is non-blank, Freeplane sends a profile marker for that name. If both
         * are blank, no profile instruction is sent.</p>
         *
         * @param name profile name to display in the instruction; {@code null} is treated as blank
         * @param message inline profile instruction; must not be {@code null}
         * @return this builder
         */
        public Builder profile(String name, String message) {
            this.profileName = name == null ? "" : name;
            this.profileMessage = Objects.requireNonNull(message, "message");
            return this;
        }

        /**
         * Builds immutable request options.
         *
         * @return immutable options
         * @throws NullPointerException if timeout is unset
         * @throws IllegalArgumentException if timeout is zero or negative
         */
        public AiRequestOptions build() {
            return new AiRequestOptions(this);
        }
    }
}
