package com.zynth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zynth.generator.SqlGenerator;
import com.zynth.model.Column;
import com.zynth.model.DatabaseSchema;
import com.zynth.model.PostgresDataType;
import com.zynth.model.Table;
import org.junit.jupiter.api.Test;

class SqlGeneratorTest {

    @Test
    void generatesCreateTableStatement() {
        DatabaseSchema schema = new DatabaseSchema();
        Table table = new Table();
        table.setName("users");
        Column id = new Column("id", PostgresDataType.UUID);
        id.setPrimaryKey(true);
        id.setNullable(false);
        table.getColumns().add(id);
        schema.getTables().add(table);

        String ddl = new SqlGenerator().generate(schema);
        assertTrue(ddl.contains("CREATE TABLE"));
        assertTrue(ddl.contains("\"users\""));
        assertTrue(ddl.contains("PRIMARY KEY"));
    }
}
