package com.architectcgz.file.infrastructure.cache;

import com.architectcgz.file.infrastructure.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 文件 URL 缓存管理器
 *
 * 统一管理文件 URL 的缓存读写和清除操作，供 Application 层各 Service 注入使用。
 * 所有缓存操作在异常时降级处理，不阻断业务流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUrlCacheManager {

    private final RedisTemplate<String, String> redisTemplate;
    private final CacheProperties cacheProperties;

    /**
     * 从缓存获取文件 URL
     *
     * @param fileId 文件ID
     * @return 缓存的 URL，缓存未命中或异常时返回 null
     */
    public String get(String fileId) {
        if (!cacheProperties.isEnabled()) {
            return null;
        }

        try {
            String cacheKey = FileRedisKeys.fileUrl(fileId);
            String cachedUrl = redisTemplate.opsForValue().get(cacheKey);

            if (cachedUrl != null) {
                log.debug("Cache hit: fileId={}", fileId);
            } else {
                log.debug("Cache miss: fileId={}", fileId);
            }
            return cachedUrl;
        } catch (Exception e) {
            log.warn("Failed to get cached URL, fallback to database: fileId={}", fileId, e);
            return null;
        }
    }

    /**
     * 将文件 URL 写入缓存
     *
     * @param fileId 文件ID
     * @param url    文件访问URL
     */
    public void put(String fileId, String url) {
        if (!cacheProperties.isEnabled()) {
            return;
        }

        try {
            String cacheKey = FileRedisKeys.fileUrl(fileId);
            long ttl = cacheProperties.getUrl().getTtl();
            redisTemplate.opsForValue().set(cacheKey, url, ttl, TimeUnit.SECONDS);
            log.debug("Cached URL: fileId={}, ttl={}s", fileId, ttl);
        } catch (Exception e) {
            log.warn("Failed to cache URL: fileId={}", fileId, e);
            // 缓存写入失败不影响业务流程
        }
    }

    /**
     * 清除文件 URL 缓存
     *
     * @param fileId 文件ID
     */
    public void evict(String fileId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }

        try {
            String cacheKey = FileRedisKeys.fileUrl(fileId);
            Boolean deleted = redisTemplate.delete(cacheKey);

            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Evicted URL cache: fileId={}", fileId);
            } else {
                log.debug("No URL cache to evict: fileId={}", fileId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict URL cache: fileId={}", fileId, e);
            // 缓存清除失败不影响业务流程
        }
    }
}
