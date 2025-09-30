package org.oldskooler.entity4j;

import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.dialect.SqlDialectType;
import org.oldskooler.entity4j.mapping.MappingRegistry;
import org.oldskooler.entity4j.mapping.ModelBuilder;
import org.oldskooler.entity4j.mapping.TableMeta;
import org.oldskooler.entity4j.operations.DbBatchOperations;
import org.oldskooler.entity4j.operations.DbCrudOperations;
import org.oldskooler.entity4j.operations.DbDdlOperations;
import org.oldskooler.entity4j.operations.DbQueryExecutor;
import org.oldskooler.entity4j.transaction.*;
import org.oldskooler.entity4j.util.*;

import java.sql.*;
import java.util.*;

/**
 * Abstract base class for database context implementations that provides core database operations
 * including CRUD, DDL, batch operations, and query execution capabilities.
 * <p>
 * This class serves as the main entry point for interacting with the database and managing
 * entity mappings. It supports automatic SQL dialect detection and provides a fluent API
 * for database operations.
 * </p>
 * <p>
 * Implementations must override the {@link #onModelCreating(ModelBuilder)} method to configure
 * entity mappings and relationships.
 * </p>
 *
 * @author Entity4J Framework
 * @since 1.0.0
 */
public abstract class IDbContext implements AutoCloseable {
    private final Connection connection;
    private Transaction currentTransaction;
    private final SqlDialect dialect;

    /** Registry for entity-to-table mappings */
    private final MappingRegistry mappingRegistry = new MappingRegistry();

    /** Flag indicating whether the model has been built */
    private boolean modelBuilt = false;

