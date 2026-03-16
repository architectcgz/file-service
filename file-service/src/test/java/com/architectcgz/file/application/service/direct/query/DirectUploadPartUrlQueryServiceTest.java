package com.architectcgz.file.application.service.direct.query;

import com.architectcgz.file.application.dto.DirectUploadPartUrlRequest;
import com.architectcgz.file.application.dto.DirectUploadPartUrlResponse;
import com.architectcgz.file.application.service.direct.bridge.DirectUploadCoreBridgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectUploadPartUrlQueryService 单元测试")
class DirectUploadPartUrlQueryServiceTest {

    @Mock
    private DirectUploadCoreBridgeService directUploadCoreBridgeService;

    private DirectUploadPartUrlQueryService directUploadPartUrlQueryService;

    @BeforeEach
    void setUp() {
        directUploadPartUrlQueryService = new DirectUploadPartUrlQueryService(directUploadCoreBridgeService);
    }

    @Test
    @DisplayName("获取直传分片地址应委托给 core bridge")
    void getPartUploadUrls_shouldDelegateToCoreBridge() {
        DirectUploadPartUrlRequest request = new DirectUploadPartUrlRequest();
        request.setTaskId("task-001");
        request.setPartNumbers(List.of(1, 2));

        DirectUploadPartUrlResponse response = DirectUploadPartUrlResponse.builder()
                .taskId("task-001")
                .partUrls(List.of())
                .build();
        when(directUploadCoreBridgeService.getPartUploadUrls("blog", request, "user-123"))
                .thenReturn(response);

        DirectUploadPartUrlResponse actual =
                directUploadPartUrlQueryService.getPartUploadUrls("blog", request, "user-123");

        assertThat(actual).isSameAs(response);
        verify(directUploadCoreBridgeService).getPartUploadUrls("blog", request, "user-123");
    }
}
