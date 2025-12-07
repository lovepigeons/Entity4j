package org.oldskooler.entity4j.json;

import org.oldskooler.entity4j.DbContext;
import org.oldskooler.entity4j.dialect.SqlDialectType;
import org.oldskooler.entity4j.json.models.Order;
import org.oldskooler.entity4j.json.models.User;
import org.oldskooler.entity4j.mapping.ModelBuilder;

import java.sql.Connection;
import java.sql.SQLException;

public class AppDbContext extends DbContext {
    public AppDbContext(Connection connection) throws SQLException {
        super(connection, SqlDialectType.MYSQL);
    }

    @Override
    public void onModelCreating(ModelBuilder model) {
        model.entity(User.class)
                .toTable("users")
                .hasId("id", "id")                // AUTO_INCREMENT
                .map("name", "name")
                .map("status", "status")
                .map("rating", "rating")
                .done();

        model.entity(Order.class)
                .toTable("orders")
                .hasId("orderId")          // not auto in your DDL
                .map("userId", "user_id")
                .map("total", "total")
                .map("placedAt", "placed_at")
                .done();


    }
}
