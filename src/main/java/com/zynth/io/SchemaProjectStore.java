package com.zynth.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zynth.model.DatabaseSchema;
import java.io.IOException;
import java.nio.file.Path;

public class SchemaProjectStore {
    private final ObjectMapper mapper;

    public SchemaProjectStore() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void save(Path path, DatabaseSchema schema) throws IOException {
        mapper.writeValue(path.toFile(), schema);
    }

    public DatabaseSchema load(Path path) throws IOException {
        return mapper.readValue(path.toFile(), DatabaseSchema.class);
    }
}
