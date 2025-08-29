package org.oldskooler.entity4j;

import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.dialect.SqlDialectType;
import org.oldskooler.entity4j.mapping.ModelBuilder;

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