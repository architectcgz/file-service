package com.architectcgz.file.infrastructure.mybatis;

import com.architectcgz.file.domain.model.FileStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 类型处理器：FileStatus 枚举
 * 
 * 处理数据库中小写字符串与 Java 枚举之间的转换
 * 数据库存储: "completed", "deleted" (小写)
 * Java 枚举: COMPLETED, DELETED (大写)
 */
@MappedTypes(FileStatus.class)
public class FileStatusTypeHandler extends BaseTypeHandler<FileStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, FileStatus parameter, JdbcType jdbcType) 
            throws SQLException {
        // 存储到数据库时转换为小写
        ps.setString(i, parameter.name().toLowerCase());
    }

    @Override
    public FileStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : FileStatus.valueOf(value.toUpperCase());
    }

    @Override
    public FileStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : FileStatus.valueOf(value.toUpperCase());
    }

    @Override
    public FileStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : FileStatus.valueOf(value.toUpperCase());
    }
}
