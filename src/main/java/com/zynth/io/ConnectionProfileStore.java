package com.zynth.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ConnectionProfileStore {
    public record SavedProfile(String jdbcUrl, String username, String password) {
    }

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path path;

    public ConnectionProfileStore(Path path) {
        this.path = path;
    }

    public Optional<SavedProfile> load() {
        try {
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(path.toFile(), SavedProfile.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void save(SavedProfile profile) throws Exception {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        mapper.writeValue(path.toFile(), profile);
    }
}
