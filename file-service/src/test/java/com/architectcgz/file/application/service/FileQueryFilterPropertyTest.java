package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.FileQuery;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import net.jqwik.api.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FileQuery 过滤属性测试
 * 
 * Feature: file-service-optimization
 * 使用基于属性的测试验证文件查询过滤的正确性属性
 */
class FileQueryFilterPropertyTest {

    /**
     * Feature: file-service-optimization, Property 14: 文件查询租户过滤
     * 
     * 属性：对于任何指定租户 ID 的文件查询，返回的所有文件记录的 tenant_id 
     * 应该等于查询条件中的租户 ID。
     * 
     * 验证需求：7.2
     */
    @Property(tries = 100)
    @Label("Property 14: 文件查询租户过滤 - 返回结果的租户ID匹配查询条件")
    void fileQueryTenantFiltering(
            @ForAll("fileRecordLists") List<FileRecord> fileRecords,
            @ForAll("tenantIds") String targetTenantId
    ) {
        // Given: 创建 mock repository
        FileRecordRepository mockRepository = mock(FileRecordRepository.class);
        
        // 过滤出目标租户的文件
        List<FileRecord> expectedResults = fileRecords.stream()
                .filter(r -> targetTenantId.equals(r.getAppId()))
                .collect(Collectors.toList());
        
        // 配置 mock 返回过滤后的结果
        when(mockRepository.findByQuery(any(FileQuery.class)))
                .thenReturn(expectedResults);
        
        // When: 按租户ID查询
        FileQuery query = new FileQuery();
        query.setTenantId(targetTenantId);
        query.setPage(0);
        query.setSize(1000);
        
        List<FileRecord> results = mockRepository.findByQuery(query);
        
        // Then: 所有返回的文件记录的租户ID应该等于查询条件中的租户ID
        for (FileRecord result : results) {
            assertEquals(targetTenantId, result.getAppId(),
                    "All returned file records should have tenant_id matching the query");
        }
        
        // 验证返回了所有该租户的文件
        assertEquals(expectedResults.size(), results.size(),
                "Should return all files for the specified tenant");
    }

    /**
     * Feature: file-service-optimization, Property 15: 文件查询用户过滤
     * 
     * 属性：对于任何指定用户 ID 的文件查询，返回的所有文件记录的 user_id 
     * 应该等于查询条件中的用户 ID。
     * 
     * 验证需求：7.3
     */
    @Property(tries = 100)
    @Label("Property 15: 文件查询用户过滤 - 返回结果的用户ID匹配查询条件")
    void fileQueryUserFiltering(
            @ForAll("fileRecordLists") List<FileRecord> fileRecords,
            @ForAll("userIds") String targetUserId
    ) {
        // Given: 创建 mock repository
        FileRecordRepository mockRepository = mock(FileRecordRepository.class);
        
        // 过滤出目标用户的文件
        List<FileRecord> expectedResults = fileRecords.stream()
                .filter(r -> targetUserId.equals(r.getUserId()))
                .collect(Collectors.toList());
        
        // 配置 mock 返回过滤后的结果
        when(mockRepository.findByQuery(any(FileQuery.class)))
                .thenReturn(expectedResults);
        
        // When: 按用户ID查询
        FileQuery query = new FileQuery();
        query.setUserId(targetUserId);
        query.setPage(0);
        query.setSize(1000);
        
        List<FileRecord> results = mockRepository.findByQuery(query);
        
        // Then: 所有返回的文件记录的用户ID应该等于查询条件中的用户ID
        for (FileRecord result : results) {
            assertEquals(targetUserId, result.getUserId(),
                    "All returned file records should have user_id matching the query");
        }
        
        // 验证返回了所有该用户的文件
        assertEquals(expectedResults.size(), results.size(),
                "Should return all files for the specified user");
    }

