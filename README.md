# Entity4j

A lightweight Java ORM inspired by Entity Framework (Entity Framework for Java = Entity4j)

Entity4j is a minimal, type-safe object relational mapper for Java. It lets you define entities with annotations, map them to database tables, and build queries with lambda expressions in a concise way. It also provides helpers to automatically create tables from annotated classes, with support for multiple database dialects including MySQL, PostgreSQL, SQL Server, and SQLite.

**Built and tested against Java 1.8**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

## Table of Contents

- [Quick Start](#quick-start)
    - [Defining Entities](#defining-entities)
    - [Database Configuration](#database-configuration)
    - [Table Creation](#table-creation)
    - [Querying with Lambdas](#querying-with-lambdas)
- [Fluent Mappings](#fluent-mappings)
- [Filters API](#filters-api)
- [Complex Query Example](#complex-query-example)
- [Column Selection](#column-selection)
    - [Basic Column Selection](#basic-column-selection)
    - [Selecting into Custom Types](#selecting-into-custom-types)
    - [Using toMapList()](#using-tomaplist)
- [CRUD Operations](#crud-operations)
- [Debugging and SQL Output](#debugging-and-sql-output)
- [Advanced Features](#advanced-features)
- [License](#license)

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
    implementation 'com.github.lovepigeons:Entity4j:v1.0.4'
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
        <version>v1.0.4</version>
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

## Fluent Mappings

Entity4j supports fluent mappings like below, first you must extend ``DbContext``. The the basic mapping first argument is the name of the field, the second argument is column name, or else you can add complexity by using ``column(...)`` seen below.

Fluent mappings will **always** take priority over annotations, if both are mapped.

```java
public class UsersDbContext extends DbContext {
    public UserDbContext(Connection connection, SqlDialectType dialectType) {
        super(connection, dialectType);
    }

    @Override
    protected void onModelCreating(ModelBuilder model) {
        model.entity(User.class)
            .toTable("users")
            .hasId("id", true) // auto-generated PK
    
            // basic mapping for defaults
            .map("active", "is_active") // or even just: map("active")
    
            // advanced mapping
            .column("name", c -> c
                    .name("full_name")
                    .length(100)
                    .nullable(false))
    
            .column("rating", c -> c
                    .type("DECIMAL") // optional: or let dialect infer
                    .precision(5)
                    .scale(2)
                    .nullable(true))     // default true unless you want NOT NULL
    
            // @NotMapped
            .ignore("cachedDisplayName")
            
            // And finished mapping User!
            .done();
    }
}
```

### Database Configuration

Entity4j supports MySQL, PostgreSQL, SQL Server, and SQLite. It can auto-detect the database dialect from the connection, but explicitly setting the dialect is recommended for reliability:

```java
// Explicit dialect configuration (recommended)
try (Connection conn = DriverManager.getConnection(...);
     DbContext ctx = new DbContext(conn, SqlDialectType.MYSQL)) {
    // Explicitly set to MySQL
}

// Auto-detection (Entity4j will detect dialect from connection) (not recommended)
try (Connection conn = DriverManager.getConnection(...);
     DbContext ctx = new DbContext(conn)) {
    // Entity4j automatically detects the database type
}

// Other supported dialects:
// SqlDialectType.POSTGRESQL
// SqlDialectType.SQLSERVER  
// SqlDialectType.SQLITE
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

## Column Selection

Entity4j provides powerful column selection capabilities that allow you to project only the columns you need, improving query performance and enabling you to shape your data exactly as needed.

### Basic Column Selection

Instead of selecting all columns with `SELECT *`, you can specify exactly which columns to retrieve:

```java
// Select only specific columns from User
Query<User> query = ctx.from(User.class)
    .select(s -> s
        .col(User::getId).as("user_id")
        .col(User::getName).as("name")
        .col(User::getRating).as("rating"))
    .filter(f -> f.equals(User::getStatus, "ACTIVE"))
    .orderBy(User::getName, true);

// Get results as maps (no class binding required)
List<Map<String, Object>> maps = query.toMapList();

// Or bind to a custom DTO class
List<UserSummaryDto> summaries = query.toList(UserSummaryDto.class);
```

**Generated SQL:**
```sql
SELECT id AS user_id, name AS name, rating AS rating
FROM users 
WHERE status = ?
ORDER BY name ASC
```

### Selecting into Custom Types

Create a custom DTO class to hold your projected data:

```java
public class UserSummaryDto {
    private Long userId;
    private String name;
    private Double rating;
    
    // getters and setters...
}
```

Then select specific columns and map them to your DTO:

```java
List<UserSummaryDto> summaries = ctx.from(User.class)
    .select(s -> s
        .col(User::getId).as("user_id")        // Maps to DTO's userId field
        .col(User::getName).as("name")        // Maps to DTO's name field  
        .col(User::getRating).as("rating"))   // Maps to DTO's rating field
    .filter(f -> f.equals(User::getStatus, "ACTIVE"))
    .orderBy(User::getName, true)
    .toList(UserSummaryDto.class);
```

**Important Note for Joined Entities:** When selecting columns from joined tables, you must specify the entity class for the column reference:

```java
// WRONG - This won't work for joined entities
.col(Order::getTotal).as("total")

// CORRECT - Specify Order.class for joined entity columns  
.col(Order.class, Order::getTotal).as("total")

// Main entity doesn't need class specification
.col(User::getName).as("name")
```

### Using toMapList()

When you don't want to create a specific class, use `toMapList()` to get results as a list of maps:

```java
List<Map<String, Object>> results = ctx.from(User.class)
    .select(s -> s
        .col(User::getId).as("id")
        .col(User::getName).as("name")
        .col(User::getRating).as("rating"))
    .filter(f -> f.greater(User::getRating, 4.0))
    .toMapList();

// Access the data
for (Map<String, Object> row : results) {
    Long id = (Long) row.get("id");
    String name = (String) row.get("name");
    Double rating = (Double) row.get("rating");
    System.out.println(name + " has rating: " + rating);
}
```

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

### 4. Complex Join with Column Selection

Here's a more complete example showing joins with column selection into a custom DTO:

```java
public class UserOrderDto {
    private Long orderId;
    private Long userId;
    private String name;
    private Double total;
    
    // getters and setters...
}

// Query with join and column selection
List<UserOrderDto> results = ctx.from(User.class).as("u")
    .innerJoin(Order.class, "o", j -> 
        j.eq(User::getId, Order::getUserId))
    .select(s -> s
        .col(Order.class, Order::getId).as("order_id")     // Note: Order.class required
        .col(User::getId).as("user_id")                    // Main entity doesn't need class
        .col(User::getName).as("name")
        .col(Order.class, Order::getTotal).as("total"))  // Note: Order.class required
    .filter(f -> f.equals(User::getStatus, "ACTIVE"))
    .orderBy(User::getName, true)
    .thenBy(Order.class, Order::getPlacedAt, false)
    .toList(UserOrderDto.class);
```

**Generated SQL:**
```sql
SELECT o.id AS order_id, u.id AS user_id, u.name AS name, o.total AS total
FROM users u
INNER JOIN orders o ON u.id = o.user_id  
WHERE u.status = ?
ORDER BY u.name ASC, o.placed_at DESC
```

## License

Entity4j is released under the GNU General Public License v3.0.
