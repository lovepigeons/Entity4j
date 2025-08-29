package com.example.miniorm;

import com.example.miniorm.IDbContext;
import com.example.miniorm.dialect.SqlDialect;
import com.example.miniorm.dialect.SqlDialectType;
import com.example.miniorm.mapping.ModelBuilder;

import java.sql.Connection;
import java.sql.SQLException;

public abstract class DbContext extends IDbContext {
    public DbContext(Connection connection) throws SQLException {
        super(connection);
    }

    public DbContext(Connection connection, SqlDialectType dialectType) {
        super(connection, dialectType);
    }

    public DbContext(Connection connection, SqlDialect dialect) {
        super(connection, dialect);
    }

    public abstract void onModelCreating(ModelBuilder model);
}