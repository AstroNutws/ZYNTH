package com.zynth.model;

import java.util.UUID;

public class Relationship {
    private UUID id = UUID.randomUUID();
    private UUID sourceTableId;
    private String sourceColumnName;
    private UUID targetTableId;
    private String targetColumnName;
    private RelationshipType type = RelationshipType.ONE_TO_MANY;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSourceTableId() {
        return sourceTableId;
    }

    public void setSourceTableId(UUID sourceTableId) {
        this.sourceTableId = sourceTableId;
    }

    public String getSourceColumnName() {
        return sourceColumnName;
    }

    public void setSourceColumnName(String sourceColumnName) {
        this.sourceColumnName = sourceColumnName;
    }

    public UUID getTargetTableId() {
        return targetTableId;
    }

    public void setTargetTableId(UUID targetTableId) {
        this.targetTableId = targetTableId;
    }

    public String getTargetColumnName() {
        return targetColumnName;
    }

    public void setTargetColumnName(String targetColumnName) {
        this.targetColumnName = targetColumnName;
    }

    public RelationshipType getType() {
        return type;
    }

    public void setType(RelationshipType type) {
        this.type = type;
    }
}
