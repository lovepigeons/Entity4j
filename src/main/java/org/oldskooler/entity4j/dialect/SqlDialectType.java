package org.oldskooler.entity4j.dialect;

import org.oldskooler.entity4j.dialect.types.MySqlDialect;
import org.oldskooler.entity4j.dialect.types.PostgresDialect;
import org.oldskooler.entity4j.dialect.types.SqlServerDialect;
import org.oldskooler.entity4j.dialect.types.SqliteDialect;

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
