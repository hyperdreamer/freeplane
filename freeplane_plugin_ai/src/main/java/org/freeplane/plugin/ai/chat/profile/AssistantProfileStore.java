package org.freeplane.plugin.ai.chat.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;

class AssistantProfileStore {
    static final String PROFILES_FILE_NAME = "ai-assistant-profiles.json";
    private static final String PROFILES_RESOURCE =
        "/org/freeplane/plugin/ai/assistant-profiles.json";

    private final ObjectMapper objectMapper;
    private final Path profilesPath;

    public AssistantProfileStore() {
        this(new ObjectMapper(), resolveDefaultPath());
    }

    AssistantProfileStore(ObjectMapper objectMapper, Path profilesPath) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.profilesPath = Objects.requireNonNull(profilesPath, "profilesPath");
    }

    public List<AssistantProfile> loadProfiles() {
        if (!Files.exists(profilesPath)) {
            if (copyDefaultProfiles()) {
                return loadProfiles();
            }
            return new ArrayList<>();
        }
        try (InputStream inputStream = Files.newInputStream(profilesPath)) {
            AssistantProfile[] loaded = objectMapper.readValue(inputStream, AssistantProfile[].class);
            if (loaded == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(Arrays.asList(loaded));
        } catch (IOException error) {
            LogUtils.severe(error);
            return new ArrayList<>();
        }
    }

    public void saveProfiles(List<AssistantProfile> profiles) {
        ensureParentDirectory();
        try (OutputStream outputStream = Files.newOutputStream(profilesPath)) {
            objectMapper.writeValue(outputStream, profiles);
        } catch (IOException error) {
            LogUtils.severe(error);
        }
    }

    private void ensureParentDirectory() {
        Path parent = profilesPath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException error) {
            LogUtils.severe(error);
        }
    }

    private boolean copyDefaultProfiles() {
        ensureParentDirectory();
        try (InputStream inputStream = AssistantProfileStore.class.getResourceAsStream(PROFILES_RESOURCE)) {
            if (inputStream == null) {
                return false;
            }
            try (OutputStream outputStream = Files.newOutputStream(profilesPath)) {
                inputStream.transferTo(outputStream);
                return true;
            }
        } catch (IOException error) {
            LogUtils.severe(error);
            return false;
        }
    }

    private static Path resolveDefaultPath() {
        String userDirectory = ResourceController.getResourceController().getFreeplaneUserDirectory();
        return Paths.get(userDirectory).resolve(PROFILES_FILE_NAME);
    }

}
