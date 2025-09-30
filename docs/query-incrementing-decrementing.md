# Query API: Update and Return Patterns

This guide demonstrates common patterns for updating database rows and retrieving the updated results using a fluent Query API. The examples show how to increment columns, batch update multiple fields, and return updated entities in a single operation.

## Example Entity

```java
// example entity (your real entity will have annotations / mapping)
public class User {
    public Long id;
    public String username;
    public String status;
    public Integer loginCount;
    public Integer points;
    public Double balance;
    // getters/setters omitted for brevity
}
```

## 1) Increment a Single Column for Matching Rows

Increment `loginCount` by 1 for all users with `status = 'active'`:

```java
Query<User> q = ctx.from(User.class)
                   .filter(f -> f.equals(User::status, "active"));

int updatedRows = q.increment(User::loginCount, 1);
System.out.println("Rows updated: " + updatedRows);

// If you want the updated rows afterwards:
List<User> updatedUsers = q.toList();   // same WHERE, now reflects updated values
```

**Notes:**
- `increment(...)` returns the affected row count.
- `q.toList()` reuses the same WHERE you built on `q`, so you get the updated values.

## 2) Batch Increment Multiple Columns at Once

Add 1 to `loginCount` and add 10 to `points` for active users:

```java
// Prepare ordered map of increments (LinkedHashMap preserves order)
LinkedHashMap<SFunction<User, ?>, Number> inc = new LinkedHashMap<>();
inc.put(User::loginCount, 1);
inc.put(User::points, 10);

Query<User> q = ctx.from(User.class)
                   .filter(f -> f.equals(User::status, "active"));

int rows = q.incrementBatch(inc);
System.out.println("Batch increment affected: " + rows);

// Get the updated rows
List<User> after = q.toList();
```

If you want to get the updated rows immediately (the helper pattern used earlier), call `toList()` on the same Query after the `incrementBatch()` — the WHERE lives in the Query instance, so this is straightforward.

## 3) Use `updateReturningList(...)` to SET Values and Get Back Updated Rows

Suppose you want to set `balance = balance + 50.0` for users with `points > 100` and then return all the updated User objects (DB-agnostic implementation: executes UPDATE then SELECT):

```java
Query<User> q = ctx.from(User.class)
                   .filter(f -> f.greater(User::points, 100));

// Use SetBuilder to describe SET expressions. Typical SetBuilder API:
//   s.set(User::balance, newValue)   -> sets column = ?
// If you want to add to balance using SetBuilder you may need a raw expression.
// If SetBuilder lacks expression support, use incrementBatch instead.
//
// Example using simple assignment:
List<User> updated = q.updateReturningList(s -> {
    s.set(User::balance, 500.0);   // set balance = 500.0
});

System.out.println("Returned " + updated.size() + " updated users.");
updated.forEach(u -> System.out.println(u.id + " -> " + u.balance));
```

**Important:**
- `updateReturningList` reuses the Query's WHERE to fetch rows after the update, so build your filters beforehand.
- If you need expression updates (e.g. `balance = balance + 50`) prefer `incrementBatch` or extend SetBuilder to accept raw SQL expressions.

## 4) Update and Return a Single (Optional) Entity

Update a single row (or multiple rows but return the first matched row after update):

```java
Query<User> q = ctx.from(User.class)
                   .filter(f -> f.equals(User::username, "alice"));

Optional<User> maybe = q.updateReturningOptional(s -> {
    s.set(User::status, "inactive");
});

maybe.ifPresentOrElse(
    u -> System.out.println("Updated user: " + u.username + " status=" + u.status),
    () -> System.out.println("No user updated")
);
```

**Notes:**
- `updateReturningOptional` will call `update(...)` (which updates all matching rows) and then set `limit=1` on the select to return the first row (if any). If you intend to update exactly one row, make sure your WHERE clause targets a single row (e.g. `id = ?`).

## 5) Combining `incrementBatch` and Returning the Updated Rows

If you want to increment and then fetch updated rows in one utility sequence:

```java
Query<User> q = ctx.from(User.class)
                   .filter(f -> f.equals(User::status, "active"));

LinkedHashMap<SFunction<User, ?>, Number> inc = new LinkedHashMap<>();
inc.put(User::points, 5);
inc.put(User::loginCount, 1);

int affected = q.incrementBatch(inc);

// immediately fetch the updated list
List<User> updated = q.toList();
```