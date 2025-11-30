package org.oldskooler.entity4j.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.oldskooler.entity4j.IDbContext;
import org.oldskooler.entity4j.Query;
import org.oldskooler.entity4j.mapping.TableMeta;
import org.oldskooler.entity4j.select.SelectionOrder;
import org.oldskooler.entity4j.select.SelectionPart;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Serializes and deserializes Query objects to/from JSON.
 * This allows queries to be transmitted over the network.
 */
public class QuerySerializer {
    private final Gson gson;

    public QuerySerializer() {
        this.gson = new GsonBuilder()
                //.setPrettyPrinting()
                .disableHtmlEscaping()
                //.serializeNulls()
                .create();
    }

    public QuerySerializer(Gson customGson) {
        this.gson = customGson;
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

        // Serialize group by parts
        List<QueryDTO.SelectionDTO> groupByDTOs = new ArrayList<>();
        for (SelectionPart part : query.getGroupBys()) {
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
            groupByDTOs.add(selDTO);
        }
        dto.setGroupBys(groupByDTOs);

        // Serialize order by parts
        List<QueryDTO.SelectionDTO> orderByDTOs = new ArrayList<>();
        for (SelectionPart part : query.getOrderBys()) {
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
            selDTO.setOrderBy(part.orderBy);
            orderByDTOs.add(selDTO);
        }
        dto.setOrderBys(orderByDTOs);

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

            // Restore pagination
            query.limit(dto.getLimit());
            query.offset(dto.getOffset());

            // Restore joins using reflection
            restoreJoins(query, dto, context);

            // Restore selection parts using reflection
            restoreSelections(query, dto);

            restoreGroupBys(query, dto);
            restoreOrderBys(query, dto);

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
    private List<Supplier<String>> getOrderBysField(Query<?> query) throws Exception {
        Field orderBysField = Query.class.getDeclaredField("orderBys");
        orderBysField.setAccessible(true);
        return (List<Supplier<String>>) orderBysField.get(query);
    }

    @SuppressWarnings("unchecked")
    private List<Supplier<String>> getGroupBysField(Query<?> query) throws Exception {
        Field orderBysField = Query.class.getDeclaredField("groupBys");
        orderBysField.setAccessible(true);
        return (List<Supplier<String>>) orderBysField.get(query);
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

        // Restore selection parts
        restoreQueryParts(
                query,
                "selectionParts",
                SelectionPart.class,
                dto.getSelectionParts()
        );
    }

    @SuppressWarnings("unchecked")
    private void restoreOrderBys(Query<?> query, QueryDTO dto) throws Exception {
        restoreQueryParts(
                query,
                "orderBys",
                SelectionPart.class,
                dto.getOrderBys()
        );
    }

    @SuppressWarnings("unchecked")
    private void restoreGroupBys(Query<?> query, QueryDTO dto) throws Exception {
        restoreQueryParts(
                query,
                "groupBys",
                SelectionPart.class,
                dto.getGroupBys()
        );
    }

    /**
     * Generic method to restore query parts (SelectionPart, OrderByPart, GroupByPart)
     *
     * @param query The Query object to restore parts into
     * @param fieldName The name of the field in Query class (e.g., "selectionParts")
     * @param partClass The class type of the part (SelectionPart.class, OrderByPart.class, etc.)
     * @param dtoList The list of DTOs containing the serialized part data
     */
    @SuppressWarnings("unchecked")
    private <T, D extends QueryDTO.SelectionDTO> void restoreQueryParts(
            Query<?> query,
            String fieldName,
            Class<T> partClass,
            List<D> dtoList) throws Exception {

        // Access internal parts list
        Field partsField = Query.class.getDeclaredField(fieldName);
        partsField.setAccessible(true);
        List<T> parts = (List<T>) partsField.get(query);
        parts.clear();

        if (dtoList == null || dtoList.isEmpty()) {
            return;
        }

        // Get the appropriate constructor based on part type
        Constructor<T> ctor = getPartConstructor(partClass);
        ctor.setAccessible(true);

        // Reconstruct each part
        for (D partDTO : dtoList) {
            T part = createPartFromDTO(ctor, partClass, partDTO);
            parts.add(part);
        }
    }

    /**
     * Get the constructor for the specific part type
     */
    @SuppressWarnings("unchecked")
    private <T> Constructor<T> getPartConstructor(Class<T> partClass) throws NoSuchMethodException {
            return (Constructor<T>) SelectionPart.class.getDeclaredConstructor(
                    SelectionPart.Kind.class,
                    Class.class,
                    String.class,
                    String.class,
                    SelectionPart.AggregateFunction.class,
                    boolean.class,
                    Supplier.class,
                    SelectionOrder.class
            );

    }

    /**
     * Create a part instance from its DTO representation
     */
    @SuppressWarnings("unchecked")
    private <T, D extends QueryDTO.SelectionDTO> T createPartFromDTO(
            Constructor<T> ctor,
            Class<T> partClass,
            D partDTO) throws Exception {

            QueryDTO.SelectionDTO selDTO = (QueryDTO.SelectionDTO) partDTO;
            SelectionPart.Kind kind = SelectionPart.Kind.valueOf(selDTO.getKind());
            Class<?> entityType = getEntityType(selDTO.getEntityTypeClassName());
            String propertyName = selDTO.getPropertyName();
            String alias = selDTO.getAlias();
            SelectionPart.AggregateFunction agg = getAggregateFunction(selDTO.getAggregateFunction());
            boolean distinct = selDTO.isDistinct();
            Supplier<String> expr = createExpressionSupplier(selDTO.getExpression());
            SelectionOrder order = selDTO.getOrderBy();

            return ctor.newInstance(kind, entityType, propertyName, alias, agg, distinct, expr, order);
    }

    /**
     * Helper method to resolve entity type from class name
     */
    private Class<?> getEntityType(String className) throws ClassNotFoundException {
        return className != null ? Class.forName(className) : null;
    }

    /**
     * Helper method to resolve aggregate function from string
     */
    private SelectionPart.AggregateFunction getAggregateFunction(String functionName) {
        return functionName != null ? SelectionPart.AggregateFunction.valueOf(functionName) : null;
    }

    /**
     * Helper method to create expression supplier
     */
    private Supplier<String> createExpressionSupplier(String expression) {
        return expression != null ? () -> expression : null;
    }

}