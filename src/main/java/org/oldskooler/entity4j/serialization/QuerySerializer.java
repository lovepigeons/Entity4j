package org.oldskooler.entity4j.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.oldskooler.entity4j.IDbContext;
import org.oldskooler.entity4j.Query;
import org.oldskooler.entity4j.functions.SerializableSupplier;
import org.oldskooler.entity4j.mapping.TableMeta;
import org.oldskooler.entity4j.select.SelectionPart;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Serializes and deserializes Query objects to/from JSON.
 * This allows queries to be transmitted over the network.
 */
public class QuerySerializer {
    private final Gson gson;
    private final SupplierSerializer supplierSerializer;

    public QuerySerializer() {
        this.gson = new GsonBuilder()
                //.setPrettyPrinting()
                .disableHtmlEscaping()
                //.serializeNulls()
                .create();

        this.supplierSerializer = new SupplierSerializer();
    }

    public QuerySerializer(Gson customGson) {
        this.gson = customGson;
        this.supplierSerializer = new SupplierSerializer();
    }

    /**
     * Serialize a Query to JSON string
     */
    public <T> String toJson(Query<T> query) {
        QueryDTO dto = toDTO(query);
        return gson.toJson(dto);
    }

    /**
     * Deserialize a Query from JSON string
     */
    @SuppressWarnings("unchecked")
    public  <T> Query<T> fromJson(String json, IDbContext context) {
        QueryDTO dto = gson.fromJson(json, QueryDTO.class);
        return (Query<T>) fromDTO(dto, context);
    }

    /**
     * Convert Query to QueryDTO
     */
    private <T> QueryDTO toDTO(Query<T> query) {
        QueryDTO dto = new QueryDTO();

        dto.setEntityTypeClassName(query.getEntityType().getName());
        dto.setWhereClause(query.getWhereClause());
        dto.setOrderBys(query.getOrderBys());
        dto.setLimit(query.getLimit());
        dto.setOffset(query.getOffset());
        dto.setBaseAlias(query.getBaseAlias());
        dto.setHasExplicitSelect(query.hasExplicitSelect());

        // Serialize parameters with type information
        List<QueryDTO.ParameterDTO> paramDTOs = new ArrayList<>();
        for (Object param : query.getParameters()) {
            if (param != null) {
                paramDTOs.add(new QueryDTO.ParameterDTO(
                        param,
                        param.getClass().getName()
                ));
            } else {
                paramDTOs.add(new QueryDTO.ParameterDTO(null, "null"));
            }
        }
        dto.setParameters(paramDTOs);

        // Serialize joins
        List<QueryDTO.JoinDTO> joinDTOs = new ArrayList<>();
        for (Query.JoinPart<?> join : query.getJoins()) {
            QueryDTO.JoinDTO joinDTO = new QueryDTO.JoinDTO();
            joinDTO.setJoinedTableName(join.getMeta().table);
            joinDTO.setJoinedEntityClassName(join.getMeta().type.getName());
            joinDTO.setAlias(join.getAlias());
            joinDTO.setKind(join.getKind());
            joinDTO.setOnSql(join.getOnSql());
            joinDTOs.add(joinDTO);
        }
        dto.setJoins(joinDTOs);

        // Serialize selection parts
        List<QueryDTO.SelectionDTO> selectionDTOs = new ArrayList<>();
        for (SelectionPart part : query.getSelectionParts()) {
            QueryDTO.SelectionDTO selDTO = new QueryDTO.SelectionDTO();
            selDTO.setKind(part.kind.name());
            selDTO.setEntityTypeClassName(
                    part.entityType != null ? part.entityType.getName() : null
            );
            selDTO.setPropertyName(part.propertyName);
            selDTO.setAlias(part.alias);
            if (part.aggregateFunction != null) {
                selDTO.setAggregateFunction(part.aggregateFunction.name());
            }
            selDTO.setDistinct(part.distinct);
            selDTO.setExpression(part.expression != null ? part.expression.get() : null);
            selectionDTOs.add(selDTO);
        }
        dto.setSelectionParts(selectionDTOs);

        return dto;
    }

    /**
     * Convert QueryDTO back to Query
     */
    @SuppressWarnings("unchecked")
    public Query<?> fromDTO(QueryDTO dto, IDbContext context) {
        try {
            // Load the entity class
            Class<?> entityType = Class.forName(dto.getEntityTypeClassName());

            // Create TableMeta
            TableMeta<?> meta = TableMeta.of(entityType, context.mappingRegistry());

            // Create new Query instance
            Query<?> query = new Query<>(context, meta);

            // Restore base alias
            if (dto.getBaseAlias() != null) {
                query.as(dto.getBaseAlias());
            }

            // Restore WHERE clause and parameters using reflection
            restoreWhereAndParams(query, dto);

            // Restore ORDER BY
            List<String> orderBys = getOrderBysField(query);
            orderBys.clear();
            orderBys.addAll(dto.getOrderBys());

            // Restore pagination
            query.limit(dto.getLimit());
            query.offset(dto.getOffset());

            // Restore joins using reflection
            restoreJoins(query, dto, context);

            // Restore selection parts using reflection
            restoreSelections(query, dto);

            return query;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize Query from DTO", e);
        }
    }

