package com.example.miniorm.dialect;

import com.example.miniorm.dialect.types.MySqlDialect;
import com.example.miniorm.dialect.types.PostgresDialect;
import com.example.miniorm.dialect.types.SqlServerDialect;
import com.example.miniorm.dialect.types.SqliteDialect;

public enum SqlDialectType {
    MYSQL,
    POSTGRESQL,
    SQLSERVER,
    SQLITE;

    public SqlDialect createDialect() {
        switch (this) {
            case MYSQL:
                return new MySqlDialect();
            case POSTGRESQL:
                return new PostgresDialect();
            case SQLSERVER:
                return new SqlServerDialect();
            case SQLITE:
                return new SqliteDialect();
            default:
                // This should be unreachable, but keeps Java 8 compilers happy
                throw new IllegalStateException("Unknown dialect: " + this);
        }
    }
}
