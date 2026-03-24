package com.zynth.io;

import com.zynth.model.Column;
import com.zynth.model.DatabaseSchema;
import com.zynth.model.Index;
import com.zynth.model.IndexType;
import com.zynth.model.PostgresDataType;
import com.zynth.model.Relationship;
import com.zynth.model.RelationshipType;
import com.zynth.model.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class SupabaseImporter {

    public record ConnectionInfo(String jdbcUrl, String user, String password) {
    }

    public DatabaseSchema importSchema(ConnectionInfo info) throws Exception {
        return importSchema(info, null);
    }

    public DatabaseSchema importSchema(ConnectionInfo info, String schemaName) throws Exception {
        DatabaseSchema schema = new DatabaseSchema();
        schema.setName("supabase-import");

        String tableSchemaFilterSql = schemaName == null || schemaName.isBlank()
            ? "table_schema not in ('pg_catalog', 'information_schema')"
            : "table_schema = '" + escapeLiteral(schemaName) + "'";

        String schemaNameNotNull = schemaName == null || schemaName.isBlank() ? null : schemaName.trim();

        try (Connection conn = DriverManager.getConnection(info.jdbcUrl(), info.user(), info.password());
            Statement st = conn.createStatement()) {

            ResultSet tablesRs = st.executeQuery("""
                select table_schema, table_name
                from information_schema.tables
                where table_type='BASE TABLE'
                  and %s
                order by table_schema, table_name
                """.formatted(tableSchemaFilterSql));

            int idx = 0;
            Map<String, Table> tableMap = new HashMap<>();
            while (tablesRs.next()) {
                Table t = new Table();
                t.setSchema(tablesRs.getString("table_schema"));
                t.setName(tablesRs.getString("table_name"));
                t.setX(60 + (idx % 4) * 320);
                t.setY(60 + (idx / 4) * 220);
                idx++;
                schema.getTables().add(t);
                tableMap.put(t.getSchema() + "." + t.getName(), t);
            }

            for (Table t : schema.getTables()) {
                ResultSet colRs = st.executeQuery("""
                    select column_name, data_type, is_nullable, column_default
                    from information_schema.columns
                    where table_schema='%s' and table_name='%s'
                    order by ordinal_position
                    """.formatted(escapeLiteral(t.getSchema()), escapeLiteral(t.getName())));
                while (colRs.next()) {
                    Column c = new Column();
                    c.setName(colRs.getString("column_name"));
                    c.setDataType(mapDataType(colRs.getString("data_type")));
                    c.setNullable("YES".equalsIgnoreCase(colRs.getString("is_nullable")));
                    c.setDefaultValue(colRs.getString("column_default"));
                    t.getColumns().add(c);
                }
            }

            String fkWhere = schemaNameNotNull == null
                ? "tc.constraint_type='FOREIGN KEY'"
                : "tc.constraint_type='FOREIGN KEY' and tc.table_schema='" + escapeLiteral(schemaNameNotNull) + "'";

            ResultSet fkRs = st.executeQuery("""
                select
                  tc.table_schema as source_schema,
                  tc.table_name as source_table,
                  kcu.column_name as source_column,
                  ccu.table_schema as target_schema,
                  ccu.table_name as target_table,
                  ccu.column_name as target_column
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_name = kcu.constraint_name
                 and tc.table_schema = kcu.table_schema
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_name = tc.constraint_name
                 and ccu.table_schema = tc.table_schema
                where %s
                """.formatted(fkWhere));

            while (fkRs.next()) {
                String srcSchema = fkRs.getString("source_schema");
                String srcTable = fkRs.getString("source_table");
                String srcColumn = fkRs.getString("source_column");
                String dstSchema = fkRs.getString("target_schema");
                String dstTable = fkRs.getString("target_table");
                String dstColumn = fkRs.getString("target_column");
                Table src = tableMap.get(srcSchema + "." + srcTable);
                Table dst = tableMap.get(dstSchema + "." + dstTable);
                if (src == null) {
                    continue;
                }
                for (Column c : src.getColumns()) {
                    if (srcColumn.equalsIgnoreCase(c.getName())) {
                        c.setReferencesSchema(dstSchema);
                        c.setReferencesTable(dstTable);
                        c.setReferencesColumn(dstColumn);
                        break;
                    }
                }
                if (dst != null) {
                    Relationship rel = new Relationship();
                    rel.setSourceTableId(src.getId());
                    rel.setSourceColumnName(srcColumn);
                    rel.setTargetTableId(dst.getId());
                    rel.setTargetColumnName(dstColumn);
                    rel.setType(RelationshipType.MANY_TO_ONE);
                    schema.getRelationships().add(rel);
                }
            }

            String idxWhere = schemaNameNotNull == null
                ? "schemaname not in ('pg_catalog','information_schema')"
                : "schemaname = '" + escapeLiteral(schemaNameNotNull) + "'";

            ResultSet idxRs = st.executeQuery("""
                select schemaname, tablename, indexname, indexdef
                from pg_indexes
                where %s
                """.formatted(idxWhere));
            while (idxRs.next()) {
                String schemaName2 = idxRs.getString("schemaname");
                String tableName = idxRs.getString("tablename");
                String indexName = idxRs.getString("indexname");
                String indexDef = idxRs.getString("indexdef");
                Table t = tableMap.get(schemaName2 + "." + tableName);
                if (t == null) {
                    continue;
                }
                Index importedIndex = new Index();
                importedIndex.setName(indexName);
                importedIndex.setUnique(indexDef != null && indexDef.toUpperCase().contains("UNIQUE"));
                importedIndex.setType(parseIndexType(indexDef));
                importedIndex.setColumnNames(parseIndexColumns(indexDef));
                t.getIndexes().add(importedIndex);
            }

            String commentsWhere = schemaNameNotNull == null
                ? "n.nspname not in ('pg_catalog','information_schema')"
                : "n.nspname = '" + escapeLiteral(schemaNameNotNull) + "'";

            ResultSet commentsRs = st.executeQuery("""
                select n.nspname as schema_name, c.relname as table_name, obj_description(c.oid) as table_comment
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                where c.relkind='r'
                  and %s
                """.formatted(commentsWhere));
            while (commentsRs.next()) {
                String tableComment = commentsRs.getString("table_comment");
                if (tableComment != null && !tableComment.isBlank()) {
                    String key = commentsRs.getString("schema_name") + "." + commentsRs.getString("table_name");
                    schema.getComments().put(key, tableComment);
                }
            }
        }
        return schema;
    }

    private String escapeLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private PostgresDataType mapDataType(String dataType) {
        if (dataType == null) {
            return PostgresDataType.TEXT;
        }
        return switch (dataType.toLowerCase()) {
            case "smallint" -> PostgresDataType.SMALLINT;
            case "integer" -> PostgresDataType.INTEGER;
            case "bigint" -> PostgresDataType.BIGINT;
            case "real" -> PostgresDataType.REAL;
            case "double precision" -> PostgresDataType.DOUBLE_PRECISION;
            case "numeric" -> PostgresDataType.NUMERIC;
            case "boolean" -> PostgresDataType.BOOLEAN;
            case "date" -> PostgresDataType.DATE;
            case "timestamp without time zone" -> PostgresDataType.TIMESTAMP;
            case "timestamp with time zone" -> PostgresDataType.TIMESTAMPTZ;
            case "uuid" -> PostgresDataType.UUID;
            case "json" -> PostgresDataType.JSON;
            case "jsonb" -> PostgresDataType.JSONB;
            case "bytea" -> PostgresDataType.BYTEA;
            case "character varying" -> PostgresDataType.VARCHAR;
            case "character" -> PostgresDataType.CHAR;
            default -> PostgresDataType.TEXT;
        };
    }

    private java.util.List<String> parseIndexColumns(String indexDef) {
        if (indexDef == null) {
            return java.util.List.of();
        }
        Matcher matcher = Pattern.compile("\\(([^\\)]*)\\)").matcher(indexDef);
        if (!matcher.find()) {
            return java.util.List.of();
        }
        String cols = matcher.group(1);
        return java.util.Arrays.stream(cols.split(","))
            .map(String::trim)
            .map(s -> s.replace("\"", ""))
            .toList();
    }

    private IndexType parseIndexType(String indexDef) {
        if (indexDef == null) {
            return IndexType.BTREE;
        }
        String upper = indexDef.toUpperCase();
        if (upper.contains(" USING GIN ")) {
            return IndexType.GIN;
        }
        if (upper.contains(" USING GIST ")) {
            return IndexType.GIST;
        }
        if (upper.contains(" USING HASH ")) {
            return IndexType.HASH;
        }
        if (upper.contains(" USING BRIN ")) {
            return IndexType.BRIN;
        }
        return IndexType.BTREE;
    }
}
