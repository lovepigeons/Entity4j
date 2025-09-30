package org.oldskooler.entity4j.transaction;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Enhanced implementation of Transaction with support for options and advanced features.
 */
public class DbTransactionEnhanced extends DbTransaction {
    private final TransactionOptions options;
    private final int originalIsolationLevel;
    private final boolean originalReadOnly;

    /**
     * Creates a new transaction with the given connection and options.
     *
     * @param connection the JDBC connection to use for this transaction
     * @param options the transaction options
     * @throws SQLException if a database access error occurs
     */
    public DbTransactionEnhanced(Connection connection, TransactionOptions options) throws SQLException {
        super(connection);

        if (options == null) {
            throw new IllegalArgumentException("Transaction options cannot be null");
        }

        this.options = options;
        this.originalIsolationLevel = connection.getTransactionIsolation();
        this.originalReadOnly = connection.isReadOnly();

        // Apply transaction options
        applyOptions(connection);
    }

    /**
     * Gets the options for this transaction.
     *
     * @return the transaction options
     */
    public TransactionOptions getOptions() {
        return options;
    }

    @Override
    public void commit() throws SQLException {
        try {
            super.commit();
        } finally {
            restoreConnectionSettings();
        }
    }

    @Override
    public void rollback() throws SQLException {
        try {
            super.rollback();
        } finally {
            restoreConnectionSettings();
        }
    }

    private void applyOptions(Connection connection) throws SQLException {
        // Set isolation level
        if (options.getIsolationLevel() != null) {
            connection.setTransactionIsolation(options.getIsolationLevel().getJdbcLevel());
        }

        // Set read-only flag
        connection.setReadOnly(options.isReadOnly());

        // Note: JDBC doesn't have a standard way to set transaction timeout
        // This would need to be implemented at the database-specific level
        // or using query timeouts on individual statements
    }

    private void restoreConnectionSettings() {
        try {
            Connection conn = getConnection();
            conn.setTransactionIsolation(originalIsolationLevel);
            conn.setReadOnly(originalReadOnly);
        } catch (SQLException e) {
            System.err.println("Warning: Failed to restore connection settings: " + e.getMessage());
        }
    }
}