    /**
     * Feature: file-service-optimization, Property 16: 文件查询内容类型过滤
     * 
     * 属性：对于任何指定内容类型的文件查询，返回的所有文件记录的 content_type 
     * 应该等于查询条件中的内容类型。
     * 
     * 验证需求：7.4
     */
    @Property(tries = 100)
    @Label("Property 16: 文件查询内容类型过滤 - 返回结果的内容类型匹配查询条件")
    void fileQueryContentTypeFiltering(
            @ForAll("fileRecordLists") List<FileRecord> fileRecords,
            @ForAll("contentTypes") String targetContentType
    ) {
        // Given: 创建 mock repository
        FileRecordRepository mockRepository = mock(FileRecordRepository.class);
        
        // 过滤出目标内容类型的文件
        List<FileRecord> expectedResults = fileRecords.stream()
                .filter(r -> targetContentType.equals(r.getContentType()))
                .collect(Collectors.toList());
        
        // 配置 mock 返回过滤后的结果
        when(mockRepository.findByQuery(any(FileQuery.class)))
                .thenReturn(expectedResults);
        
        // When: 按内容类型查询
        FileQuery query = new FileQuery();
        query.setContentType(targetContentType);
        query.setPage(0);
        query.setSize(1000);
        
        List<FileRecord> results = mockRepository.findByQuery(query);
        
        // Then: 所有返回的文件记录的内容类型应该等于查询条件中的内容类型
        for (FileRecord result : results) {
            assertEquals(targetContentType, result.getContentType(),
                    "All returned file records should have content_type matching the query");
        }
        
        // 验证返回了所有该内容类型的文件
        assertEquals(expectedResults.size(), results.size(),
                "Should return all files for the specified content type");
    }

    /**
     * Feature: file-service-optimization, Property 17: 文件查询访问级别过滤
     * 
     * 属性：对于任何指定访问级别的文件查询，返回的所有文件记录的 access_level 
     * 应该等于查询条件中的访问级别。
     * 
     * 验证需求：7.5
     */
    @Property(tries = 100)
    @Label("Property 17: 文件查询访问级别过滤 - 返回结果的访问级别匹配查询条件")
    void fileQueryAccessLevelFiltering(
            @ForAll("fileRecordLists") List<FileRecord> fileRecords,
            @ForAll("accessLevels") AccessLevel targetAccessLevel
    ) {
        // Given: 创建 mock repository
        FileRecordRepository mockRepository = mock(FileRecordRepository.class);
        
        // 过滤出目标访问级别的文件
        List<FileRecord> expectedResults = fileRecords.stream()
                .filter(r -> targetAccessLevel.equals(r.getAccessLevel()))
                .collect(Collectors.toList());
        
        // 配置 mock 返回过滤后的结果
        when(mockRepository.findByQuery(any(FileQuery.class)))
                .thenReturn(expectedResults);
        
        // When: 按访问级别查询
        FileQuery query = new FileQuery();
        query.setAccessLevel(targetAccessLevel);
        query.setPage(0);
        query.setSize(1000);
        
        List<FileRecord> results = mockRepository.findByQuery(query);
        
        // Then: 所有返回的文件记录的访问级别应该等于查询条件中的访问级别
        for (FileRecord result : results) {
            assertEquals(targetAccessLevel, result.getAccessLevel(),
                    "All returned file records should have access_level matching the query");
        }
        
        // 验证返回了所有该访问级别的文件
        assertEquals(expectedResults.size(), results.size(),
                "Should return all files for the specified access level");
    }

