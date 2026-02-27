package com.architectcgz.file.application.dto;

import com.architectcgz.file.domain.model.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

/**
 * 更新文件访问级别请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccessLevelRequest {
    
    /**
     * 新的访问级别
     */
    @NotNull(message = "访问级别不能为空")
    private AccessLevel accessLevel;
}
