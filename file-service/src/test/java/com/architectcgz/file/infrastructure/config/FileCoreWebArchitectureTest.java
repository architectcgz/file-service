package com.architectcgz.file.infrastructure.config;

import com.architectcgz.file.FileServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class FileCoreWebArchitectureTest {

    @Test
    void fileServiceApplicationShouldScanCoreWebPackage() {
        SpringBootApplication springBootApplication = FileServiceApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(springBootApplication).isNotNull();
        assertThat(springBootApplication.scanBasePackages())
                .contains("com.architectcgz.file", "com.platform.fileservice.core.web")
                .doesNotContain("com.platform.file");
    }
}