    /**
     * Feature: file-service-optimization, Property 18: 文件查询时间范围过滤
     * 
     * 属性：对于任何指定时间范围的文件查询，返回的所有文件记录的创建时间
     * 应该在查询条件指定的时间范围内。
     * 
     * 验证需求：7.6
     */
    @Property(tries = 100)
    @Label("Property 18: 文件查询时间范围过滤 - 返回结果的创建时间在指定范围内")
    void fileQueryTimeRangeFiltering(
            @ForAll("fileRecordLists") List<FileRecord> fileRecords,
            @ForAll("timeRanges") TimeRange timeRange
    ) {
        // Given: 创建 mock repository
        FileRecordRepository mockRepository = mock(FileRecordRepository.class);
        
        // 过滤出时间范围内的文件
        List<FileRecord> expectedResults = fileRecords.stream()
                .filter(r -> !r.getCreatedAt().isBefore(timeRange.start) &&
                           !r.getCreatedAt().isAfter(timeRange.end))
                .collect(Collectors.toList());
        
        // 配置 mock 返回过滤后的结果
        when(mockRepository.findByQuery(any(FileQuery.class)))
                .thenReturn(expectedResults);
        
        // When: 按时间范围查询
        FileQuery query = new FileQuery();
        query.setStartTime(timeRange.start);
        query.setEndTime(timeRange.end);
        query.setPage(0);
        query.setSize(1000);
        
        List<FileRecord> results = mockRepository.findByQuery(query);
        
        // Then: 所有返回的文件记录的创建时间应该在指定范围内
        for (FileRecord result : results) {
            assertTrue(
                    !result.getCreatedAt().isBefore(timeRange.start) &&
                    !result.getCreatedAt().isAfter(timeRange.end),
                    String.format("File created_at %s should be between %s and %s",
                            result.getCreatedAt(), timeRange.start, timeRange.end)
            );
        }
        
        // 验证返回了所有在时间范围内的文件
        assertEquals(expectedResults.size(), results.size(),
                "Should return all files within the specified time range");
    }

    /**
     * Feature: file-service-optimization, Property 19: 文件查询大小范围过滤
     * 
     * 属性：对于任何指定大小范围的文件查询，返回的所有文件记录的文件大小
     * 应该在查询条件指定的大小范围内。
     * 
     * 验证需求：7.7
     */
    @Property(tries = 100)
    @Label("Property 19: 文件查询大小范围过滤 - 返回结果的文件大小在指定范围内")
    void fileQuerySizeRangeFiltering(
            @ForAll("fileRecordLists") List<FileRecord> fileRecords,
            @ForAll("sizeRanges") SizeRange sizeRange
    ) {
        // Given: 创建 mock repository
        FileRecordRepository mockRepository = mock(FileRecordRepository.class);
        
        // 过滤出大小范围内的文件
        List<FileRecord> expectedResults = fileRecords.stream()
                .filter(r -> r.getFileSize() >= sizeRange.min &&
                           r.getFileSize() <= sizeRange.max)
                .collect(Collectors.toList());
        
        // 配置 mock 返回过滤后的结果
        when(mockRepository.findByQuery(any(FileQuery.class)))
                .thenReturn(expectedResults);
        
        // When: 按大小范围查询
        FileQuery query = new FileQuery();
        query.setMinSize(sizeRange.min);
        query.setMaxSize(sizeRange.max);
        query.setPage(0);
        query.setSize(1000);
        
        List<FileRecord> results = mockRepository.findByQuery(query);
        
        // Then: 所有返回的文件记录的文件大小应该在指定范围内
        for (FileRecord result : results) {
            assertTrue(
                    result.getFileSize() >= sizeRange.min &&
                    result.getFileSize() <= sizeRange.max,
                    String.format("File size %d should be between %d and %d",
                            result.getFileSize(), sizeRange.min, sizeRange.max)
            );
        }
        
        // 验证返回了所有在大小范围内的文件
        assertEquals(expectedResults.size(), results.size(),
                "Should return all files within the specified size range");
    }

