package org.freeplane.plugin.ai.chat.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;

public class ChatTranscriptStore {
    private static final String TRANSCRIPT_DIRECTORY = "ai-chats";
    private static final String TRANSCRIPT_EXTENSION = ".json.gz";

    private final ObjectMapper objectMapper;
    private final Path rootDirectory;
    private final DateTimeFormatter dateFormatter;

    public ChatTranscriptStore() {
        this(new ObjectMapper(), resolveDefaultRoot());
    }

    ChatTranscriptStore(ObjectMapper objectMapper, Path rootDirectory) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory");
        this.dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
    }

    public ChatTranscriptId save(ChatTranscriptRecord record, ChatTranscriptId existingId) {
        if (record == null) {
            return existingId;
        }
        long timestamp = System.currentTimeMillis();
        record.setTimestamp(timestamp);
        ChatTranscriptId targetId = buildTranscriptId(existingId, timestamp);
        Path targetPath = resolvePath(targetId);
        ensureParentDirectory(targetPath);
        writeRecord(targetPath, record);
        if (existingId != null) {
            Path oldPath = resolvePath(existingId);
            if (!oldPath.equals(targetPath)) {
                deletePath(oldPath);
            }
        }
        return targetId;
    }

    public List<ChatTranscriptSummary> list() {
        List<ChatTranscriptSummary> summaries = new ArrayList<>();
        if (!Files.exists(rootDirectory)) {
            return summaries;
        }
        try {
            Files.walk(rootDirectory, 2, FileVisitOption.FOLLOW_LINKS)
                .filter(Files::isRegularFile)
                .filter(this::isTranscriptFile)
                .forEach(path -> summaries.add(readSummary(path)));
        } catch (IOException error) {
            LogUtils.severe(error);
        }
        summaries.sort(Comparator.comparingLong(ChatTranscriptSummary::getTimestamp).reversed());
        return summaries;
    }

    public ChatTranscriptRecord load(ChatTranscriptId id) {
        if (id == null || id.getFileName() == null) {
            return null;
        }
        Path path = resolvePath(id);
        if (!Files.exists(path)) {
            return null;
        }
        try (InputStream fileInputStream = Files.newInputStream(path);
             GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream)) {
            return objectMapper.readValue(gzipInputStream, ChatTranscriptRecord.class);
        } catch (IOException error) {
            LogUtils.severe(error);
            return null;
        }
    }

    public boolean delete(ChatTranscriptId id) {
        if (id == null || id.getFileName() == null) {
            return false;
        }
        Path path = resolvePath(id);
        return deletePath(path);
    }

    public ChatTranscriptId rename(ChatTranscriptId id, String displayName) {
        if (id == null) {
            return null;
        }
        ChatTranscriptRecord record = load(id);
        if (record == null) {
            return id;
        }
        record.setDisplayName(displayName);
        return save(record, id);
    }

    private void writeRecord(Path path, ChatTranscriptRecord record) {
        try (OutputStream fileOutputStream = Files.newOutputStream(path);
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(fileOutputStream)) {
            objectMapper.writeValue(gzipOutputStream, record);
        } catch (IOException error) {
            LogUtils.severe(error);
        }
    }

    private ChatTranscriptSummary readSummary(Path path) {
        ChatTranscriptSummary summary = new ChatTranscriptSummary();
        summary.setId(new ChatTranscriptId(rootDirectory.relativize(path).toString()));
        summary.setDisplayName(path.getFileName().toString());
        summary.setStatus(ChatTranscriptStatus.ERROR);
        summary.setTimestamp(readLastModifiedTimestamp(path));
        try (InputStream fileInputStream = Files.newInputStream(path);
             GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream)) {
            ChatTranscriptRecord record = objectMapper.readValue(gzipInputStream, ChatTranscriptRecord.class);
            summary.setStatus(ChatTranscriptStatus.TRANSCRIPT);
            summary.setTimestamp(record.getTimestamp());
            summary.setDisplayName(record.getDisplayName());
            summary.setMapRootShortTextCounts(record.getMapRootShortTextCounts());
        } catch (IOException error) {
            summary.setErrorMessage(error.getClass().getSimpleName());
        }
        return summary;
    }

    private long readLastModifiedTimestamp(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException error) {
            return 0L;
        }
    }

    private boolean isTranscriptFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(TRANSCRIPT_EXTENSION);
    }

    private ChatTranscriptId buildTranscriptId(ChatTranscriptId existingId, long timestamp) {
        String fileName = existingId == null ? null : existingId.getLeafFileName();
        if (fileName == null || fileName.isEmpty()) {
            fileName = UUID.randomUUID().toString() + TRANSCRIPT_EXTENSION;
        }
        String dateFolder = dateFormatter.format(LocalDate.ofInstant(Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()));
        Path relativePath = Paths.get(dateFolder).resolve(fileName);
        return new ChatTranscriptId(relativePath.toString());
    }

    private Path resolvePath(ChatTranscriptId id) {
        return rootDirectory.resolve(id.getFileName());
    }

    private void ensureParentDirectory(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException error) {
            LogUtils.severe(error);
        }
    }

    private boolean deletePath(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException error) {
            LogUtils.severe(error);
            return false;
        }
    }

    private static Path resolveDefaultRoot() {
        String userDirectory = ResourceController.getResourceController().getFreeplaneUserDirectory();
        return Paths.get(userDirectory).resolve(TRANSCRIPT_DIRECTORY);
    }
}