    /**
     * Constructs a new database context with explicit SQL dialect specification via enum.
     *
     * @param connection the JDBC connection to use
     * @param dialectType the SQL dialect type to use
     * @throws NullPointerException if connection is null
     */
    public IDbContext(Connection connection, SqlDialectType dialectType) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.dialect = dialectType.createDialect();
    }

    /**
     * Constructs a new database context with an explicit SQL dialect instance.
     *
     * @param connection the JDBC connection to use
     * @param dialect the SQL dialect instance to use
     * @throws NullPointerException if connection or dialect is null
     */
    public IDbContext(Connection connection, SqlDialect dialect) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    /**
     * Constructs a new database context with automatic SQL dialect detection from JDBC metadata.
     *
     * @param connection the JDBC connection to use
     * @throws SQLException if unable to detect the dialect from connection metadata
     * @throws NullPointerException if connection is null
     */
    public IDbContext(Connection connection) throws SQLException {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.dialect = DialectDetector.detectDialect(connection);
    }

    /**
     * Override this method to configure entity mappings and relationships.
     * This method is called automatically when the model is first needed.
     *
     * @param model the model builder to configure mappings
     */
    public abstract void onModelCreating(ModelBuilder model);

    /**
     * Ensures the entity model has been built by calling {@link #onModelCreating(ModelBuilder)}
     * if it hasn't been called yet.
     */
    private void ensureModelBuilt() {
        if (!modelBuilt) {
            onModelCreating(new ModelBuilder(mappingRegistry));
            modelBuilt = true;
        }
    }

    /**
     * Begins a new database transaction.
     * If a transaction is already active, throws IllegalStateException.
     *
     * @return a new Transaction object
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if a transaction is already active
     */
    public Transaction beginTransaction() throws SQLException {
        if (currentTransaction != null && !currentTransaction.isCompleted()) {
            throw new IllegalStateException("A transaction is already active. Commit or rollback the current transaction before starting a new one.");
        }

        currentTransaction = new DbTransaction(connection);
        return currentTransaction;
    }

    /**
     * Gets the current active transaction, or null if no transaction is active.
     *
     * @return the current Transaction or null
     */
    public Transaction getCurrentTransaction() {
        if (currentTransaction != null && currentTransaction.isCompleted()) {
            currentTransaction = null;
        }
        return currentTransaction;
    }

    /**
     * Checks if a transaction is currently active.
     *
     * @return true if a transaction is active, false otherwise
     */
    public boolean hasActiveTransaction() {
        return getCurrentTransaction() != null;
    }

    /**
     * Executes a function within a transaction, automatically committing on success
     * or rolling back on failure.
     *
     * @param action the action to perform within the transaction
     * @param <T> the return type of the action
     * @return the result of the action
     * @throws Exception if the action throws an exception
     */
    public <T> T executeInTransaction(TransactionAction<T> action) throws Exception {
        Transaction transaction = beginTransaction();
        try {
            T result = action.execute(this);
            transaction.commit();
            return result;
        } catch (Exception e) {
            try {
                transaction.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            throw e;
        } finally {
            if (!transaction.isCompleted()) {
                try {
                    transaction.close();
                } catch (SQLException closeEx) {
                    // Log or handle close exception
                }
            }
        }
    }

    /**
     * Executes a function within a transaction without a return value,
     * automatically committing on success or rolling back on failure.
     *
     * @param action the action to perform within the transaction
     * @throws Exception if the action throws an exception
     */
    public void executeInTransaction(VoidTransactionAction action) throws Exception {
        executeInTransaction(ctx -> {
            action.execute(ctx);
            return null;
        });
    }

    /**
     * Begins a new database transaction with the specified options.
     *
     * @param options the transaction options
     * @return a new Transaction object
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if a transaction is already active
     */
    public Transaction beginTransaction(TransactionOptions options) throws SQLException {
        if (currentTransaction != null && !currentTransaction.isCompleted()) {
            throw new IllegalStateException("A transaction is already active. Commit or rollback the current transaction before starting a new one.");
        }

        currentTransaction = new DbTransactionEnhanced(connection, options);
        return currentTransaction;
    }

    /**
     * Begins a new database transaction with the specified isolation level.
     *
     * @param isolationLevel the transaction isolation level
     * @return a new Transaction object
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if a transaction is already active
     */
    public Transaction beginTransaction(TransactionIsolationLevel isolationLevel) throws SQLException {
        TransactionOptions options = TransactionOptions.builder()
                .isolationLevel(isolationLevel)
                .build();
        return beginTransaction(options);
    }

    /**
     * Executes a function within a transaction with specified options,
     * automatically committing on success or rolling back on failure.
     *
     * @param options the transaction options
     * @param action the action to perform within the transaction
     * @param <T> the return type of the action
     * @return the result of the action
     * @throws Exception if the action throws an exception
     */
    public <T> T executeInTransaction(TransactionOptions options, TransactionAction<T> action) throws Exception {
        Transaction transaction = beginTransaction(options);
        try {
            T result = action.execute(this);
            transaction.commit();
            return result;
        } catch (Exception e) {
            try {
                transaction.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            throw e;
        } finally {
            if (!transaction.isCompleted()) {
                try {
                    transaction.close();
                } catch (SQLException closeEx) {
                    // Log or handle close exception
                }
            }
        }
    }

    /**
     * Executes an action within a transaction with specified options,
     * automatically committing on success or rolling back on failure.
     *
     * @param options the transaction options
     * @param action the action to perform within the transaction
     * @throws Exception if the action throws an exception
     */
    public void executeInTransaction(TransactionOptions options, VoidTransactionAction action) throws Exception {
        executeInTransaction(options, ctx -> {
            action.execute(ctx);
            return null;
        });
    }

    /**
     * Executes a function within a serializable transaction (highest isolation level).
     *
     * @param action the action to perform within the transaction
     * @param <T> the return type of the action
     * @return the result of the action
     * @throws Exception if the action throws an exception
     */
    public <T> T executeInSerializableTransaction(TransactionAction<T> action) throws Exception {
        TransactionOptions options = TransactionOptions.builder()
                .isolationLevel(TransactionIsolationLevel.SERIALIZABLE)
                .build();
        return executeInTransaction(options, action);
    }

    /**
     * Executes a function within a read-only transaction.
     * This can provide performance benefits for queries.
     *
     * @param action the action to perform within the transaction
     * @param <T> the return type of the action
     * @return the result of the action
     * @throws Exception if the action throws an exception
     */
    public <T> T executeInReadOnlyTransaction(TransactionAction<T> action) throws Exception {
        TransactionOptions options = TransactionOptions.builder()
                .readOnly(true)
                .build();
        return executeInTransaction(options, action);
    }

    /**
     * Functional interface for transaction actions that return a value.
     *
     * @param <T> the return type
     */
    @FunctionalInterface
    public interface TransactionAction<T> {
        T execute(IDbContext context) throws Exception;
    }

    /**
     * Functional interface for transaction actions that don't return a value.
     */
    @FunctionalInterface
    public interface VoidTransactionAction {
        void execute(IDbContext context) throws Exception;
    }

    /**
     * Creates a new query builder for the specified entity type.
     *
     * @param <T> the entity type
     * @param type the class of the entity to query
     * @return a new Query instance for the specified type
     */
    public <T> Query<T> from(Class<T> type) {
        return new Query<>(this, TableMeta.of(type, mappingRegistry));
    }

    // DDL Operations

    /**
     * Generates SQL for creating a table for the specified entity type.
     * Equivalent to calling {@link #createTableSql(Class, boolean)} with {@code ifNotExists = true}.
     *
     * @param <T> the entity type
     * @param type the class of the entity
     * @return the CREATE TABLE SQL statement
     */
    public <T> String createTableSql(Class<T> type) {
        return createTableSql(type, true);
    }

    /**
     * Generates SQL for creating a table for the specified entity type.
     *
     * @param <T> the entity type
     * @param type the class of the entity
     * @param ifNotExists whether to include IF NOT EXISTS clause
     * @return the CREATE TABLE SQL statement
     */
    public <T> String createTableSql(Class<T> type, boolean ifNotExists) {
        return getDdlOperations().createTableSql(type, ifNotExists);
    }

    /**
     * Generates SQL for dropping a table for the specified entity type.
     * Equivalent to calling {@link #dropTableSql(Class, boolean)} with {@code ifExists = true}.
     *
     * @param <T> the entity type
     * @param type the class of the entity
     * @return the DROP TABLE SQL statement
     */
    public <T> String dropTableSql(Class<T> type) {
        return dropTableSql(type, true);
    }

    /**
     * Generates SQL for dropping a table for the specified entity type.
     *
     * @param <T> the entity type
     * @param type the class of the entity
     * @param ifExists whether to include IF EXISTS clause
     * @return the DROP TABLE SQL statement
     */
    public <T> String dropTableSql(Class<T> type, boolean ifExists) {
        return getDdlOperations().dropTableSql(type, ifExists);
    }

    /**
     * Creates a table for the specified entity type in the database.
     *
     * @param <T> the entity type
     * @param type the class of the entity
     * @return the number of statements executed (typically 1)
     */
    public <T> int createTable(Class<T> type) {
        return getDdlOperations().createTable(type);
    }

    /**
     * Creates tables for multiple entity types in the database.
     *
     * @param types the classes of the entities
     * @return the total number of statements executed
     */
    public int createTables(Class<?>... types) {
        return getDdlOperations().createTables(types);
    }

    /**
     * Drops the table for the specified entity type if it exists.
     *
     * @param <T> the entity type
     * @param type the class of the entity
     * @return the number of statements executed (typically 1 if table existed, 0 otherwise)
     */
    public <T> int dropTableIfExists(Class<T> type) {
        return getDdlOperations().dropTableIfExists(type);
    }

    // CRUD Operations

    /**
     * Inserts a single entity into the database.
     *
     * @param <T> the entity type
     * @param entity the entity to insert
     * @return the number of rows affected (typically 1)
     */
    public <T> int insert(T entity) {
        return getCrudOperations().insert(entity);
    }

    /**
     * Updates a single entity in the database based on its primary key.
     *
     * @param <T> the entity type
     * @param entity the entity to update
     * @return the number of rows affected (typically 1)
     */
    public <T> int update(T entity) {
        return getCrudOperations().update(entity);
    }

    /**
     * Deletes a single entity from the database based on its primary key.
     *
     * @param <T> the entity type
     * @param entity the entity to delete
     * @return the number of rows affected (typically 1)
     */
    public <T> int delete(T entity) {
        return getCrudOperations().delete(entity);
    }

    // Batch Operations

    /**
     * Inserts multiple entities into the database in a batch operation.
     * This is more efficient than calling {@link #insert(Object)} multiple times.
     *
     * @param <T> the entity type
     * @param entities the collection of entities to insert
     * @return the total number of rows affected
     */
    public <T> int insertAll(Collection<T> entities) {
        return getBatchOperations().insertAll(entities);
    }

    /**
     * Updates multiple entities in the database in a batch operation.
     * This is more efficient than calling {@link #update(Object)} multiple times.
     *
     * @param <T> the entity type
     * @param entities the collection of entities to update
     * @return the total number of rows affected
     */
    public <T> int updateAll(Collection<T> entities) {
        return getBatchOperations().updateAll(entities);
    }

    /**
     * Deletes multiple entities from the database in a batch operation.
     * This is more efficient than calling {@link #delete(Object)} multiple times.
     *
     * @param <T> the entity type
     * @param entities the collection of entities to delete
     * @return the total number of rows affected
     */
    public <T> int deleteAll(Collection<T> entities) {
        return getBatchOperations().deleteAll(entities);
    }

    // Query execution helpers

    /**
     * Executes a SQL query and maps the results to entity instances.
     * This method is used internally by the Query builder.
     *
     * @param <T> the entity type
     * @param m the table metadata for the entity
     * @param sql the SQL query to execute
     * @param params the parameters for the query
     * @return a list of entity instances
     */
    <T> List<T> executeQuery(TableMeta<T> m, String sql, List<Object> params) {
        return getQueryExecutor().executeQuery(m, sql, params);
    }

    /**
     * Executes a SQL query and returns the results as maps.
     * This method is used for queries that don't map to specific entity types.
     *
     * @param sql the SQL query to execute
     * @param params the parameters for the query
     * @return a list of maps containing column name-value pairs
     */
    List<Map<String, Object>> executeQueryMap(String sql, List<Object> params) {
        return getQueryExecutor().executeQueryMap(sql, params);
    }

    /**
     * Closes the underlying database connection.
     * This method is called automatically when using try-with-resources.
     *
     * @throws RuntimeException if a SQLException occurs while closing the connection
     */
    @Override
    public void close() throws RuntimeException {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Package-private accessors for internal components

    /**
     * Quotes an identifier using the current SQL dialect's quoting rules.
     * This method is used internally for generating SQL statements.
     *
     * @param ident the identifier to quote
     * @return the quoted identifier
     */
    String q(String ident) {
        return dialect.q(ident);
    }

    /**
     * Returns the underlying JDBC connection.
     * This method provides access to the connection for internal operations.
     *
     * @return the JDBC connection
     */
    public Connection conn() {
        return connection;
    }

    /**
     * Returns the SQL dialect instance.
     * This method provides access to dialect-specific functionality.
     *
     * @return the SQL dialect
     */
    public SqlDialect dialect() {
        return dialect;
    }

    /**
     * Returns the mapping registry containing entity-to-table mappings.
     * This method provides access to the mapping configuration.
     *
     * @return the mapping registry
     */
    public MappingRegistry mappingRegistry() {
        return mappingRegistry;
    }

    /**
     * Ensures the entity model has been built.
     * This method is used internally to trigger model building when needed.
     */
    public void ensureModelBuiltInternal() {
        ensureModelBuilt();
    }

    // Lazy-loaded operation handlers

    /** Lazy-loaded DDL operations handler */
    private DbDdlOperations ddlOperations;

    /** Lazy-loaded CRUD operations handler */
    private DbCrudOperations crudOperations;

    /** Lazy-loaded batch operations handler */
    private DbBatchOperations batchOperations;

    /** Lazy-loaded query executor */
    private DbQueryExecutor queryExecutor;

    /**
     * Returns the DDL operations handler, creating it if necessary.
     *
     * @return the DDL operations handler
     */
    private DbDdlOperations getDdlOperations() {
        if (ddlOperations == null) {
            ddlOperations = new DbDdlOperations(this);
        }
        return ddlOperations;
    }

    /**
     * Returns the CRUD operations handler, creating it if necessary.
     *
     * @return the CRUD operations handler
     */
    private DbCrudOperations getCrudOperations() {
        if (crudOperations == null) {
            crudOperations = new DbCrudOperations(this);
        }
        return crudOperations;
    }

    /**
     * Returns the batch operations handler, creating it if necessary.
     *
     * @return the batch operations handler
     */
    private DbBatchOperations getBatchOperations() {
        if (batchOperations == null) {
            batchOperations = new DbBatchOperations(this);
        }
        return batchOperations;
    }

    /**
     * Returns the query executor, creating it if necessary.
     *
     * @return the query executor
     */
    private DbQueryExecutor getQueryExecutor() {
        if (queryExecutor == null) {
            queryExecutor = new DbQueryExecutor(this);
        }
        return queryExecutor;
    }
}