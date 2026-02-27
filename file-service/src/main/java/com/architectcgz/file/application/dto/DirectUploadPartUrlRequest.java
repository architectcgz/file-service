package com.architectcgz.file.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 获取分片上传 URL 请求
 */
@Data
public class DirectUploadPartUrlRequest {
    
    /**
     * 上传任务ID
     */
    @NotBlank(message = "任务ID不能为空")
    private String taskId;
    
    /**
     * 需要获取上传 URL 的分片编号列表
     */
    @NotEmpty(message = "分片编号列表不能为空")
    private List<Integer> partNumbers;
}
