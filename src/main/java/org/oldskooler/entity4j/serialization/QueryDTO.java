package org.oldskooler.entity4j.serialization;

import com.google.gson.annotations.SerializedName;
import org.oldskooler.entity4j.select.SelectionOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for serializing Query objects to/from JSON.
 * This class contains all the state needed to reconstruct a Query.
 */
public class QueryDTO {
    @SerializedName("entityType")
    private String entityTypeClassName;

    @SerializedName("where")
    private String whereClause;

    @SerializedName("params")
    private List<ParameterDTO> parameters = new ArrayList<>();

    @SerializedName("orderBy")
    private List<SelectionDTO> orderBys = new ArrayList<>();

    @SerializedName("groupBy")
    private List<SelectionDTO> groupBys = new ArrayList<>();

    @SerializedName("limit")
    private Integer limit;

    @SerializedName("offset")
    private Integer offset;

    @SerializedName("alias")
    private String baseAlias;

    @SerializedName("joins")
    private List<JoinDTO> joins = new ArrayList<>();

    @SerializedName("explicitSelect")
    private boolean hasExplicitSelect;

    @SerializedName("selections")
    private List<SelectionDTO> selectionParts = new ArrayList<>();

    // Constructors
    public QueryDTO() {}

    // Getters and Setters
    public String getEntityTypeClassName() {
        return entityTypeClassName;
    }

    public void setEntityTypeClassName(String entityTypeClassName) {
        this.entityTypeClassName = entityTypeClassName;
    }

    public String getWhereClause() {
        return whereClause;
    }

    public void setWhereClause(String whereClause) {
        this.whereClause = whereClause;
    }

    public List<ParameterDTO> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterDTO> parameters) {
        this.parameters = parameters;
    }

    public List<SelectionDTO> getOrderBys() {
        return orderBys;
    }

    public void setOrderBys(List<SelectionDTO> orderBys) {
        this.orderBys = orderBys;
    }

    public List<SelectionDTO> getGroupBys() {
        return groupBys;
    }

    public void setGroupBys(List<SelectionDTO> groupBys) {
        this.groupBys = groupBys;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public String getBaseAlias() {
        return baseAlias;
    }

    public void setBaseAlias(String baseAlias) {
        this.baseAlias = baseAlias;
    }

    public List<JoinDTO> getJoins() {
        return joins;
    }

    public void setJoins(List<JoinDTO> joins) {
        this.joins = joins;
    }

    public boolean isHasExplicitSelect() {
        return hasExplicitSelect;
    }

    public void setHasExplicitSelect(boolean hasExplicitSelect) {
        this.hasExplicitSelect = hasExplicitSelect;
    }

    public List<SelectionDTO> getSelectionParts() {
        return selectionParts;
    }

    public void setSelectionParts(List<SelectionDTO> selectionParts) {
        this.selectionParts = selectionParts;
    }

    /**
     * Represents a query parameter with its type information
     */
    public static class ParameterDTO {
        @SerializedName("value")
        private Object value;

        @SerializedName("type")
        private String typeClassName;

        public ParameterDTO() {}

        public ParameterDTO(Object value, String typeClassName) {
            this.value = value;
            this.typeClassName = typeClassName;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getTypeClassName() {
            return typeClassName;
        }

        public void setTypeClassName(String typeClassName) {
            this.typeClassName = typeClassName;
        }
    }

    /**
     * Represents a JOIN clause
     */
    public static class JoinDTO {
        @SerializedName("table")
        private String joinedTableName;

        @SerializedName("entity")
        private String joinedEntityClassName;

        @SerializedName("alias")
        private String alias;

        @SerializedName("kind")
        private String kind;

        @SerializedName("on")
        private String onSql;

        public JoinDTO() {}

        public String getJoinedTableName() {
            return joinedTableName;
        }

        public void setJoinedTableName(String joinedTableName) {
            this.joinedTableName = joinedTableName;
        }

        public String getJoinedEntityClassName() {
            return joinedEntityClassName;
        }

        public void setJoinedEntityClassName(String joinedEntityClassName) {
            this.joinedEntityClassName = joinedEntityClassName;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getOnSql() {
            return onSql;
        }

        public void setOnSql(String onSql) {
            this.onSql = onSql;
        }
    }

    /**
     * Represents a SELECT clause part
     */
    public static class SelectionDTO {
        @SerializedName("kind")
        private String kind; // COLUMN, STAR, AGGREGATE, COMPUTED

        @SerializedName("entityType")
        private String entityTypeClassName;

        @SerializedName("property")
        private String propertyName;

        @SerializedName("alias")
        private String alias;

        @SerializedName("function")
        private String aggregateFunction; // SUM, AVG, COUNT, MIN, MAX

        @SerializedName("distinct")
        private boolean distinct;

        @SerializedName("expr")
        private String expression;

        @SerializedName("orderBy")
        private SelectionOrder orderBy;

        public SelectionDTO() {}

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getEntityTypeClassName() {
            return entityTypeClassName;
        }

        public void setEntityTypeClassName(String entityTypeClassName) {
            this.entityTypeClassName = entityTypeClassName;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getAggregateFunction() {
            return aggregateFunction;
        }

        public void setAggregateFunction(String aggregateFunction) {
            this.aggregateFunction = aggregateFunction;
        }

        public boolean isDistinct() {
            return distinct;
        }

        public void setDistinct(boolean distinct) {
            this.distinct = distinct;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public SelectionOrder getOrderBy() {
            return orderBy;
        }

        public void setOrderBy(SelectionOrder orderBy) {
            this.orderBy = orderBy;
        }
    }
}