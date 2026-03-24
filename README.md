# Zynth Schema Designer v1.0.0

Zynth is a JavaFX desktop schema designer for PostgreSQL and Supabase. It gives you visual modeling, table/column editing, relationship mapping, and live SQL/Prisma generation in a release-focused desktop workflow.

## Core capabilities

- Visual canvas with draggable tables and relationship lines
- Schema-grouped explorer with table and column tree
- Table editor (name/schema/realtime)
- Column editor (type/default/nullability/PK/unique/ENUM/FK)
- Relationship builder dialog
- Live code generation:
  - SQL DDL
  - Prisma schema
  - migration diff from baseline
- Undo/redo workflow (`Ctrl+Z` / `Ctrl+Y`)
- Save/load project format: `.zynth`
- Optional database integration:
  - Connect to PostgreSQL/Supabase
  - Import schema
  - Apply generated SQL to connected DB
  - Backup generated SQL

## v1.0.0 UX behavior

- Fixed panel layout (Explorer + right panel are always visible and locked)
- Add Table now:
  - uses active schema filter when applicable
  - places new table in a non-overlapping position
  - auto-centers camera on the new table
- Canvas helpers:
  - `Center Selected` toolbar button
  - `Reset View` toolbar button
  - `Ctrl+F` centers on selected table
  - `Ctrl+0` resets zoom/pan
- Branded icon loading supports:
  - `src/logo/logo.ico`
  - `src/logo/logo.png`
  - `src/logo/logo.jpg`

## Run locally

```powershell
cd "C:\Users\Administrator\Documents\ZYNTH"
mvn javafx:run
```

Alternative:

```powershell
.\run-zynth.bat
```

## Build and test

```powershell
cd "C:\Users\Administrator\Documents\ZYNTH"
mvn clean test package
```

Expected artifact:

```text
target\zynth-schema-designer-1.0.0.jar
```

## Build Windows EXE (exact commands)

### Recommended (with custom runtime)

```powershell
cd "C:\Users\Administrator\Documents\ZYNTH"

jlink `
  --module-path "$env:JAVA_HOME\jmods" `
  --add-modules java.base,java.desktop,java.sql,javafx.controls,javafx.graphics `
  --strip-debug `
  --no-header-files `
  --no-man-pages `
  --compress=2 `
  --output target\runtime

jpackage `
  --type exe `
  --name ZynthSchemaDesigner `
  --app-version 1.0.0 `
  --input target `
  --main-jar zynth-schema-designer-1.0.0.jar `
  --main-class com.zynth.app.ZynthApp `
  --runtime-image target\runtime `
  --icon src\logo\logo.ico `
  --dest target\release `
  --vendor "Zynth" `
  --description "Zynth Schema Designer for PostgreSQL and Supabase" `
  --win-menu `
  --win-shortcut `
  --win-dir-chooser `
  --win-per-user-install
```

### Fast one-line EXE command (no runtime-image step)

```powershell
cd "C:\Users\Administrator\Documents\ZYNTH"
jpackage --type exe --name ZynthSchemaDesigner --app-version 1.0.0 --input target --main-jar zynth-schema-designer-1.0.0.jar --main-class com.zynth.app.ZynthApp --icon src\logo\logo.ico --dest target\release --vendor "Zynth" --description "Zynth Schema Designer for PostgreSQL and Supabase" --win-menu --win-shortcut --win-dir-chooser --win-per-user-install
```

Output directory:

```text
target\release\
```

## DB connection quick reference

JDBC URL format:

```text
jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
```

Typical flow:

1. Click `Connect Supabase`
2. Enter JDBC URL, username, password
3. Connect only or connect + import
4. Use `Select Schema From DB` for scoped import
5. Use `Apply To Database` for generated SQL deployment

## Project structure

- `src/main/java/com/zynth/app/` - desktop app + canvas
- `src/main/java/com/zynth/model/` - schema model
- `src/main/java/com/zynth/generator/` - SQL/Prisma generators
- `src/main/java/com/zynth/io/` - persistence/import/connect helpers
- `src/main/resources/zynth-theme.css` - dark theme
- `src/logo/` - app icons
- `tutorial.md` - release packaging steps
- `requirements.md` - runtime/build requirements

## Contributors

- AstronNutws
- Gab.Dev
- Joshua Gabriel De Leon
