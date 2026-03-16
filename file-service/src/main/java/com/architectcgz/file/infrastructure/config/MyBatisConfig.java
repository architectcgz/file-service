package com.architectcgz.file.infrastructure.config;

import com.architectcgz.file.infrastructure.repository.mapper.RuntimeMyBatisMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis Configuration
 */
@Configuration
@MapperScan(
        basePackages = "com.architectcgz.file.infrastructure.repository.mapper",
        markerInterface = RuntimeMyBatisMapper.class
)
public class MyBatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(
            DataSource dataSource,
            ObjectProvider<MyBatisMapperLocationCustomizer> mapperLocationCustomizers
    ) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        sessionFactory.setMapperLocations(resolveMapperLocations(resolver, mapperLocationCustomizers));

        // Register custom type handlers
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultStatementTimeout(30);
        configuration.getTypeHandlerRegistry().register(String[].class, StringArrayTypeHandler.class);
        sessionFactory.setConfiguration(configuration);

        return sessionFactory.getObject();
    }

    private Resource[] resolveMapperLocations(
            PathMatchingResourcePatternResolver resolver,
            ObjectProvider<MyBatisMapperLocationCustomizer> mapperLocationCustomizers
    ) throws Exception {
        List<Resource> mapperResources = new ArrayList<>();
        mapperResources.addAll(List.of(resolver.getResources("classpath:mapper/AuditLogMapper.xml")));
        mapperResources.addAll(List.of(resolver.getResources("classpath:mapper/FileRecordMapper.xml")));
        for (MyBatisMapperLocationCustomizer mapperLocationCustomizer : mapperLocationCustomizers) {
            mapperLocationCustomizer.customize(mapperResources, resolver);
        }
        return mapperResources.toArray(Resource[]::new);
    }
}