    private void restoreWhereAndParams(Query<?> query, QueryDTO dto) throws Exception {
        Field whereField = Query.class.getDeclaredField("where");
        whereField.setAccessible(true);
        StringBuilder where = (StringBuilder) whereField.get(query);
        where.setLength(0);
        where.append(dto.getWhereClause());

        Field paramsField = Query.class.getDeclaredField("params");
        paramsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> params = (List<Object>) paramsField.get(query);
        params.clear();

        for (QueryDTO.ParameterDTO paramDTO : dto.getParameters()) {
            if ("null".equals(paramDTO.getTypeClassName())) {
                params.add(null);
            } else {
                params.add(paramDTO.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getOrderBysField(Query<?> query) throws Exception {
        Field orderBysField = Query.class.getDeclaredField("orderBys");
        orderBysField.setAccessible(true);
        return (List<String>) orderBysField.get(query);
    }

    @SuppressWarnings("unchecked")
    private void restoreJoins(Query<?> query, QueryDTO dto, IDbContext context) throws Exception {
        // Access internal joins list
        Field joinsField = Query.class.getDeclaredField("joins");
        joinsField.setAccessible(true);
        List<Object> joins = (List<Object>) joinsField.get(query);
        joins.clear();

        // Access internal alias map (we must keep the base alias entry)
        Field aliasesField = Query.class.getDeclaredField("aliases");
        aliasesField.setAccessible(true);
        Map<Class<?>, Object> aliases = (Map<Class<?>, Object>) aliasesField.get(query);

        if (dto.getJoins() == null || dto.getJoins().isEmpty()) {
            return;
        }

        // Reflective access to inner classes Query.JoinPart and Query.AliasMeta
        Class<?> joinPartClass = Class.forName(Query.class.getName() + "$JoinPart");
        Class<?> aliasMetaClass = Class.forName(Query.class.getName() + "$AliasMeta");

        Constructor<?> joinCtor = joinPartClass.getDeclaredConstructor(
                TableMeta.class, String.class, String.class, String.class
        );
        joinCtor.setAccessible(true);

        Constructor<?> aliasCtor = aliasMetaClass.getDeclaredConstructor(
                TableMeta.class, String.class
        );
        aliasCtor.setAccessible(true);

        for (QueryDTO.JoinDTO joinDTO : dto.getJoins()) {
            // Rebuild TableMeta for the joined entity
            Class<?> joinedType = Class.forName(joinDTO.getJoinedEntityClassName());
            TableMeta<?> joinedMeta = TableMeta.of(joinedType, context.mappingRegistry());

            String alias = joinDTO.getAlias();
            String kind = joinDTO.getKind();
            String onSql = joinDTO.getOnSql();

            // Add JoinPart entry (same as joins.add(new JoinPart<>(jm, alias, kind, on.toSql())))
            Object joinPart = joinCtor.newInstance(joinedMeta, alias, kind, onSql);
            joins.add(joinPart);

            // Rebuild alias info (same as aliases.put(type, new AliasMeta<>(jm, alias)))
            Object aliasMeta = aliasCtor.newInstance(joinedMeta, alias);
            aliases.put(joinedType, aliasMeta);
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreSelections(Query<?> query, QueryDTO dto) throws Exception {
        // Restore hasExplicitSelect flag
        Field hasExplicitSelectField = Query.class.getDeclaredField("hasExplicitSelect");
        hasExplicitSelectField.setAccessible(true);
        hasExplicitSelectField.set(query, dto.isHasExplicitSelect());

        // Access internal selectionParts list
        Field selectionPartsField = Query.class.getDeclaredField("selectionParts");
        selectionPartsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<SelectionPart> selectionParts = (List<SelectionPart>) selectionPartsField.get(query);
        selectionParts.clear();

        if (dto.getSelectionParts() == null || dto.getSelectionParts().isEmpty()) {
            return;
        }

        // Reflective access to SelectionPart private ctor:
        // SelectionPart(Kind kind, Class<?> entityType, String propertyName,
        //               String alias, AggregateFunction aggregateFunction, boolean distinct)
        Constructor<SelectionPart> ctor = SelectionPart.class.getDeclaredConstructor(
                SelectionPart.Kind.class,
                Class.class,
                String.class,
                String.class,
                SelectionPart.AggregateFunction.class,
                boolean.class,
                SerializableSupplier.class
        );
        ctor.setAccessible(true);

        for (QueryDTO.SelectionDTO selDTO : dto.getSelectionParts()) {
            SelectionPart.Kind kind = SelectionPart.Kind.valueOf(selDTO.getKind());

            Class<?> entityType = null;
            if (selDTO.getEntityTypeClassName() != null) {
                entityType = Class.forName(selDTO.getEntityTypeClassName());
            }

            String propertyName = selDTO.getPropertyName();
            String alias = selDTO.getAlias();

            SelectionPart.AggregateFunction agg = null;
            if (selDTO.getAggregateFunction() != null) {
                agg = SelectionPart.AggregateFunction.valueOf(selDTO.getAggregateFunction());
            }

            boolean distinct = selDTO.isDistinct();
            SerializableSupplier<String> expr = selDTO.getExpression() != null ? selDTO::getExpression : null;

            SelectionPart part = ctor.newInstance(
                    kind,
                    entityType,
                    propertyName,
                    alias,
                    agg,
                    distinct,
                    expr
            );
            selectionParts.add(part);
        }
    }

}