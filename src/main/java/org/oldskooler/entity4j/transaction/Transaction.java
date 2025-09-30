package org.oldskooler.entity4j.transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

/**
 * Represents a database transaction abstraction.
 * Provides methods for transaction lifecycle management,
 * savepoint handling, and connection access.
 */
public interface Transaction extends AutoCloseable {

    /**
     * Commits the current transaction.
     *
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if the transaction is already completed
     */
    void commit() throws SQLException;

    /**
     * Rolls back the current transaction.
     *
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if the transaction is already completed
     */
    void rollback() throws SQLException;

    /**
     * Rolls back to a specific savepoint.
     *
     * @param savepoint the savepoint to roll back to
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if the transaction is already completed
     */
    void rollback(Savepoint savepoint) throws SQLException;

    /**
     * Creates an unnamed savepoint within this transaction.
     *
     * @return the created Savepoint
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if the transaction is already completed
     */
    Savepoint createSavepoint() throws SQLException;

    /**
     * Creates a named savepoint within this transaction.
     *
     * @param name the name of the savepoint
     * @return the created Savepoint
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if the transaction is already completed
     */
    Savepoint createSavepoint(String name) throws SQLException;

    /**
     * Releases a previously created savepoint.
     *
     * @param savepoint the savepoint to release
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if the transaction is already completed
     */
    void releaseSavepoint(Savepoint savepoint) throws SQLException;

    /**
     * Returns the underlying JDBC connection associated with this transaction.
     *
     * @return the JDBC connection
     */
    Connection getConnection();

    /**
     * Checks whether this transaction has been completed
     * (either committed or rolled back).
     *
     * @return true if the transaction is completed, false otherwise
     */
    boolean isCompleted();

    /**
     * Closes this transaction, rolling back if not already completed.
     *
     * @throws SQLException if a database access error occurs
     */
    @Override
    void close() throws SQLException;
}
