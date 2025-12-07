package org.oldskooler.entity4j.json;

import org.oldskooler.entity4j.json.models.Order;
import org.oldskooler.entity4j.json.models.User;
import org.oldskooler.entity4j.json.models.UserOrderDTO;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Demonstrates QuerySerializer with two examples:
 * 1. Simple: Basic user filtering and ordering
 * 2. Complex: JOIN query with custom DTO projection
 */
public class QueryDemo {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://127.0.0.1:3306/app";
        String user = "root";
        String pass = "verysecret";

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             AppDbContext context = new AppDbContext(conn)) {

            // Setup test data
            setupDatabase(context);

            List<UserOrderDTO> query = context.from(User.class).as("u")
                    .select(s -> s
                            .col(User::getId).as("user_id")
                            .col(User::getName).as("user_name")
                            .col(User::getRating).as("rating")
                            .col(Order.class, Order::getOrderId).as("order_id")
                            .col(Order.class, Order::getTotal).as("order_total")
                            .col(Order.class, Order::getPlacedAt).as("placed_at"))
                    .innerJoin(Order.class, "o", on ->
                            on.eq(User::getId, Order::getUserId))
                    .filter(f -> f
                            .equals(User::getStatus, "active")
                            .and()
                            .greater(Order.class, Order::getTotal, 100.0))
                    .limit(20)
                    .toList(UserOrderDTO.class);

            System.out.println(query);
        }
    }

    /**
     * Setup database with sample data
     */
    static void setupDatabase(AppDbContext context) throws Exception {
        // Drop and recreate tables
        context.dropTableIfExists(Order.class);
        context.dropTableIfExists(User.class);
        context.createTable(User.class);
        context.createTable(Order.class);

        // Insert sample users
        User alice = new User();
        alice.setName("Alice Smith");
        alice.setStatus("active");
        alice.setRating(4.8);
        context.insert(alice);

        User bob = new User();
        bob.setName("Bob Johnson");
        bob.setStatus("active");
        bob.setRating(4.8);
        context.insert(bob);

        User charlie = new User();
        charlie.setName("Charlie Brown");
        charlie.setStatus("active");
        charlie.setRating(4.2);
        context.insert(charlie);

        User diana = new User();
        diana.setName("Diana Prince");
        diana.setStatus("inactive");
        diana.setRating(3.5);
        context.insert(diana);

        User eve = new User();
        eve.setName("Eve Wilson");
        eve.setStatus("active");
        eve.setRating(4.7);
        context.insert(eve);

        // Insert sample orders (some > $100, some < $100)
        context.insert(new Order(null, 1, 125.50, LocalDateTime.now().minusDays(5)));
        context.insert(new Order(null, 1, 75.25, LocalDateTime.now().minusDays(10)));
        context.insert(new Order(null, 2, 250.00, LocalDateTime.now().minusDays(2)));
        context.insert(new Order(null, 2, 89.99, LocalDateTime.now().minusDays(15)));
        context.insert(new Order(null, 3, 45.00, LocalDateTime.now().minusDays(3)));
        context.insert(new Order(null, 3, 150.75, LocalDateTime.now().minusDays(20)));
        context.insert(new Order(null, 5, 199.99, LocalDateTime.now().minusDays(1)));
        context.insert(new Order(null, 5, 320.00, LocalDateTime.now().minusDays(7)));
    }
}