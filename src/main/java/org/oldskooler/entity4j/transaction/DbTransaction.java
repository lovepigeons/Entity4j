package org.oldskooler.entity4j.transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of Transaction interface that wraps a JDBC connection
 * and manages transaction lifecycle.
 */
public class DbTransaction implements Transaction {

    private final Connection connection;
    private final boolean originalAutoCommit;
    private boolean isCompleted;
    private final List<Savepoint> savepoints;

    /**
     * Creates a new transaction with the given connection.
     * Disables auto-commit mode on the connection.
     *
     * @param connection the JDBC connection to use for this transaction
     * @throws SQLException if a database access error occurs
     */
    public DbTransaction(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }

        this.connection = connection;
        this.originalAutoCommit = connection.getAutoCommit();
        this.isCompleted = false;
        this.savepoints = new ArrayList<>();

        // Begin transaction by disabling auto-commit
        connection.setAutoCommit(false);
    }

    @Override
    public void commit() throws SQLException {
        checkNotCompleted();

        try {
            connection.commit();
            isCompleted = true;
        } finally {
            restoreAutoCommit();
            clearSavepoints();
        }
    }

    @Override
    public void rollback() throws SQLException {
        checkNotCompleted();

        try {
            connection.rollback();
            isCompleted = true;
        } finally {
            restoreAutoCommit();
            clearSavepoints();
        }
    }

    /**
     * Rolls back to a specific savepoint.
     *
     * @param savepoint the savepoint to roll back to
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if the transaction is already completed
     */
    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        checkNotCompleted();
        connection.rollback(savepoint);
    }

    /**
     * Creates a savepoint within this transaction.
     *
     * @return the created Savepoint
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if the transaction is already completed
     */
    @Override
    public Savepoint createSavepoint() throws SQLException {
        checkNotCompleted();
        Savepoint savepoint = connection.setSavepoint();
        savepoints.add(savepoint);
        return savepoint;
    }

    /**
     * Creates a named savepoint within this transaction.
     *
     * @param name the name of the savepoint
     * @return the created Savepoint
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if the transaction is already completed
     */
    @Override
    public Savepoint createSavepoint(String name) throws SQLException {
        checkNotCompleted();
        Savepoint savepoint = connection.setSavepoint(name);
        savepoints.add(savepoint);
        return savepoint;
    }

    /**
     * Releases a savepoint, removing it from this transaction.
     *
     * @param savepoint the savepoint to release
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if the transaction is already completed
     */
    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        checkNotCompleted();
        connection.releaseSavepoint(savepoint);
        savepoints.remove(savepoint);
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public boolean isCompleted() {
        return isCompleted;
    }

    @Override
    public void close() throws SQLException {
        if (!isCompleted) {
            try {
                rollback();
            } catch (SQLException e) {
                // Log or handle rollback failure
                throw new SQLException("Failed to rollback transaction during close", e);
            }
        }
    }

    private void checkNotCompleted() {
        if (isCompleted) {
            throw new IllegalStateException("Transaction has already been completed");
        }
    }

    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException e) {
            // Log warning - this is a cleanup operation
            System.err.println("Warning: Failed to restore auto-commit setting: " + e.getMessage());
        }
    }

    private void clearSavepoints() {
        savepoints.clear();
    }
}