package com.architectcgz.file.infrastructure.mybatis;

import com.architectcgz.file.domain.model.AccessLevel;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 类型处理器：AccessLevel 枚举
 * 
 * 处理数据库中小写字符串与 Java 枚举之间的转换
 * 数据库存储: "public", "private" (小写)
 * Java 枚举: PUBLIC, PRIVATE (大写)
 */
@MappedTypes(AccessLevel.class)
public class AccessLevelTypeHandler extends BaseTypeHandler<AccessLevel> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, AccessLevel parameter, JdbcType jdbcType) 
            throws SQLException {
        // 存储到数据库时转换为小写
        ps.setString(i, parameter.name().toLowerCase());
    }

    @Override
    public AccessLevel getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : AccessLevel.valueOf(value.toUpperCase());
    }

    @Override
    public AccessLevel getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : AccessLevel.valueOf(value.toUpperCase());
    }

    @Override
    public AccessLevel getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : AccessLevel.valueOf(value.toUpperCase());
    }
}
