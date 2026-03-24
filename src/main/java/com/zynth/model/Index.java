package com.zynth.model;

import java.util.ArrayList;
import java.util.List;

public class Index {
    private String name;
    private List<String> columnNames = new ArrayList<>();
    private boolean unique;
    private IndexType type = IndexType.BTREE;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public void setColumnNames(List<String> columnNames) {
        this.columnNames = columnNames;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public IndexType getType() {
        return type;
    }

    public void setType(IndexType type) {
        this.type = type;
    }
}
