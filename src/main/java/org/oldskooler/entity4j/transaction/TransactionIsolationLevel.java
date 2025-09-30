package org.oldskooler.entity4j.transaction;

import java.sql.Connection;

/**
 * Enumeration of standard SQL transaction isolation levels.
 */
public enum TransactionIsolationLevel {

    /**
     * Allows dirty reads, non-repeatable reads, and phantom reads.
     * The lowest isolation level.
     */
    READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),

    /**
     * Prevents dirty reads; allows non-repeatable reads and phantom reads.
     */
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),

    /**
     * Prevents dirty reads and non-repeatable reads; allows phantom reads.
     */
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),

    /**
     * Prevents dirty reads, non-repeatable reads, and phantom reads.
     * The highest isolation level.
     */
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

    private final int jdbcLevel;

    TransactionIsolationLevel(int jdbcLevel) {
        this.jdbcLevel = jdbcLevel;
    }

    /**
     * Gets the JDBC constant value for this isolation level.
     *
     * @return the JDBC transaction isolation level constant
     */
    public int getJdbcLevel() {
        return jdbcLevel;
    }

    /**
     * Converts a JDBC isolation level constant to a TransactionIsolationLevel enum.
     *
     * @param jdbcLevel the JDBC isolation level constant
     * @return the corresponding TransactionIsolationLevel
     * @throws IllegalArgumentException if the JDBC level is not recognized
     */
    public static TransactionIsolationLevel fromJdbcLevel(int jdbcLevel) {
        for (TransactionIsolationLevel level : values()) {
            if (level.jdbcLevel == jdbcLevel) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown JDBC isolation level: " + jdbcLevel);
    }
}