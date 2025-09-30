package org.oldskooler.entity4j.transaction;

/**
 * Options for configuring transaction behavior.
 */
public class TransactionOptions {

    private final TransactionIsolationLevel isolationLevel;
    private final boolean readOnly;
    private final Integer timeoutSeconds;

    /**
     * Creates transaction options with default settings.
     * Default: READ_COMMITTED isolation, read-write, no timeout.
     */
    public TransactionOptions() {
        this(TransactionIsolationLevel.READ_COMMITTED, false, null);
    }

    /**
     * Creates transaction options with specified settings.
     *
     * @param isolationLevel the transaction isolation level
     * @param readOnly whether the transaction is read-only
     * @param timeoutSeconds timeout in seconds, or null for no timeout
     */
    public TransactionOptions(TransactionIsolationLevel isolationLevel,
                              boolean readOnly,
                              Integer timeoutSeconds) {
        this.isolationLevel = isolationLevel;
        this.readOnly = readOnly;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Gets the transaction isolation level.
     *
     * @return the isolation level
     */
    public TransactionIsolationLevel getIsolationLevel() {
        return isolationLevel;
    }

    /**
     * Checks if the transaction is read-only.
     *
     * @return true if read-only, false otherwise
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Gets the timeout in seconds.
     *
     * @return the timeout in seconds, or null if no timeout
     */
    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Creates a builder for constructing TransactionOptions.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for TransactionOptions.
     */
    public static class Builder {
        private TransactionIsolationLevel isolationLevel = TransactionIsolationLevel.READ_COMMITTED;
        private boolean readOnly = false;
        private Integer timeoutSeconds = null;

        /**
         * Sets the isolation level.
         *
         * @param isolationLevel the isolation level
         * @return this builder
         */
        public Builder isolationLevel(TransactionIsolationLevel isolationLevel) {
            this.isolationLevel = isolationLevel;
            return this;
        }

        /**
         * Sets whether the transaction is read-only.
         *
         * @param readOnly true for read-only
         * @return this builder
         */
        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        /**
         * Sets the timeout in seconds.
         *
         * @param timeoutSeconds the timeout in seconds
         * @return this builder
         */
        public Builder timeout(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        /**
         * Builds the TransactionOptions.
         *
         * @return a new TransactionOptions instance
         */
        public TransactionOptions build() {
            return new TransactionOptions(isolationLevel, readOnly, timeoutSeconds);
        }
    }
}