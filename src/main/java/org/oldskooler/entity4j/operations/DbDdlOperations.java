package org.oldskooler.entity4j.operations;

import org.oldskooler.entity4j.IDbContext;
import org.oldskooler.entity4j.mapping.TableMeta;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles DDL (Data Definition Language) operations like CREATE/DROP TABLE
 */
public class DbDdlOperations {
    private final IDbContext context;

    public DbDdlOperations(IDbContext context) {
        this.context = context;
    }

    public <T> String createTableSql(Class<T> type, boolean ifNotExists) {
        context.ensureModelBuiltInternal();
        TableMeta<T> m = TableMeta.of(type, context.mappingRegistry());
        boolean useIfNotExists = ifNotExists && context.dialect().supportsCreateIfNotExists();
        return context.dialect().createTableDdl(m, useIfNotExists);
    }

    public <T> String dropTableSql(Class<T> type, boolean ifExists) {
        context.ensureModelBuiltInternal();
        TableMeta<T> m = TableMeta.of(type, context.mappingRegistry());
        return context.dialect().dropTableDdl(m, ifExists && context.dialect().supportsDropIfExists());
    }

    public <T> int createTable(Class<T> type) throws SQLException {
        context.ensureModelBuiltInternal();
        String sql = createTableSql(type, true);
        try (Statement st = context.conn().createStatement()) {
            return st.executeUpdate(sql);
        }
    }

    public int createTables(Class<?>... types) throws SQLException {
        int total = 0;
        for (Class<?> t : types) {
            total += createTable(t);
        }
        return total;
    }

    public <T> int dropTableIfExists(Class<T> type) throws SQLException {
        context.ensureModelBuiltInternal();
        String sql = dropTableSql(type, true);
        try (Statement st = context.conn().createStatement()) {
            return st.executeUpdate(sql);
        }
    }
}