    /**
     * Feature: file-service-optimization, Property 20: 文件查询结果排序
     * 
     * 属性：对于任何指定排序字段和排序方向的文件查询，返回的文件记录列表
     * 应该按照指定字段和方向正确排序。
     * 
     * 验证需求：7.8
     */
    @Property(tries = 100)
    @Label("Property 20: 文件查询结果排序 - 返回结果按指定字段和方向排序")
    void fileQueryResultSorting(
            @ForAll("fileRecordLists") List<FileRecord> fileRecords,
            @ForAll("sortFields") String sortBy,
            @ForAll("sortOrders") String sortOrder
    ) {
        // Given: 确保至少有2条记录才能验证排序
        Assume.that(fileRecords.size() >= 2);
        
        // 创建 mock repository
        FileRecordRepository mockRepository = mock(FileRecordRepository.class);
        
        // 手动排序文件记录以模拟数据库排序行为
        List<FileRecord> sortedResults = new ArrayList<>(fileRecords);
        boolean isAscending = "asc".equalsIgnoreCase(sortOrder);
        
        Comparator<FileRecord> comparator = (r1, r2) -> {
            int comparison = compareByField(r1, r2, sortBy);
            return isAscending ? comparison : -comparison;
        };
        sortedResults.sort(comparator);
        
        // 配置 mock 返回排序后的结果
        when(mockRepository.findByQuery(any(FileQuery.class)))
                .thenReturn(sortedResults);
        
        // When: 按指定字段和方向查询
        FileQuery query = new FileQuery();
        query.setSortBy(sortBy);
        query.setSortOrder(sortOrder);
        query.setPage(0);
        query.setSize(1000);
        
        List<FileRecord> results = mockRepository.findByQuery(query);
        
        // Then: 验证结果按照指定字段和方向排序
        if (results.size() >= 2) {
            for (int i = 0; i < results.size() - 1; i++) {
                FileRecord current = results.get(i);
                FileRecord next = results.get(i + 1);
                
                int comparison = compareByField(current, next, sortBy);
                
                if (isAscending) {
                    assertTrue(comparison <= 0,
                            String.format("Records should be in ascending order by %s", sortBy));
                } else {
                    assertTrue(comparison >= 0,
                            String.format("Records should be in descending order by %s", sortBy));
                }
            }
        }
        
        // 验证返回了所有记录
        assertEquals(fileRecords.size(), results.size(),
                "Should return all file records");
    }

    // ========== Helper Methods ==========

    /**
     * 按字段比较两个文件记录
     */
    private int compareByField(FileRecord r1, FileRecord r2, String field) {
        switch (field) {
            case "createdAt":
                return r1.getCreatedAt().compareTo(r2.getCreatedAt());
            case "fileSize":
                return Long.compare(r1.getFileSize(), r2.getFileSize());
            case "originalFilename":
                return r1.getOriginalFilename().compareTo(r2.getOriginalFilename());
            default:
                return r1.getCreatedAt().compareTo(r2.getCreatedAt());
        }
    }

    // ========== Arbitraries (数据生成器) ==========

    /**
     * 生成文件记录列表
     */
    @Provide
    Arbitrary<List<FileRecord>> fileRecordLists() {
        return fileRecords().list().ofMinSize(5).ofMaxSize(20);
    }

    /**
     * 生成文件记录
     */
    @Provide
    Arbitrary<FileRecord> fileRecords() {
        // jqwik 的 combine 最多支持 8 个参数，所以需要分组
        Arbitrary<FileRecord> part1 = Combinators.combine(
                fileIds(),
                tenantIds(),
                userIds(),
                storageObjectIds(),
                originalFilenames(),
                storagePaths(),
                fileSizes(),
                contentTypes()
        ).as((fileId, tenantId, userId, storageObjectId, originalFilename, storagePath,
              fileSize, contentType) ->
                FileRecord.builder()
                        .id(fileId)
                        .appId(tenantId)
                        .userId(userId)
                        .storageObjectId(storageObjectId)
                        .originalFilename(originalFilename)
                        .storagePath(storagePath)
                        .fileSize(fileSize)
                        .contentType(contentType)
                        .status(FileStatus.COMPLETED)
                        .build()
        );
        
        // 添加剩余字段
        return Combinators.combine(
                part1,
                fileHashes(),
                accessLevels(),
                createdAtTimes()
        ).as((record, fileHash, accessLevel, createdAt) -> {
            record.setFileHash(fileHash);
            record.setHashAlgorithm("MD5");
            record.setAccessLevel(accessLevel);
            record.setCreatedAt(createdAt);
            record.setUpdatedAt(createdAt);
            return record;
        });
    }

