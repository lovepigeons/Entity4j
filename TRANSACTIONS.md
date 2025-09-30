# Transaction Support for Entity4j

This document describes the transaction support added to Entity4j, providing ACID guarantees for database operations.

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Core Concepts](#core-concepts)
- [Usage Patterns](#usage-patterns)
- [Transaction Isolation Levels](#transaction-isolation-levels)
- [Savepoints](#savepoints)
- [Best Practices](#best-practices)
- [Error Handling](#error-handling)

## Overview

Entity4j now includes comprehensive transaction support with:

- **Explicit transactions** with commit/rollback control
- **Automatic transactions** using try-with-resources or lambda expressions
- **Transaction isolation levels** (READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE)
- **Savepoints** for nested transaction patterns
- **Read-only transactions** for performance optimization
- **Transaction options** for fine-grained control

## Quick Start

### Basic Transaction

```java
try (Connection conn = DriverManager.getConnection(...);
     DbContext ctx = new DbContext(conn, DatabaseDialect.MYSQL)) {
    
    Transaction tx = ctx.beginTransaction();
    
    try {
        User user = new User("Alice", 25, true);
        ctx.insert(user);
        
        user.setAge(26);
        ctx.update(user);
        
        tx.commit();
    } catch (Exception e) {
        tx.rollback();
        throw e;
    }
}
```

### Automatic Transaction (Recommended)

```java
ctx.executeInTransaction(context -> {
    User user = new User("Bob", 30, true);
    context.insert(user);
    // Automatically commits on success, rolls back on exception
});
```

## Core Concepts

### Transaction Interface

The `Transaction` interface provides methods for controlling transaction lifecycle:

- `commit()` - Commits all changes to the database
- `rollback()` - Discards all changes made in the transaction
- `getConnection()` - Returns the underlying JDBC connection
- `isCompleted()` - Checks if the transaction has been committed or rolled back
- `close()` - Automatically rolls back if not committed (for try-with-resources)

### Transaction States

A transaction can be in one of three states:

1. **Active** - Transaction is open and operations can be performed
2. **Committed** - Changes have been permanently saved to the database
3. **Rolled Back** - Changes have been discarded

Once a transaction is committed or rolled back, it cannot be reused.

## Usage Patterns

### Pattern 1: Explicit Transaction Control

Use when you need fine-grained control over the transaction lifecycle:

```java
Transaction tx = ctx.beginTransaction();

try {
    // Perform operations
    ctx.insert(user);
    ctx.update(order);
    
    // Explicitly commit
    tx.commit();
    
} catch (Exception e) {
    // Explicitly rollback on error
    tx.rollback();
    throw e;
}
```

### Pattern 2: Try-with-Resources

Automatically rolls back if an exception occurs:

```java
try (Transaction tx = ctx.beginTransaction()) {
    ctx.insert(user);
    ctx.update(order);
    
    tx.commit(); // Must call explicitly
    
    // If commit() is not called, transaction rolls back automatically
}
```

### Pattern 3: Lambda-based Transactions (Recommended)

The simplest and safest approach:

```java
// With return value
User user = ctx.executeInTransaction(context -> {
    User newUser = new User("Charlie", 28, true);
    context.insert(newUser);
    return newUser;
});

// Without return value
ctx.executeInTransaction(context -> {
    context.insert(user1);
    context.insert(user2);
});
```

### Pattern 4: Custom Transaction Options

Configure isolation level, read-only mode, and timeouts:

```java
TransactionOptions options = TransactionOptions.builder()
    .isolationLevel(TransactionIsolationLevel.SERIALIZABLE)
    .readOnly(false)
    .timeout(30)
    .build();

ctx.executeInTransaction(options, context -> {
    // Critical operations with highest isolation
});
```

### Pattern 5: Read-Only Transactions

Optimise read-only operations:

```java
List<User> users = ctx.executeInReadOnlyTransaction(context -> {
    return context.from(User.class)
        .filter(f -> f.equals(User::getActive, true))
        .toList();
});
```

## Transaction Isolation Levels

Transaction isolation levels control how concurrent transactions interact:

### READ_UNCOMMITTED
- **Allows**: Dirty reads, non-repeatable reads, phantom reads
- **Use when**: Maximum concurrency is needed, accuracy is less critical
- **Risk**: May read uncommitted changes from other transactions

```java
ctx.beginTransaction(TransactionIsolationLevel.READ_UNCOMMITTED);
```

### READ_COMMITTED (Default)
- **Prevents**: Dirty reads
- **Allows**: Non-repeatable reads, phantom reads
- **Use when**: Standard OLTP operations
- **Behavior**: Only reads committed data

```java
ctx.beginTransaction(TransactionIsolationLevel.READ_COMMITTED);
```

### REPEATABLE_READ
- **Prevents**: Dirty reads, non-repeatable reads
- **Allows**: Phantom reads
- **Use when**: Consistency within a transaction is important
- **Behavior**: Same query returns same results throughout transaction

```java
ctx.beginTransaction(TransactionIsolationLevel.REPEATABLE_READ);
```

### SERIALIZABLE
- **Prevents**: Dirty reads, non-repeatable reads, phantom reads
- **Use when**: Absolute consistency is required
- **Behavior**: Transactions execute as if serialized (one at a time)
- **Risk**: Highest chance of deadlocks, lowest concurrency

```java
ctx.executeInSerializableTransaction(context -> {
    // Critical operations
});
```

### Isolation Level Comparison

| Isolation Level | Dirty Reads | Non-Repeatable Reads | Phantom Reads | Concurrency |
|----------------|-------------|---------------------|---------------|-------------|
| READ_UNCOMMITTED | Yes | Yes | Yes | Highest |
| READ_COMMITTED | No | Yes | Yes | High |
| REPEATABLE_READ | No | No | Yes | Medium |
| SERIALIZABLE | No | No | No | Lowest |

## Savepoints

Savepoints allow you to create partial rollback points within a transaction:

### Creating Savepoints

```java
DbTransaction tx = (DbTransaction) ctx.beginTransaction();

try {
    // Operation 1
    ctx.insert(user1);
    
    // Create savepoint
    Savepoint sp1 = tx.createSavepoint("afterUser1");
    
    try {
        // Operation 2 (risky)
        ctx.insert(user2);
        
    } catch (Exception e) {
        // Rollback to savepoint - user1 is kept
        tx.rollback(sp1);
    }
    
    // Commit (user1 is saved)
    tx.commit();
    
} catch (Exception e) {
    tx.rollback();
}
```

### Named vs Unnamed Savepoints

```java
// Unnamed savepoint
Savepoint sp1 = tx.createSavepoint();

// Named savepoint (easier to debug)
Savepoint sp2 = tx.createSavepoint("checkpoint");
```

### Releasing Savepoints

Release a savepoint when you no longer need it:

```java
tx.releaseSavepoint(sp1);
```

This frees up resources but cannot undo the operations after the savepoint.

## Best Practices

### 1. Keep Transactions Short

```java
// Bad: Long-running transaction
ctx.executeInTransaction(context -> {
    List<User> users = context.from(User.class).toList();
    
    for (User user : users) {
        // Expensive operation
        processUser(user);
        Thread.sleep(1000);
        context.update(user);
    }
});

// Good: Short transaction
List<User> users = ctx.from(User.class).toList();

for (User user : users) {
    processUser(user);
    
    ctx.executeInTransaction(context -> {
        context.update(user);
    });
}
```

### 2. Use Appropriate Isolation Levels

```java
// Bad: Using SERIALIZABLE for everything
ctx.executeInSerializableTransaction(context -> {
    // Simple read operation
    return context.from(User.class).toList();
});

// Good: Use READ_COMMITTED or read-only for reads
ctx.executeInReadOnlyTransaction(context -> {
    return context.from(User.class).toList();
});
```

### 3. Always Handle Exceptions

```java
// Bad: Swallowing exceptions
try {
    ctx.executeInTransaction(context -> {
        context.insert(user);
    });
} catch (Exception e) {
    // Silent failure
}

// Good: Proper exception handling
try {
    ctx.executeInTransaction(context -> {
        context.insert(user);
    });
} catch (SQLException e) {
    logger.error("Failed to insert user", e);
    throw new RuntimeException("User creation failed", e);
}
```

### 4. Check for Active Transactions

```java
// Good: Avoid nested transactions
if (!ctx.hasActiveTransaction()) {
    ctx.executeInTransaction(context -> {
        // Operations
    });
} else {
    // Use existing transaction
    ctx.insert(user);
}
```

### 5. Use Try-with-Resources

```java
// Good: Automatic cleanup
try (Transaction tx = ctx.beginTransaction()) {
    ctx.insert(user);
    tx.commit();
}
// Transaction automatically rolled back if commit() not called
```

### 6. Implement Retry Logic for Conflicts

```java
int maxRetries = 3;
for (int i = 0; i < maxRetries; i++) {
    try {
        ctx.executeInSerializableTransaction(context -> {
            // Operations
        });
        break; // Success
        
    } catch (SQLException e) {
        if (e.getSQLState().equals("40001") && i < maxRetries - 1) {
            Thread.sleep(100 * (i + 1)); // Exponential backoff
        } else {
            throw e;
        }
    }
}
```

## Error Handling

### Common SQLException States

- `40001` - Serialization failure (deadlock)
- `23000` - Integrity constraint violation
- `08S01` - Communication link failure

### Handling Rollback Failures

```java
Transaction tx = ctx.beginTransaction();

try {
    ctx.insert(user);
    tx.commit();
    
} catch (Exception e) {
    try {
        tx.rollback();
    } catch (SQLException rollbackEx) {
        // Log both exceptions
        logger.error("Original error", e);
        logger.error("Rollback failed", rollbackEx);
        throw new RuntimeException("Transaction failed and rollback failed", e);
    }
    throw e;
}
```

### Detecting Deadlocks

```java
try {
    ctx.executeInTransaction(context -> {
        // Operations
    });
} catch (SQLException e) {
    if (e.getSQLState().equals("40001") || 
        e.getMessage().contains("deadlock")) {
        logger.warn("Deadlock detected, consider retry");
        // Implement retry logic
    }
    throw e;
}
```

## Performance Considerations

### Transaction Overhead

Transactions have overhead. For single operations, they may not be necessary:

```java
// For single insert
ctx.insert(user); // Uses auto-commit

// For multiple related operations
ctx.executeInTransaction(context -> {
    context.insert(user);
    context.insert(order);
});
```

### Read-Only Optimization

```java
// Read-only transactions can be optimized by the database
TransactionOptions options = TransactionOptions.builder()
    .readOnly(true)
    .build();

ctx.executeInTransaction(options, context -> {
    // Complex read query
});
```

### Batch Operations

```java
ctx.executeInTransaction(context -> {
    for (User user : users) {
        context.insert(user);
    }
    // All inserts in one transaction
});
```

## Advanced Features

### Checking Transaction Completion

```java
Transaction tx = ctx.beginTransaction();

if (!tx.isCompleted()) {
    // Transaction is still active
}

tx.commit();

if (tx.isCompleted()) {
    // Transaction has been completed
}
```

### Getting Current Transaction

```java
Transaction current = ctx.getCurrentTransaction();

if (current != null) {
    System.out.println("Transaction is active");
}
```

### Connection Access

```java
Transaction tx = ctx.beginTransaction();
Connection conn = tx.getConnection();

// Use connection directly if needed
PreparedStatement ps = conn.prepareStatement("...");
```

## Migration Guide

If you have existing Entity4j code, here's how to add transactions:

### Before (Auto-commit)

```java
ctx.insert(user);
ctx.update(order);
```

### After (Transactional)

```java
ctx.executeInTransaction(context -> {
    context.insert(user);
    context.update(order);
});
```

No other changes needed! The API remains backward compatible.