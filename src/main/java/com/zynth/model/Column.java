package com.zynth.model;

import java.util.ArrayList;
import java.util.List;

public class Column {
    private String name;
    private PostgresDataType dataType = PostgresDataType.TEXT;
    private Integer maxLength;
    private Integer precision;
    private Integer scale;
    private boolean nullable = true;
    private boolean primaryKey = false;
    private boolean unique = false;
    private String defaultValue;
    private String comment;
    private String enumName;
    private List<String> enumValues = new ArrayList<>();
    private String referencesSchema;
    private String referencesTable;
    private String referencesColumn;

    public Column() {
    }

    public Column(String name, PostgresDataType dataType) {
        this.name = name;
        this.dataType = dataType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PostgresDataType getDataType() {
        return dataType;
    }

    public void setDataType(PostgresDataType dataType) {
        this.dataType = dataType;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getEnumName() {
        return enumName;
    }

    public void setEnumName(String enumName) {
        this.enumName = enumName;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(List<String> enumValues) {
        this.enumValues = enumValues;
    }

    public String getReferencesSchema() {
        return referencesSchema;
    }

    public void setReferencesSchema(String referencesSchema) {
        this.referencesSchema = referencesSchema;
    }

    public String getReferencesTable() {
        return referencesTable;
    }

    public void setReferencesTable(String referencesTable) {
        this.referencesTable = referencesTable;
    }

    public String getReferencesColumn() {
        return referencesColumn;
    }

    public void setReferencesColumn(String referencesColumn) {
        this.referencesColumn = referencesColumn;
    }
}
