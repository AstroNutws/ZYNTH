package com.zynth.generator;

import com.zynth.model.Column;
import com.zynth.model.DatabaseSchema;
import com.zynth.model.PostgresDataType;
import com.zynth.model.Table;

public class PrismaGenerator {

    public String generate(DatabaseSchema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("generator client {\n")
          .append("  provider = \"prisma-client-js\"\n")
          .append("}\n\n")
          .append("datasource db {\n")
          .append("  provider = \"postgresql\"\n")
          .append("  url      = env(\"DATABASE_URL\")\n")
          .append("}\n\n");

        for (Table table : schema.getTables()) {
            sb.append("model ").append(toModelName(table.getName())).append(" {\n");
            for (Column c : table.getColumns()) {
                sb.append("  ")
                  .append(c.getName())
                  .append(" ")
                  .append(mapType(c))
                  .append(c.isNullable() ? "?" : "")
                  .append(columnAttributes(c))
                  .append("\n");
            }
            sb.append("  @@map(\"").append(table.getName()).append("\")\n");
            sb.append("}\n\n");
        }
        return sb.toString().trim();
    }

    private String toModelName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return "Unnamed";
        }
        return Character.toUpperCase(tableName.charAt(0)) + tableName.substring(1);
    }

    private String columnAttributes(Column c) {
        StringBuilder sb = new StringBuilder();
        if (c.isPrimaryKey()) {
            sb.append(" @id");
        }
        if (c.isUnique()) {
            sb.append(" @unique");
        }
        return sb.toString();
    }

    private String mapType(Column c) {
        return switch (c.getDataType()) {
            case SERIAL, SMALLSERIAL, INTEGER, SMALLINT -> "Int";
            case BIGSERIAL, BIGINT -> "BigInt";
            case BOOLEAN -> "Boolean";
            case DATE, TIME, TIMETZ, TIMESTAMP, TIMESTAMPTZ -> "DateTime";
            case DECIMAL, NUMERIC, REAL, DOUBLE_PRECISION, MONEY -> "Decimal";
            case JSON, JSONB -> "Json";
            case BYTEA -> "Bytes";
            case UUID, VARCHAR, CHAR, TEXT, XML, CITEXT, HSTORE, INET, CIDR, MACADDR, MACADDR8, TSVECTOR, TSQUERY,
                POINT, LINE, LSEG, BOX, PATH, POLYGON, CIRCLE, INT4RANGE, INT8RANGE, NUMRANGE, TSRANGE, TSTZRANGE,
                DATERANGE, INT4MULTIRANGE, INT8MULTIRANGE, NUMMULTIRANGE, TSMULTIRANGE, TSTZMULTIRANGE, DATEMULTIRANGE,
                ENUM, OID, REGCLASS, GEOMETRY, GEOGRAPHY, ARRAY -> "String";
        };
    }
}
