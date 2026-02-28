package com.architectcgz.file.application.dto;

import lombok.Data;

/**
 * 按内容类型分组的文件计数
 * 承载 SQL 层 GROUP BY content_type 聚合查询的返回值
 */
@Data
public class ContentTypeCount {

    /**
     * 内容类型（MIME type）
     */
    private String contentType;

    /**
     * 该类型的文件数量
     */
    private long fileCount;
}
