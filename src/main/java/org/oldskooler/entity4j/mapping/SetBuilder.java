package org.oldskooler.entity4j.mapping;

import org.oldskooler.entity4j.Query;
import org.oldskooler.entity4j.dialect.SqlDialect;
import org.oldskooler.entity4j.functions.SFunction;
import org.oldskooler.entity4j.util.LambdaUtils;
import org.oldskooler.entity4j.util.Names;

import java.util.ArrayList;
import java.util.List;

public class SetBuilder<T> {
    private final TableMeta<T> meta;
    final List<String> sets = new ArrayList<>();
    final List<Object> params = new ArrayList<>();
    private final SqlDialect dialect;

    public SetBuilder(SqlDialect dialect, TableMeta<T> meta) {
        this.dialect = dialect;
        this.meta = meta;
    }

    public SetBuilder<T> set(SFunction<T, ?> getter, Object value) {
        String prop = LambdaUtils.propertyName(getter);
        String col  = meta.propToColumn.getOrDefault(prop, Names.defaultColumnName(prop));
        sets.add(this.dialect.q(col) + " = ?");
        params.add(value);
        return this;
    }

    public List<String> sets() {
        return sets;
    }

    public List<Object> params() {
        return params;
    }
}