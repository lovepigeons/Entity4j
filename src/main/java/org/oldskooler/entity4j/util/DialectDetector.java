package org.oldskooler.entity4j.util;

import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.dialect.SqlDialectType;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;

public final class DialectDetector {
    private DialectDetector() {}

    public static SqlDialect detectDialect(Connection c) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        String url = (md.getURL() == null ? "" : md.getURL()).toLowerCase(Locale.ROOT);
        String product = (md.getDatabaseProductName() == null ? "" : md.getDatabaseProductName()).toLowerCase(Locale.ROOT);

        if (url.startsWith("jdbc:mysql:") || product.contains("mysql") || product.contains("mariadb"))
            return SqlDialectType.MYSQL.createDialect();
        if (url.startsWith("jdbc:postgresql:") || product.contains("postgres"))
            return SqlDialectType.POSTGRESQL.createDialect();
        if (url.startsWith("jdbc:sqlserver:") || product.contains("microsoft sql server"))
            return SqlDialectType.SQLSERVER.createDialect();
        if (url.startsWith("jdbc:sqlite:") || product.contains("sqlite"))
            return SqlDialectType.SQLITE.createDialect();

        throw new IllegalArgumentException("Could not automatically detect sql dialect, please explicitly declare it with: new DbContext(Connection connection, SqlDialectType dialectType)");
    }
}