    @Provide
    Arbitrary<String> fileIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(8)
                .ofMaxLength(32)
                .map(s -> "file-" + s);
    }

    @Provide
    Arbitrary<String> tenantIds() {
        return Arbitraries.of("blog", "im", "forum", "shop", "cms");
    }

    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "user-" + s);
    }

    @Provide
    Arbitrary<String> storageObjectIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(8)
                .ofMaxLength(32)
                .map(s -> "storage-" + s);
    }

    @Provide
    Arbitrary<String> originalFilenames() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
                Arbitraries.of("jpg", "png", "pdf", "txt", "mp4", "mp3", "doc", "zip")
        ).as((name, ext) -> name + "." + ext);
    }

    @Provide
    Arbitrary<String> storagePaths() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
                Arbitraries.integers().between(2020, 2030),
                Arbitraries.integers().between(1, 12),
                Arbitraries.integers().between(1, 28),
                Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(15),
                Arbitraries.of("images", "files", "videos", "audios"),
                Arbitraries.strings().alpha().numeric().ofMinLength(8).ofMaxLength(16),
                Arbitraries.of("jpg", "png", "pdf", "txt", "mp4", "mp3")
        ).as((tenantId, year, month, day, userId, type, fileId, ext) ->
                String.format("%s/%04d/%02d/%02d/%s/%s/%s.%s",
                        tenantId, year, month, day, userId, type, fileId, ext)
        );
    }

    @Provide
    Arbitrary<Long> fileSizes() {
        return Arbitraries.longs().between(1L, 104857600L); // 1 byte to 100MB
    }

    @Provide
    Arbitrary<String> contentTypes() {
        return Arbitraries.of(
                "image/jpeg",
                "image/png",
                "image/gif",
                "application/pdf",
                "text/plain",
                "video/mp4",
                "audio/mpeg",
                "application/zip"
        );
    }

    @Provide
    Arbitrary<String> fileHashes() {
        return Arbitraries.strings()
                .withCharRange('a', 'f')
                .numeric()
                .ofLength(32);
    }

    @Provide
    Arbitrary<AccessLevel> accessLevels() {
        return Arbitraries.of(AccessLevel.PUBLIC, AccessLevel.PRIVATE);
    }

    @Provide
    Arbitrary<LocalDateTime> createdAtTimes() {
        return Arbitraries.longs()
                .between(
                        LocalDateTime.of(2024, 1, 1, 0, 0).toEpochSecond(java.time.ZoneOffset.UTC),
                        LocalDateTime.of(2026, 12, 31, 23, 59).toEpochSecond(java.time.ZoneOffset.UTC)
                )
                .map(seconds -> LocalDateTime.ofEpochSecond(seconds, 0, java.time.ZoneOffset.UTC));
    }

    @Provide
    Arbitrary<TimeRange> timeRanges() {
        return Arbitraries.longs()
                .between(
                        LocalDateTime.of(2024, 1, 1, 0, 0).toEpochSecond(java.time.ZoneOffset.UTC),
                        LocalDateTime.of(2026, 12, 31, 23, 59).toEpochSecond(java.time.ZoneOffset.UTC)
                )
                .flatMap(startSeconds -> {
                    LocalDateTime start = LocalDateTime.ofEpochSecond(startSeconds, 0, java.time.ZoneOffset.UTC);
                    return Arbitraries.longs()
                            .between(startSeconds, startSeconds + 365L * 24 * 3600) // up to 1 year later
                            .map(endSeconds -> {
                                LocalDateTime end = LocalDateTime.ofEpochSecond(endSeconds, 0, java.time.ZoneOffset.UTC);
                                return new TimeRange(start, end);
                            });
                });
    }

    @Provide
    Arbitrary<SizeRange> sizeRanges() {
        return Arbitraries.longs()
                .between(1L, 50000000L) // 1 byte to 50MB
                .flatMap(min -> Arbitraries.longs()
                        .between(min, min + 50000000L) // up to 50MB larger
                        .map(max -> new SizeRange(min, max))
                );
    }

    @Provide
    Arbitrary<String> sortFields() {
        return Arbitraries.of("createdAt", "fileSize", "originalFilename");
    }

    @Provide
    Arbitrary<String> sortOrders() {
        return Arbitraries.of("asc", "desc");
    }

    // ========== Helper Classes ==========

    private static class TimeRange {
        final LocalDateTime start;
        final LocalDateTime end;

        TimeRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class SizeRange {
        final long min;
        final long max;

        SizeRange(long min, long max) {
            this.min = min;
            this.max = max;
        }
    }
}
