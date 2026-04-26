package io.github.shivam61.grpcobs.core.config;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;
import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

    @Test
    void loadsConfigCorrectly() throws Exception {
        File tempFile = File.createTempFile("sidecar-config", ".yaml");
        try (FileWriter fw = new FileWriter(tempFile)) {
            fw.write("""
                server:
                  listenPort: 8080
                upstream:
                  host: localhost
                  port: 9090
                metrics:
                  port: 9091
                  path: /metrics
                  histogramBuckets:
                    - 0.01
                    - 0.05
                  enablePayloadSizeMetrics: true
                cardinality:
                  maxMethods: 50
                  unknownMethodLabel: __other__
                  methodAllowlist:
                    - TestMethod
                sampling:
                  enabled: false
                  successSampleRate: 1.0
                  errorSampleRate: 1.0
                overhead:
                  enableSelfMetrics: true
                """);
        }

        SidecarConfig config = ConfigLoader.load(tempFile.getAbsolutePath());
        
        assertThat(config.server().listenPort()).isEqualTo(8080);
        assertThat(config.upstream().host()).isEqualTo("localhost");
        assertThat(config.cardinality().maxMethods()).isEqualTo(50);
        assertThat(config.cardinality().methodAllowlist()).contains("TestMethod");
    }
}
