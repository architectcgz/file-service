package com.platform.fileservice.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 通用API响应包装器
 * 使用统一的结构包装所有API响应，包含状态码、消息和数据
 *
 * @param <T> 响应数据的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    /**
     * 响应状态码（通常是HTTP状态码）
     */
    private int code;
    
    /**
     * 响应消息（成功消息或错误描述）
     */
    private String message;
    
    /**
     * 响应数据负载
     */
    private T data;
    
    /**
     * 检查响应是否表示成功
     *
     * @return 如果code为200则返回true，否则返回false
     */
    public boolean isSuccess() {
        return code == 200;
    }
}
