package com.platform.fileservice.core.web.controller;

import com.platform.fileservice.core.application.service.AccessAppService;
import com.platform.fileservice.core.domain.exception.FileAccessDeniedException;
import com.platform.fileservice.core.domain.exception.FileAccessMutationException;
import com.platform.fileservice.core.domain.exception.FileAssetNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.AccessTicketGrant;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import com.platform.fileservice.core.web.config.FileCoreAccessProperties;
import com.platform.fileservice.core.web.exception.V1GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V1FileController.class)
@Import({V1GlobalExceptionHandler.class, V1FileControllerTest.TestPropsConfiguration.class})
@TestPropertySource(properties = {
        "file.core.access.gateway-base-url=http://gateway.example.com",
        "file.core.access.ticket-ttl=5m",
        "file.core.access.signing-secret=unit-test-secret"
})
class V1FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccessAppService accessAppService;

    @Test
    void shouldReturnFileDetail() throws Exception {
        when(accessAppService.getAccessibleFile(eq("blog"), eq("file-001"), eq("user-001")))
                .thenReturn(new FileAsset(
                        "file-001",
                        "blog",
                        "user-001",
                        "blob-001",
                        "test.png",
                        "images/test.png",
                        "image/png",
                        1024L,
                        AccessLevel.PUBLIC,
                        FileAssetStatus.ACTIVE,
                        Instant.parse("2026-03-14T00:00:00Z"),
                        Instant.parse("2026-03-14T00:00:00Z")
                ));

        mockMvc.perform(get("/api/v1/files/{fileId}", "file-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value("file-001"))
                .andExpect(jsonPath("$.tenantId").value("blog"))
                .andExpect(jsonPath("$.accessLevel").value("PUBLIC"));

        verify(accessAppService).getAccessibleFile("blog", "file-001", "user-001");
    }

    @Test
    void shouldIssueAccessTicket() throws Exception {
        Instant expiresAt = Instant.parse("2026-03-14T00:05:00Z");
        when(accessAppService.issueAccessTicket(eq("file-001"), eq("blog"), eq("user-001"), eq(java.time.Duration.ofMinutes(5))))
                .thenReturn(new AccessTicketGrant(
                        "ticket-001",
                        "file-001",
                        "blog",
                        "user-001",
                        Instant.parse("2026-03-14T00:00:00Z"),
                        expiresAt,
                        "encoded.ticket"
                ));

        mockMvc.perform(post("/api/v1/files/{fileId}:issue-access-ticket", "file-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").value("encoded.ticket"))
                .andExpect(jsonPath("$.gatewayUrl").value("http://gateway.example.com/api/v1/files/file-001/content?ticket=encoded.ticket"))
                .andExpect(jsonPath("$.expiresAt").value("2026-03-14T00:05:00Z"));

        verify(accessAppService).issueAccessTicket("file-001", "blog", "user-001", java.time.Duration.ofMinutes(5));
    }

    @Test
    void shouldBatchIssueAccessTicketsWithPerItemResults() throws Exception {
        Instant expiresAt = Instant.parse("2026-03-14T00:05:00Z");
        when(accessAppService.issueAccessTicket(eq("file-001"), eq("blog"), eq("user-001"), eq(java.time.Duration.ofMinutes(5))))
                .thenReturn(new AccessTicketGrant(
                        "ticket-001",
                        "file-001",
                        "blog",
                        "user-001",
                        Instant.parse("2026-03-14T00:00:00Z"),
                        expiresAt,
                        "encoded.ticket.1"
                ));
        when(accessAppService.issueAccessTicket(eq("file-002"), eq("blog"), eq("user-001"), eq(java.time.Duration.ofMinutes(5))))
                .thenThrow(new FileAccessDeniedException("access denied for fileId: file-002"));
        when(accessAppService.issueAccessTicket(eq("file-003"), eq("blog"), eq("user-001"), eq(java.time.Duration.ofMinutes(5))))
                .thenThrow(new FileAssetNotFoundException("fileId not found: file-003"));

        mockMvc.perform(post("/api/v1/files:batch-issue-access-ticket")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "fileIds": ["file-001", "file-002", "file-003"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].fileId").value("file-001"))
                .andExpect(jsonPath("$.items[0].ticket").value("encoded.ticket.1"))
                .andExpect(jsonPath("$.items[0].gatewayUrl").value("http://gateway.example.com/api/v1/files/file-001/content?ticket=encoded.ticket.1"))
                .andExpect(jsonPath("$.items[1].fileId").value("file-002"))
                .andExpect(jsonPath("$.items[1].errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.items[1].message").value("access denied for fileId: file-002"))
                .andExpect(jsonPath("$.items[2].fileId").value("file-003"))
                .andExpect(jsonPath("$.items[2].errorCode").value("FILE_NOT_FOUND"))
                .andExpect(jsonPath("$.items[2].message").value("fileId not found: file-003"));

        verify(accessAppService).issueAccessTicket("file-001", "blog", "user-001", java.time.Duration.ofMinutes(5));
        verify(accessAppService).issueAccessTicket("file-002", "blog", "user-001", java.time.Duration.ofMinutes(5));
        verify(accessAppService).issueAccessTicket("file-003", "blog", "user-001", java.time.Duration.ofMinutes(5));
    }

    @Test
    void shouldChangeAccessLevel() throws Exception {
        mockMvc.perform(post("/api/v1/files/{fileId}:change-access-level", "file-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("{\"accessLevel\":\"PRIVATE\"}"))
                .andExpect(status().isOk());

        verify(accessAppService).updateAccessLevel("blog", "file-001", "user-001", AccessLevel.PRIVATE);
    }

    @Test
    void shouldReturnForbiddenWhenChangeAccessLevelDenied() throws Exception {
        doThrow(new FileAccessDeniedException("denied"))
                .when(accessAppService)
                .updateAccessLevel("blog", "file-001", "user-002", AccessLevel.PRIVATE);

        mockMvc.perform(post("/api/v1/files/{fileId}:change-access-level", "file-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-002")
                        .contentType("application/json")
                        .content("{\"accessLevel\":\"PRIVATE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("denied"));
    }

    @Test
    void shouldReturnInternalServerErrorWhenMutationFails() throws Exception {
        doThrow(new FileAccessMutationException("mutation failed"))
                .when(accessAppService)
                .updateAccessLevel("blog", "file-001", "user-001", AccessLevel.PRIVATE);

        mockMvc.perform(post("/api/v1/files/{fileId}:change-access-level", "file-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001")
                        .contentType("application/json")
                        .content("{\"accessLevel\":\"PRIVATE\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("mutation failed"));
    }

    @TestConfiguration
    @EnableConfigurationProperties(FileCoreAccessProperties.class)
    static class TestPropsConfiguration {
    }
}
