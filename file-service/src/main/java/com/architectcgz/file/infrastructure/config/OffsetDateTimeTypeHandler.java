package com.architectcgz.file.infrastructure.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.time.OffsetDateTime;

/**
 * MyBatis TypeHandler for OffsetDateTime to handle PostgreSQL TIMESTAMPTZ
 */
@MappedTypes(OffsetDateTime.class)
public class OffsetDateTimeTypeHandler extends BaseTypeHandler<OffsetDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OffsetDateTime parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object object = rs.getObject(columnName);
        if (object == null) {
            return null;
        }
        if (object instanceof OffsetDateTime) {
            return (OffsetDateTime) object;
        }
        if (object instanceof Timestamp) {
            Timestamp timestamp = (Timestamp) object;
            return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        return null;
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object object = rs.getObject(columnIndex);
        if (object == null) {
            return null;
        }
        if (object instanceof OffsetDateTime) {
            return (OffsetDateTime) object;
        }
        if (object instanceof Timestamp) {
            Timestamp timestamp = (Timestamp) object;
            return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        return null;
    }

    @Override
    public OffsetDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object object = cs.getObject(columnIndex);
        if (object == null) {
            return null;
        }
        if (object instanceof OffsetDateTime) {
            return (OffsetDateTime) object;
        }
        if (object instanceof Timestamp) {
            Timestamp timestamp = (Timestamp) object;
            return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        return null;
    }
}
