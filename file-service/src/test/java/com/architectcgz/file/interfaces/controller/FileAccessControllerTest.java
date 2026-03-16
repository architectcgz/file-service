package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.application.service.FileAccessService;
import com.architectcgz.file.common.context.UserContext;
import com.architectcgz.file.config.WebMvcTestConfig;
import com.architectcgz.file.infrastructure.config.AccessProperties;
import com.architectcgz.file.infrastructure.config.CacheProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FileAccessController.class, excludeAutoConfiguration = {
        MybatisAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(WebMvcTestConfig.class)
class FileAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileAccessService fileAccessService;

    @MockBean
    private AccessProperties accessProperties;

    @MockBean
    private CacheProperties cacheProperties;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRedirectPublicFileContent() throws Exception {
        CacheProperties.UrlCache urlCache = new CacheProperties.UrlCache();
        urlCache.setTtl(3600);
        when(cacheProperties.getUrl()).thenReturn(urlCache);
        when(fileAccessService.getFileUrl("test-app", "file-001", "user-001"))
                .thenReturn(FileUrlResponse.builder()
                        .url("https://cdn.example.com/public/file-001.png")
                        .permanent(true)
                        .build());
        UserContext.setUserId("user-001");

        mockMvc.perform(get("/api/v1/files/{fileId}/content", "file-001")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://cdn.example.com/public/file-001.png"))
                .andExpect(header().string("Cache-Control", "public, max-age=3600"));

        verify(fileAccessService).getFileUrl("test-app", "file-001", "user-001");
    }

    @Test
    void shouldRedirectPrivateFileContentWithoutUserContext() throws Exception {
        when(accessProperties.getPrivateUrlExpireSeconds()).thenReturn(900);
        when(fileAccessService.getFileUrl("test-app", "file-002", null))
                .thenReturn(FileUrlResponse.builder()
                        .url("https://s3.example.com/private/file-002?signature=abc")
                        .permanent(false)
                        .build());

        mockMvc.perform(get("/api/v1/files/{fileId}/content", "file-002")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://s3.example.com/private/file-002?signature=abc"))
                .andExpect(header().string("Cache-Control", "private, max-age=900"));

        verify(fileAccessService).getFileUrl("test-app", "file-002", null);
    }
}
