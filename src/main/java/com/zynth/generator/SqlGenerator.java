package com.zynth.generator;

import com.zynth.model.Column;
import com.zynth.model.DatabaseSchema;
import com.zynth.model.PostgresDataType;
import com.zynth.model.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SqlGenerator {

    public String generate(DatabaseSchema schema) {
        StringBuilder sb = new StringBuilder();
        Set<String> schemas = new LinkedHashSet<>();
        Set<String> enumTypes = new LinkedHashSet<>();

        for (Table t : schema.getTables()) {
            schemas.add(t.getSchema() == null || t.getSchema().isBlank() ? "public" : t.getSchema());
            for (Column c : t.getColumns()) {
                if (c.getDataType() == PostgresDataType.ENUM && c.getEnumValues() != null && !c.getEnumValues().isEmpty()) {
                    String enumName = c.getEnumName();
                    if (enumName == null || enumName.isBlank()) {
                        enumName = t.getName() + "_" + c.getName() + "_enum";
                    }
                    String schemaName = t.getSchema() == null || t.getSchema().isBlank() ? "public" : t.getSchema();
                    String values = c.getEnumValues().stream()
                        .map(v -> "'" + v.replace("'", "''") + "'")
                        .collect(Collectors.joining(", "));
                    enumTypes.add("CREATE TYPE " + quote(schemaName) + "." + quote(enumName) + " AS ENUM (" + values + ");");
                }
            }
        }

        for (String schemaName : schemas) {
            sb.append("CREATE SCHEMA IF NOT EXISTS ").append(quote(schemaName)).append(";\n");
        }
        if (!schemas.isEmpty()) {
            sb.append("\n");
        }
        for (String enumSql : enumTypes) {
            sb.append(enumSql).append("\n");
        }
        if (!enumTypes.isEmpty()) {
            sb.append("\n");
        }

        for (Table table : schema.getTables()) {
            sb.append(generateCreateTable(table)).append("\n\n");
        }

        for (Table table : schema.getTables()) {
            if (table.isRealtimeEnabled()) {
                sb.append("ALTER PUBLICATION supabase_realtime ADD TABLE ")
                    .append(quote(table.getSchema()))
                    .append(".")
                    .append(quote(table.getName()))
                    .append(";\n");
            }
        }
        return sb.toString().trim();
    }

    public String generateCreateTable(Table table) {
        String qualified = quote(table.getSchema()) + "." + quote(table.getName());
        StringBuilder sb = new StringBuilder("CREATE TABLE " + qualified + " (\n");

        List<String> lines = new ArrayList<>();
        List<String> pkCols = new ArrayList<>();
        for (Column col : table.getColumns()) {
            lines.add("    " + quote(col.getName()) + " " + mapType(col) + columnConstraints(col));
            if (col.isPrimaryKey()) {
                pkCols.add(quote(col.getName()));
            }
        }
        if (!pkCols.isEmpty()) {
            lines.add("    PRIMARY KEY (" + String.join(", ", pkCols) + ")");
        }
        sb.append(lines.stream().collect(Collectors.joining(",\n")));
        sb.append("\n);");
        return sb.toString();
    }

    private String columnConstraints(Column col) {
        StringBuilder sb = new StringBuilder();
        if (!col.isNullable()) {
            sb.append(" NOT NULL");
        }
        if (col.isUnique()) {
            sb.append(" UNIQUE");
        }
        if (col.getDefaultValue() != null && !col.getDefaultValue().isBlank()) {
            sb.append(" DEFAULT ").append(col.getDefaultValue());
        }
        if (col.getReferencesTable() != null && !col.getReferencesTable().isBlank()
            && col.getReferencesColumn() != null && !col.getReferencesColumn().isBlank()) {
            String refSchema = col.getReferencesSchema() == null || col.getReferencesSchema().isBlank() ? "public" : col.getReferencesSchema();
            sb.append(" REFERENCES ")
                .append(quote(refSchema))
                .append(".")
                .append(quote(col.getReferencesTable()))
                .append("(")
                .append(quote(col.getReferencesColumn()))
                .append(")");
        }
        return sb.toString();
    }

    private String mapType(Column col) {
        PostgresDataType t = col.getDataType();
        return switch (t) {
            case VARCHAR -> col.getMaxLength() != null ? "VARCHAR(" + col.getMaxLength() + ")" : "VARCHAR";
            case CHAR -> col.getMaxLength() != null ? "CHAR(" + col.getMaxLength() + ")" : "CHAR";
            case DECIMAL, NUMERIC -> {
                if (col.getPrecision() != null && col.getScale() != null) {
                    yield t.name() + "(" + col.getPrecision() + "," + col.getScale() + ")";
                }
                yield t.name();
            }
            case DOUBLE_PRECISION -> "DOUBLE PRECISION";
            case ENUM -> {
                String enumName = col.getEnumName() == null || col.getEnumName().isBlank() ? col.getName() + "_enum" : col.getEnumName();
                yield quote(enumName);
            }
            default -> t.name();
        };
    }

    private String quote(String ident) {
        return "\"" + ident + "\"";
    }
}
