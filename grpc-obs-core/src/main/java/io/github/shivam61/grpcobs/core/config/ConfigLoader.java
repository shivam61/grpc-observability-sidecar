package io.github.shivam61.grpcobs.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;

public class ConfigLoader {
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public static SidecarConfig load(String path) throws IOException {
        return mapper.readValue(new File(path), SidecarConfig.class);
    }
}
