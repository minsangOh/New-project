package com.example.pstarchive.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;

public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonUtils() {
    }

    public static void write(Path path, Object value) throws IOException {
        MAPPER.writeValue(path.toFile(), value);
    }

    public static <T> T read(Path path, Class<T> type) throws IOException {
        return MAPPER.readValue(path.toFile(), type);
    }
}
