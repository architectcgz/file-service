package com.architectcgz.file.application.service;

import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 分片上传数据库写操作事务助手
 *
 * <p>将分片记录的持久化操作封装为独立的短事务，
 * 避免在持有分布式锁期间同时持有数据库事务，
 * 防止长事务占用连接池资源。
 *
 * <p>之所以抽取到独立 Spring Bean，是因为 Spring AOP 代理基于接口/子类，
 * 同一个类内部的 self-invocation 无法触发事务拦截器，
 * 必须通过外部 Bean 调用才能使 @Transactional 生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadPartTransactionHelper {

    private final UploadPartRepository uploadPartRepository;

    /**
     * 在独立短事务中保存分片记录
     *
     * @param taskId     上传任务 ID
     * @param partNumber 分片编号（1-based）
     * @param etag       S3 返回的 ETag
     * @param size       分片字节数
     */
    @Transactional
    public void savePart(String taskId, int partNumber, String etag, long size) {
        UploadPart part = new UploadPart();
        part.setId(UuidCreator.getTimeOrderedEpoch().toString());
        part.setTaskId(taskId);
        part.setPartNumber(partNumber);
        part.setEtag(etag);
        part.setSize(size);
        part.setUploadedAt(LocalDateTime.now());

        uploadPartRepository.savePart(part);
        log.debug("分片记录已保存: taskId={}, partNumber={}, etag={}", taskId, partNumber, etag);
    }
}
