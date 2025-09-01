package org.oldskooler.entity4j.util;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class JdbcParamBinder {
    private JdbcParamBinder() {}

    public static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object v = params.get(i);
            if (v instanceof LocalDate) {
                LocalDate ld = (LocalDate) v;
                ps.setDate(i + 1, Date.valueOf(ld));
            } else if (v instanceof LocalDateTime) {
                LocalDateTime ldt = (LocalDateTime) v;
                ps.setTimestamp(i + 1, Timestamp.valueOf(ldt));
            } else if (v instanceof Instant) {
                Instant inst = (Instant) v;
                ps.setTimestamp(i + 1, Timestamp.from(inst));
            } else {
                ps.setObject(i + 1, v);
            }
        }
    }
}