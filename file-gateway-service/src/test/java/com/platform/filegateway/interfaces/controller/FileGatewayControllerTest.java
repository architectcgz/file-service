package com.platform.filegateway.interfaces.controller;

import com.platform.filegateway.common.exception.GlobalExceptionHandler;
import com.platform.filegateway.domain.GatewayRedirectResponse;
import com.platform.filegateway.service.FileGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileGatewayController.class)
@Import(GlobalExceptionHandler.class)
class FileGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileGatewayService fileGatewayService;

    @Test
    void shouldRedirectToResolvedLocation() throws Exception {
        when(fileGatewayService.resolveRedirect(
                eq("file-001"),
                eq("blog"),
                eq("user-001"),
                eq(null),
                eq(null),
                eq(null),
                eq(null)))
                .thenReturn(new GatewayRedirectResponse("https://cdn.example.com/file-001", "no-store"));

        mockMvc.perform(get("/api/v1/files/{fileId}/content", "file-001")
                        .header("X-App-Id", "blog")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://cdn.example.com/file-001"))
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(fileGatewayService).resolveRedirect("file-001", "blog", "user-001", null, null, null, null);
    }
}
