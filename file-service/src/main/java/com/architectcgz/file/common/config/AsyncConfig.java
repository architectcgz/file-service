package com.architectcgz.file.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 为 Bitmap 同步操作提供独立的线程池
 * 
 * @author architectcgz
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    
    /**
     * Bitmap 同步专用线程池
     * 用于异步同步分片记录到数据库，避免阻塞主业务流程
     * 
     * @return 配置好的线程池执行器
     */
    @Bean(name = "bitmapSyncExecutor")
    public Executor bitmapSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数 - 保持活跃的最小线程数
        executor.setCorePoolSize(5);
        
        // 最大线程数 - 允许的最大线程数
        executor.setMaxPoolSize(10);
        
        // 队列容量 - 等待执行的任务队列大小
        executor.setQueueCapacity(100);
        
        // 线程名称前缀 - 便于日志追踪和问题排查
        executor.setThreadNamePrefix("bitmap-sync-");
        
        // 拒绝策略 - 队列满时由调用线程执行，保证任务不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 线程空闲时间（秒）- 超过核心线程数的线程空闲多久后回收
        executor.setKeepAliveSeconds(60);
        
        // 允许核心线程超时 - 核心线程空闲时也可以回收
        executor.setAllowCoreThreadTimeOut(true);
        
        // 等待任务完成后关闭 - 应用关闭时等待正在执行的任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间（秒）- 应用关闭时最多等待多久
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
}
