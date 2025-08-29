# Entity4j

A lightweight Java ORM inspired by Entity Framework (Java + Entity Framework = J-ENny)

Entity4j is a minimal, type-safe object relational mapper for Java. It lets you define entities with annotations, map them to database tables, and build queries with lambda expressions in a concise way. It also provides helpers to automatically create tables from annotated classes, with support for multiple database dialects including MySQL, PostgreSQL, SQL Server, and SQLite.

**Built and tested against Java 1.8**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
    - [Defining Entities](#defining-entities)
    - [Database Configuration](#database-configuration)
    - [Table Creation](#table-creation)
    - [Querying with Lambdas](#querying-with-lambdas)
- [Filters API](#filters-api)
- [Complex Query Example](#complex-query-example)
- [CRUD Operations](#crud-operations)
- [Debugging and SQL Output](#debugging-and-sql-output)
- [Extending DbContext](#extending-dbcontext)
- [Features](#features)
- [License](#license)

## Features

- Annotation-driven mapping
- Type-safe lambdas for filters and ordering
- CRUD helpers: insert, update, delete
- SQL preview with parameters
- Limit and offset for pagination
- Automatic camelCase to snake_case mapping
- Automatic table creation with `createTable` and `dropTableIfExists`
- Rich `@Column` annotation for type, length, precision, scale, and custom definitions
- `@NotMapped` support for transient fields
- DbContext can be extended with custom methods and configuration
- Multi-database support: MySQL, PostgreSQL, SQL Server, and SQLite
- Automatic dialect detection or explicit dialect configuration (recommended)
---

## Installation

### Gradle

Add the [JitPack](https://jitpack.io/#lovepigeons/Entity4j) repository:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

Then add the dependency:

```groovy
dependencies {
    implementation 'com.github.lovepigeons:Entity4j:v1.0.0'
}
```

### Maven

Add the [JitPack](https://jitpack.io/#lovepigeons/Entity4j) repository:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Then add the dependency:

```xml
<dependencies>
    <dependency>
        <groupId>com.github.lovepigeons</groupId>
        <artifactId>Entity4j</artifactId>
        <version>v1.0.0</version>
    </dependency>
</dependencies>
```

## Quick Start

### Defining Entities

```java
@Entity(table = "users")
public class User {
    @Id(auto = true)
    private Long id;
    
    @Column(name = "full_name", length = 100, nullable = false)
    private String name;
    
    @Column(precision = 5, scale = 2)
    private java.math.BigDecimal rating;
    
    private Boolean active;
    
    @NotMapped
    private String cachedDisplayName;
    
    // getters and setters...
}
```

### Database Configuration

Entity4j supports MySQL, PostgreSQL, SQL Server, and SQLite. It can auto-detect the database dialect from the connection, but explicitly setting the dialect is recommended for better performance and reliability:

```java
// Explicit dialect configuration (recommended)
try (Connection conn = DriverManager.getConnection(...);
     DbContext ctx = new DbContext(conn, DatabaseDialect.MYSQL)) {
    // Explicitly set to MySQL
}

// Auto-detection (Entity4j will detect dialect from connection) (not recommended)
try (Connection conn = DriverManager.getConnection(...);
     DbContext ctx = new DbContext(conn)) {
    // Entity4j automatically detects the database type
}

// Other supported dialects:
// DatabaseDialect.POSTGRESQL
// DatabaseDialect.SQLSERVER  
// DatabaseDialect.SQLITE
```

### Table Creation

This will create the table and insert the user.

```java
try (Connection conn = DriverManager.getConnection(...);
     DbContext ctx = new DbContext(conn)) {
    ctx.createTable(User.class);
    ctx.insert(new User("Ada Lovelace", 36, true));
}
```

### Querying with Lambdas

This example shwows updating, querying, and deleting.

```java
// Update
User ada = ctx.from(User.class)
              .filter(f -> f.equals(User::getName, "Ada Lovelace"))
              .first()
              .orElseThrow();
ada.setRating(new java.math.BigDecimal("4.95"));
ctx.update(ada);

// Query
List<User> results = ctx.from(User.class)
    .filter(f -> f.equals(User::getActive, true))
    .orderBy(User::getRating, false)
    .limit(5)
    .toList();

// Delete
ctx.delete(ada);
```

## Filters API

### Comparison Operators

| Method | SQL | Example |
|--------|-----|---------|
| `equals` | `=` | `f.equals(User::getName, "Ada Lovelace")` |
| `notEquals` | `<>` | `f.notEquals(User::getAge, 40)` |
| `greater` | `>` | `f.greater(User::getAge, 18)` |
| `greaterOrEquals` | `>=` | `f.greaterOrEquals(User::getAge, 21)` |
| `less` | `<` | `f.less(User::getAge, 65)` |
| `lessOrEquals` | `<=` | `f.lessOrEquals(User::getAge, 100)` |
| `like` | `LIKE` | `f.like(User::getName, "%Ada%")` |
| `in` | `IN (...)` | `f.in(User::getAge, List.of(18, 21, 25))` |

### Logical Connectors

- `and()` → `AND`
- `or()` → `OR`
- `open()` → `(`
- `close()` → `)`

## Complex Query Example

```java
List<User> advanced = ctx.from(User.class)
    .filter(f -> f.open()
                  .greaterOrEquals(User::getAge, 30)
                  .and()
                  .less(User::getAge, 60)
                  .close()
                  .or()
                  .open()
                  .equals(User::getActive, true)
                  .and()
                  .like(User::getName, "%Ada%")
                  .close())
    .orderBy(User::getRating, false)
    .limit(10)
    .toList();
```

**Generated SQL:**

```sql
SELECT * FROM users 
WHERE (age >= ? AND age < ?) OR (active = ? AND full_name LIKE ?)
ORDER BY rating DESC LIMIT 10
```

```
[Params] ?1=30, ?2=60, ?3=TRUE, ?4='%Ada%'
```

This finds users between ages 30 and 60 or active users whose names contain "Ada", ordered by rating descending.

## CRUD Operations

```java
ctx.insert(new User("Ada Lovelace", 36, true));
ctx.update(existingUser);
ctx.delete(existingUser);
```

## Debugging and SQL Output

```java
System.out.println(
    ctx.from(User.class)
       .filter(f -> f.equals(User::getName, "Ada Lovelace"))
       .toSqlWithParams()
);
```

## Extending DbContext

```java
public class MyDbContext extends DbContext {
    public MyDbContext(Connection conn) {
        super(conn);
    }
    
    // With explicit dialect (recommended)
    public MyDbContext(Connection conn, DatabaseDialect dialect) {
        super(conn, dialect);
    }

    public List<User> getActiveUsers() {
        return from(User.class)
            .filter(f -> f.equals(User::getActive, true))
            .toList();
    }
}
```

## Advanced Features

Example entities.

```java
@Entity(table = "users")
public class User {
    @Id(auto = true) private Long id;
    private String name;
    private String status;
    private LocalDate createdAt;
    // getters/setters
}

@Entity(table = "orders")
public class Order {
    @Id(auto = true) private Long id;
    private Long userId;
    private Double total;
    private LocalDateTime placedAt;
    // getters/setters
}
```

Multi-column ordering

```java
List<User> users = ctx.from(User.class)
    .orderBy(User::getStatus, true)          // ORDER BY status ASC
    .thenBy(User::getCreatedAt, false)       // , createdAt DESC
    .toList();
```

This produces SQL like:

```sql
SELECT * FROM users
ORDER BY status ASC, created_at DESC
```

2. Pagination with limit + offset

```java
List<User> page = ctx.from(User.class)
    .orderBy(User::getCreatedAt, false) // newest first
    .offset(20)                         // skip first 20
    .limit(10)                          // take next 10
    .toList();
```

SQL (Postgres/MySQL/SQLite dialects):

```sql
SELECT * FROM users
ORDER BY created_at DESC
LIMIT 10 OFFSET 20
```

SQL (SQL Server dialect):

```sql
SELECT * FROM users
ORDER BY created_at DESC
OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY
```

### 3. Join with filtering on joined table

```java
List<User> richActiveUsers = ctx.from(User.class).as("u")
    .leftJoin(Order.class, "o", on -> 
        on.eq(User::getId, Order::getUserId))       // ON u.id = o.user_id
    .filter(f -> f.equals(User::getStatus, "ACTIVE")
                 .and()
                 .greater(Order.class, Order::getTotal, 1000.0)) // o.total > 1000
    .orderBy(User::getName, true)
    .thenBy(Order.class, Order::getPlacedAt, false)
    .limit(50)
    .toList();
```

Output (Postgres/MySQL/SQLite style):

```sql
SELECT u.*
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.status = ? AND o.total > ?
ORDER BY u.name ASC, o.placed_at DESC
LIMIT 50
```

Params would be `[?1=ACTIVE, ?2=1000.0]`.

## License

Entity4j is released under the GNU General Public License v3.0.
