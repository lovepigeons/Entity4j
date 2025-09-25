package org.oldskooler.entity4j.util;

import org.oldskooler.entity4j.mapping.TableMeta;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

public final class RowMapper {
    private RowMapper() {}

    public static <T> List<T> mapAll(ResultSet rs, TableMeta<T> m) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, SQLException {
        List<T> out = new ArrayList<>();
        while (rs.next()) out.add(mapRow(rs, m));
        return out;
    }

    public static List<Map<String, Object>> toMapList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        final int cols = md.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>(cols);
            for (int i = 1; i <= cols; i++) {
                String label = md.getColumnLabel(i); // respects SQL aliases
                Object val = rs.getObject(i);
                row.put(label, val);
            }
            out.add(row);
        }
        return out;
    }

    private static <T> T mapRow(ResultSet rs, TableMeta<T> m) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
            T inst = m.type.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, String> e : m.propToColumn.entrySet()) {
                String prop = e.getKey();
                String col = e.getValue();
                Object dbVal;
                try {
                    dbVal = rs.getObject(col);
                } catch (SQLException ex) {
                    continue;
                }
                Field f = m.propToField.get(prop);
                ReflectionUtils.setField(inst, f, dbVal);
            }
            return inst;
    }
}