package org.oldskooler.entity4j.operations;

import org.oldskooler.entity4j.IDbContext;
import org.oldskooler.entity4j.mapping.TableMeta;
import org.oldskooler.entity4j.util.JdbcParamBinder;
import org.oldskooler.entity4j.util.RowMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Handles query execution and result mapping
 */
public class DbQueryExecutor {
    private final IDbContext context;

    public DbQueryExecutor(IDbContext context) {
        this.context = context;
    }

    public <T> List<T> executeQuery(TableMeta<T> m, String sql, List<Object> params) {
        try (PreparedStatement ps = context.conn().prepareStatement(sql)) {
            JdbcParamBinder.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return RowMapper.mapAll(rs, m);
            }
        } catch (SQLException e) {
            throw new RuntimeException("query failed", e);
        }
    }

    public List<Map<String, Object>> executeQueryMap(String sql, List<Object> params) {
        try (PreparedStatement ps = context.conn().prepareStatement(sql)) {
            JdbcParamBinder.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return RowMapper.toMapList(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("query failed", e);
        }
    }
}