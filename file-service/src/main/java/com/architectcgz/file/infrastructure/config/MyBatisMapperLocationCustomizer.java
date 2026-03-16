package com.architectcgz.file.infrastructure.config;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;

/**
 * 允许模块按需补充 MyBatis mapper XML，而不把兼容层知识固化到主配置。
 */
public interface MyBatisMapperLocationCustomizer {

    void customize(List<Resource> mapperResources, PathMatchingResourcePatternResolver resolver) throws Exception;
}
