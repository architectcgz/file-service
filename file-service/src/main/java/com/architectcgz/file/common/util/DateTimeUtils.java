package com.architectcgz.file.common.util;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具类
 */
public class DateTimeUtils {
    
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    
    /**
     * 将任意偏移时间转换为 UTC 偏移时间。
     */
    public static OffsetDateTime toUtc(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.withOffsetSameInstant(ZoneOffset.UTC);
    }
    
    /**
     * 统一为 UTC 偏移时间，便于输出和存储一致。
     */
    public static OffsetDateTime fromUtc(OffsetDateTime utcDateTime) {
        if (utcDateTime == null) {
            return null;
        }
        return utcDateTime.withOffsetSameInstant(ZoneOffset.UTC);
    }
    
    /**
     * 格式化日期时间
     */
    public static String format(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DEFAULT_FORMATTER);
    }
    
    /**
     * 解析日期时间字符串
     */
    public static OffsetDateTime parse(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        return OffsetDateTime.parse(dateTimeStr, DEFAULT_FORMATTER);
    }
}
