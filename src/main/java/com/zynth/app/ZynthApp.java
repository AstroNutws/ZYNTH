package com.zynth.app;

import com.zynth.generator.PrismaGenerator;
import com.zynth.generator.SqlGenerator;
import com.zynth.io.ConnectionProfileStore;
import com.zynth.io.SchemaProjectStore;
import com.zynth.io.SupabaseImporter;
import com.zynth.model.Column;
import com.zynth.model.DatabaseSchema;
import com.zynth.model.PostgresDataType;
import com.zynth.model.Relationship;
import com.zynth.model.RelationshipType;
import com.zynth.model.Table;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ZynthApp extends Application {
    private static final String APP_VERSION = "1.0";
    private final DatabaseSchema schema = seedSchema();
    private final SchemaProjectStore store = new SchemaProjectStore();
    private final SqlGenerator sqlGenerator = new SqlGenerator();
    private final PrismaGenerator prismaGenerator = new PrismaGenerator();
    private final SupabaseImporter supabaseImporter = new SupabaseImporter();
    private final ConnectionProfileStore profileStore =
        new ConnectionProfileStore(Path.of(System.getProperty("user.dir"), ".zynth-connection.json"));
    private final TreeView<String> explorerTree = new TreeView<>();
    private final Map<String, TreeItem<String>> tableNodes = new HashMap<>();
    private final TextArea codePreview = new TextArea();
    private final TextArea sqlHelper = new TextArea();
    private final TextField tableNameField = new TextField();
    private final TextField tableSchemaField = new TextField();
    private final CheckBox tableRealtimeCheck = new CheckBox("Enable Supabase Realtime");
    private final ListView<Column> columnList = new ListView<>();
    private final TextField colNameField = new TextField();
    private final ComboBox<PostgresDataType> colTypeCombo = new ComboBox<>();
    private final TextField colDefaultField = new TextField();
    private final TextField enumNameField = new TextField();
    private final TextField enumValuesField = new TextField();
    private final ComboBox<String> fkTableCombo = new ComboBox<>();
    private final ComboBox<String> fkColumnCombo = new ComboBox<>();
    private final CheckBox colNullableCheck = new CheckBox("Nullable");
    private final CheckBox colPkCheck = new CheckBox("Primary key");
    private final CheckBox colUniqueCheck = new CheckBox("Unique");
    private final TextField searchField = new TextField();
    private final ComboBox<String> generatorSelect = new ComboBox<>();
    private final ComboBox<String> schemaSelect = new ComboBox<>();
    private final Label statsLabel = new Label();
    private final Label healthLabel = new Label();
    private final Label dbStatusLabel = new Label("DB: Not connected");
    private final TextArea insightArea = new TextArea();
    private Button selectSchemaFromDbBtn;
    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private final ObjectMapper historyMapper = new ObjectMapper();
    private DiagramCanvas canvas;
    private Table selectedTable;
    private String currentGenerator = "SQL";
    private ConnectionProfileStore.SavedProfile activeProfile;
    private String baselineSchemaJson;
    private Button applyToDbBtn;
    private Button connectBtn;
    private Button disconnectBtn;
    private Button backupBtn;
    private Button functionsBtn;
    private VBox explorerPanel;
    private TabPane inspectorTabs;
    private SplitPane outerSplit;
    private SplitPane centerSplit;
    private BorderPane canvasPane;

    @Override
    public void start(Stage stage) {
        stage.getIcons().add(loadAppLogo());
        showSplashThenMain(stage);
    }

    private void showSplashThenMain(Stage stage) {
        Label logo = new Label("ZYNTH");
        logo.getStyleClass().add("zynth-logo");
        Label sub = new Label("Synth your schema. Ship faster.");
        sub.getStyleClass().add("zynth-sub");
        Label loading = new Label("Booting workspace...");
        loading.setStyle("-fx-text-fill:#9bc8ff; -fx-font-size:14;");
        ProgressBar progress = new ProgressBar();
        progress.setPrefWidth(420);
        progress.setProgress(0.12);
        VBox splash = new VBox(14, logo, sub, progress, loading);
        splash.setAlignment(Pos.CENTER);
        splash.setStyle("-fx-background-color: radial-gradient(radius 120%, #101a2e, #090f1c);");
        Scene splashScene = new Scene(splash, 960, 560);
        stage.setScene(splashScene);
        stage.setTitle("Zynth Schema Designer");
        stage.show();
        stage.centerOnScreen();

        Timeline timeline = new Timeline(
            new KeyFrame(Duration.seconds(0.6), e -> {
                progress.setProgress(0.35);
                loading.setText("Loading schema engine...");
            }),
            new KeyFrame(Duration.seconds(1.2), e -> {
                progress.setProgress(0.65);
                loading.setText("Preparing visual editor...");
            }),
            new KeyFrame(Duration.seconds(1.8), e -> {
                progress.setProgress(0.92);
                loading.setText("Almost ready...");
            }),
            new KeyFrame(Duration.seconds(2.4), e -> buildMainStage(stage))
        );
        timeline.play();
    }

    private void buildMainStage(Stage stage) {
        canvas = new DiagramCanvas(schema);
        canvas.setOnSelectionChanged(this::onTableSelected);
        canvas.setOnSchemaChanged(this::onSchemaChanged);

        refreshExplorer();
        refreshSchemaChoices();
        refreshStats();
        configureEditors();

        explorerPanel = new VBox(8, new Label("Explorer"), searchField, explorerTree);
        explorerPanel.setPadding(new Insets(8));
        explorerPanel.setMinWidth(210);
        explorerPanel.setPrefWidth(280);
        explorerPanel.getStyleClass().add("panel");
        VBox.setVgrow(explorerTree, Priority.ALWAYS);

        VBox properties = createPropertiesPanel();
        VBox right = createCodePanel();
        ScrollPane propertiesScroll = new ScrollPane(properties);
        propertiesScroll.setFitToWidth(true);
        propertiesScroll.setFitToHeight(true);
        ScrollPane codeScroll = new ScrollPane(right);
        codeScroll.setFitToWidth(true);
        codeScroll.setFitToHeight(true);
        inspectorTabs = new TabPane();
        Tab propertiesTab = new Tab("Properties", propertiesScroll);
        propertiesTab.setClosable(false);
        Tab codeTab = new Tab("Code", codeScroll);
        codeTab.setClosable(false);
        inspectorTabs.getTabs().addAll(propertiesTab, codeTab);
        inspectorTabs.setMinWidth(360);
        inspectorTabs.setPrefWidth(430);

        centerSplit = new SplitPane();
        canvasPane = new BorderPane(canvas);
        canvas.widthProperty().bind(canvasPane.widthProperty());
        canvas.heightProperty().bind(canvasPane.heightProperty());
        centerSplit.getItems().addAll(canvasPane, inspectorTabs);
        centerSplit.setDividerPositions(0.72);

        outerSplit = new SplitPane(explorerPanel, centerSplit);
        outerSplit.setDividerPositions(0.18);

        VBox toolbar = createToolbar(stage);
        BorderPane root = new BorderPane(outerSplit, toolbar, null, createStatusBar(), null);
        root.setStyle("-fx-background-color: #0f1522;");

        Scene scene = new Scene(root, 1460, 860);
        String css = getClass().getResource("/zynth-theme.css").toExternalForm();
        scene.getStylesheets().add(css);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE) {
                canvas.setSpacePanEnabled(true);
            }
            if (e.isControlDown() && e.getCode() == KeyCode.Z) {
                undo();
            }
            if (e.isControlDown() && e.getCode() == KeyCode.Y) {
                redo();
            }
            if (e.isControlDown() && e.getCode() == KeyCode.DIGIT0) {
                canvas.resetView();
            }
            if (e.isControlDown() && e.getCode() == KeyCode.F && selectedTable != null) {
                canvas.focusOnTable(selectedTable);
            }
        });
        scene.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.SPACE) {
                canvas.setSpacePanEnabled(false);
            }
        });
        stage.setScene(scene);
        stage.setTitle("Zynth Schema Designer v" + APP_VERSION);
        stage.setMinWidth(1160);
        stage.setMinHeight(700);
        stage.setMaximized(true);
        lockSplitPaneDividers();
        stage.centerOnScreen();
        stage.show();
        baselineSchemaJson = snapshotSchemaJson();
        undoStack.clear();
        redoStack.clear();
        maybePromptConnectSavedProfile(stage);
        canvas.redraw();
    }

    private VBox createToolbar(Stage stage) {
        ComboBox<String> templateCombo = new ComboBox<>();
        templateCombo.getItems().addAll("Blank table", "Supabase Profiles", "Supabase Audit Log", "Supabase Realtime Messages");
        templateCombo.setValue("Blank table");
        Button addTable = new Button("Add Table");
        Button deleteTable = new Button("Delete Selected");
        Button autoLayout = new Button("Auto Layout");
        Button addRelationship = new Button("Add Relationship");
        Button newSchema = new Button("New Schema");
        connectBtn = new Button("Connect Supabase");
        selectSchemaFromDbBtn = new Button("Select Schema From DB");
        applyToDbBtn = new Button("Apply To Database");
        disconnectBtn = new Button("Disconnect");
        backupBtn = new Button("Backup DB");
        functionsBtn = new Button("Postgres Functions");
        Button undoBtn = new Button("Undo");
        Button redoBtn = new Button("Redo");
        Button save = new Button("Save");
        Button open = new Button("Open");
        Button centerSelectedBtn = new Button("Center Selected");
        Button resetViewBtn = new Button("Reset View");

        addTable.setOnAction(e -> addTableFromTemplate(templateCombo.getValue()));
        deleteTable.setOnAction(e -> deleteSelectedTable());
        autoLayout.setOnAction(e -> {
            autoLayoutTables();
            onSchemaChanged();
        });
        addRelationship.setOnAction(e -> showAddRelationshipDialog(stage));
        newSchema.setOnAction(e -> createNewSchema());
        connectBtn.setOnAction(e -> showSupabaseConnectDialog(stage));
        selectSchemaFromDbBtn.setOnAction(e -> selectSchemaFromConnectedDatabase());
        selectSchemaFromDbBtn.setDisable(activeProfile == null);
        applyToDbBtn.setOnAction(e -> applySchemaToConnectedDatabase());
        applyToDbBtn.setDisable(true);
        disconnectBtn.setOnAction(e -> disconnectFromDatabase());
        backupBtn.setOnAction(e -> exportSchemaBackupSql(stage));
        functionsBtn.setOnAction(e -> showPostgresFunctionsDialog());
        undoBtn.setOnAction(e -> undo());
        redoBtn.setOnAction(e -> redo());
        save.setOnAction(e -> saveProject(stage));
        open.setOnAction(e -> openProject(stage));
        centerSelectedBtn.setOnAction(e -> {
            if (selectedTable != null) {
                canvas.focusOnTable(selectedTable);
            }
        });
        resetViewBtn.setOnAction(e -> canvas.resetView());
        Region spacer = new Region();
        spacer.setMinWidth(16);
        FlowPane row = new FlowPane(8, 8, addTable, templateCombo, deleteTable, autoLayout, addRelationship, schemaSelect, newSchema, spacer,
            connectBtn, selectSchemaFromDbBtn, applyToDbBtn, backupBtn, functionsBtn, disconnectBtn, undoBtn, redoBtn, save, open,
            centerSelectedBtn, resetViewBtn);
        row.getStyleClass().add("zynth-toolbar-wrap");
        row.setAlignment(Pos.CENTER_LEFT);

        VBox toolbarBox = new VBox(row);
        toolbarBox.getStyleClass().add("zynth-toolbar");
        return toolbarBox;
    }

    private HBox createStatusBar() {
        HBox status = new HBox(18, statsLabel, healthLabel, dbStatusLabel);
        status.setPadding(new Insets(6, 12, 8, 12));
        status.getStyleClass().add("zynth-status");
        return status;
    }

    private void configureEditors() {
        searchField.setPromptText("Search tables...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshExplorer());
        explorerTree.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        explorerTree.setShowRoot(false);
        explorerTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newItem) -> {
            if (newItem == null) {
                return;
            }
            String value = newItem.getValue();
            Table table = findTableByQualifiedName(value);
            if (table == null && newItem.getParent() != null) {
                table = findTableByQualifiedName(newItem.getParent().getValue());
            }
            if (table != null) {
                onTableSelected(table);
                canvas.focusOnTable(table);
            }
        });

        generatorSelect.getItems().addAll("SQL", "Prisma", "Migration Diff");
        generatorSelect.setValue("SQL");
        generatorSelect.setOnAction(e -> {
            currentGenerator = generatorSelect.getValue();
            refreshCodePreview();
        });
        codePreview.setEditable(false);
        codePreview.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12;");
        refreshCodePreview();

        colTypeCombo.setItems(FXCollections.observableArrayList(PostgresDataType.values()));
        colTypeCombo.setValue(PostgresDataType.TEXT);
        columnList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Column item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String fk = item.getReferencesTable() == null ? "" : " -> " + item.getReferencesTable() + "." + item.getReferencesColumn();
                    setText(item.getName() + " : " + item.getDataType() + fk);
                }
            }
        });
        columnList.getStyleClass().add("column-list");
        columnList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> bindColumn(newV));
        schemaSelect.setOnAction(e -> {
            onSchemaChanged();
            canvas.setSchemaFilter(activeSchemaFilter());
        });
    }

    private VBox createCodePanel() {
        ComboBox<String> helperCombo = new ComboBox<>();
        helperCombo.getItems().addAll("Enable RLS", "Policy: user owns row", "Timestamp trigger", "Enable Realtime");
        helperCombo.setValue("Enable RLS");
        helperCombo.setOnAction(e -> updateSqlHelper(helperCombo.getValue()));
        updateSqlHelper(helperCombo.getValue());
        sqlHelper.setEditable(false);
        sqlHelper.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12;");
        VBox right = new VBox(8, new Label("Code Preview"), generatorSelect, codePreview, new Label("Supabase Helper Snippets"), helperCombo,
            sqlHelper);
        right.setPadding(new Insets(8));
        right.setMinWidth(360);
        right.setPrefWidth(420);
        right.getStyleClass().add("panel");
        VBox.setVgrow(codePreview, Priority.ALWAYS);
        VBox.setVgrow(sqlHelper, Priority.ALWAYS);
        return right;
    }

    private VBox createPropertiesPanel() {
        tableNameField.setPromptText("Table name");
        tableSchemaField.setPromptText("Schema");
        enumNameField.setPromptText("Enum type name (optional)");
        enumValuesField.setPromptText("Enum values: todo,doing,done");
        fkTableCombo.setPromptText("FK table");
        fkColumnCombo.setPromptText("FK column");

        Button applyTable = new Button("Apply Table");
        applyTable.setOnAction(e -> {
            if (selectedTable == null) {
                return;
            }
            pushUndoState();
            selectedTable.setName(safeName(tableNameField.getText(), selectedTable.getName()));
            selectedTable.setSchema(safeName(tableSchemaField.getText(), "public"));
            selectedTable.setRealtimeEnabled(tableRealtimeCheck.isSelected());
            onSchemaChanged();
        });

        Button addColumn = new Button("Add Column");
        addColumn.setOnAction(e -> {
            if (selectedTable == null) {
                return;
            }
            pushUndoState();
            Column c = new Column("column_" + (selectedTable.getColumns().size() + 1), PostgresDataType.TEXT);
            selectedTable.getColumns().add(c);
            bindTableToProperties(selectedTable);
            columnList.getSelectionModel().select(c);
            onSchemaChanged();
        });

        Button deleteColumn = new Button("Delete Column");
        deleteColumn.setOnAction(e -> {
            if (selectedTable == null) {
                return;
            }
            Column c = columnList.getSelectionModel().getSelectedItem();
            if (c != null) {
                pushUndoState();
                selectedTable.getColumns().remove(c);
                bindTableToProperties(selectedTable);
                onSchemaChanged();
            }
        });

        Button applyColumn = new Button("Apply Column");
        applyColumn.setOnAction(e -> applyColumnChanges());

        fkTableCombo.setOnAction(e -> refreshFkColumns());
        FlowPane colActions = new FlowPane(10, 8, addColumn, deleteColumn, applyColumn);
        colActions.setAlignment(Pos.CENTER_LEFT);
        insightArea.setEditable(false);
        insightArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13;");

        VBox tableBox = new VBox(10,
            new Label("Table name"), tableNameField,
            new Label("Schema"), tableSchemaField,
            tableRealtimeCheck,
            applyTable
        );
        tableBox.setFillWidth(true);

        VBox columnsBox = new VBox(10,
            new Label("Columns"),
            columnList,
            colActions,
            new Label("Column name"), colNameField,
            new Label("Data type"), colTypeCombo,
            new Label("Default value"), colDefaultField,
            colNullableCheck, colPkCheck, colUniqueCheck
        );
        columnsBox.setFillWidth(true);
        VBox.setVgrow(columnList, Priority.ALWAYS);
        columnList.setPrefHeight(280);

        VBox enumFkBox = new VBox(10,
            new Label("ENUM (if type = ENUM)"),
            enumNameField,
            enumValuesField,
            new Label("Foreign key (optional)"),
            fkTableCombo,
            fkColumnCombo,
            new Label("Zynth Insights"),
            insightArea
        );
        enumFkBox.setFillWidth(true);
        VBox.setVgrow(insightArea, Priority.ALWAYS);

        Accordion accordion = new Accordion();
        TitledPane tablePane = new TitledPane("Table", tableBox);
        TitledPane columnsPane = new TitledPane("Columns", columnsBox);
        TitledPane extrasPane = new TitledPane("ENUM / Foreign Key / Insights", enumFkBox);
        accordion.getPanes().addAll(tablePane, columnsPane, extrasPane);
        accordion.setExpandedPane(columnsPane);

        VBox wrapper = new VBox(8, accordion);
        wrapper.setPadding(new Insets(10));
        wrapper.setMinWidth(350);
        wrapper.setPrefWidth(420);
        wrapper.getStyleClass().add("panel");

        tableNameField.setMaxWidth(Double.MAX_VALUE);
        tableSchemaField.setMaxWidth(Double.MAX_VALUE);
        colNameField.setMaxWidth(Double.MAX_VALUE);
        colTypeCombo.setMaxWidth(Double.MAX_VALUE);
        colDefaultField.setMaxWidth(Double.MAX_VALUE);
        enumNameField.setMaxWidth(Double.MAX_VALUE);
        enumValuesField.setMaxWidth(Double.MAX_VALUE);
        fkTableCombo.setMaxWidth(Double.MAX_VALUE);
        fkColumnCombo.setMaxWidth(Double.MAX_VALUE);

        colNameField.setPrefHeight(42);
        colDefaultField.setPrefHeight(42);
        enumNameField.setPrefHeight(42);
        enumValuesField.setPrefHeight(42);
        return wrapper;
    }

    private void applyColumnChanges() {
        Column c = columnList.getSelectionModel().getSelectedItem();
        if (c == null) {
            return;
        }
        pushUndoState();
        c.setName(safeName(colNameField.getText(), c.getName()));
        c.setDataType(colTypeCombo.getValue() == null ? PostgresDataType.TEXT : colTypeCombo.getValue());
        c.setDefaultValue(colDefaultField.getText());
        c.setNullable(colNullableCheck.isSelected());
        c.setPrimaryKey(colPkCheck.isSelected());
        c.setUnique(colUniqueCheck.isSelected());
        c.setEnumName(blankToNull(enumNameField.getText()));
        c.setEnumValues(parseCsv(enumValuesField.getText()));
        String fk = fkTableCombo.getValue();
        if (fk != null && fk.contains(".")) {
            c.setReferencesSchema(fk.split("\\.", 2)[0]);
            c.setReferencesTable(fk.split("\\.", 2)[1]);
            c.setReferencesColumn(blankToNull(fkColumnCombo.getValue()));
        } else {
            c.setReferencesSchema(null);
            c.setReferencesTable(null);
            c.setReferencesColumn(null);
        }
        bindTableToProperties(selectedTable);
        onSchemaChanged();
    }

    private void refreshExplorer() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String filter = activeSchemaFilter();
        TreeItem<String> root = new TreeItem<>("root");
        tableNodes.clear();
        Map<String, TreeItem<String>> schemas = new LinkedHashMap<>();

        for (Table t : schema.getTables()) {
            if (!"ALL".equalsIgnoreCase(filter) && !t.getSchema().equalsIgnoreCase(filter)) {
                continue;
            }
            String tableName = t.getSchema() + "." + t.getName();
            if (!q.isEmpty()) {
                boolean matchTable = tableName.toLowerCase(Locale.ROOT).contains(q);
                boolean matchColumn = t.getColumns().stream().anyMatch(c -> c.getName().toLowerCase(Locale.ROOT).contains(q));
                if (!matchTable && !matchColumn) {
                    continue;
                }
            }
            TreeItem<String> schemaNode = schemas.computeIfAbsent(t.getSchema(), s -> {
                TreeItem<String> node = new TreeItem<>(s);
                root.getChildren().add(node);
                return node;
            });
            TreeItem<String> tableNode = new TreeItem<>(tableName);
            schemaNode.getChildren().add(tableNode);
            tableNodes.put(tableName, tableNode);
            for (Column c : t.getColumns()) {
                tableNode.getChildren().add(new TreeItem<>("  - " + c.getName()));
            }
        }
        explorerTree.setRoot(root);
        for (TreeItem<String> schemaNode : root.getChildren()) {
            schemaNode.setExpanded(true);
        }
    }

    private void refreshCodePreview() {
        DatabaseSchema filtered = buildFilteredSchemaForCurrentView();
        if ("Prisma".equals(currentGenerator)) {
            codePreview.setText(prismaGenerator.generate(filtered));
            return;
        }
        if ("Migration Diff".equals(currentGenerator)) {
            codePreview.setText(generateMigrationDiff(filtered));
            return;
        }
        codePreview.setText(sqlGenerator.generate(filtered));
    }

    private void onTableSelected(Table table) {
        selectedTable = table;
        bindTableToProperties(table);
        if (table != null) {
            String key = table.getSchema() + "." + table.getName();
            TreeItem<String> node = tableNodes.get(key);
            if (node != null) {
                explorerTree.getSelectionModel().select(node);
            }
        }
        updateInsights();
        canvas.redraw();
    }

    private void bindTableToProperties(Table table) {
        refreshFkTables();
        if (table == null) {
            tableNameField.setText("");
            tableSchemaField.setText("public");
            tableRealtimeCheck.setSelected(false);
            columnList.getItems().clear();
            bindColumn(null);
            return;
        }
        tableNameField.setText(table.getName());
        tableSchemaField.setText(table.getSchema());
        tableRealtimeCheck.setSelected(table.isRealtimeEnabled());
        columnList.getItems().setAll(table.getColumns());
        if (!table.getColumns().isEmpty()) {
            columnList.getSelectionModel().select(0);
        } else {
            bindColumn(null);
        }
    }

    private void bindColumn(Column c) {
        boolean has = c != null;
        colNameField.setDisable(!has);
        colTypeCombo.setDisable(!has);
        colDefaultField.setDisable(!has);
        colNullableCheck.setDisable(!has);
        colPkCheck.setDisable(!has);
        colUniqueCheck.setDisable(!has);
        enumNameField.setDisable(!has);
        enumValuesField.setDisable(!has);
        fkTableCombo.setDisable(!has);
        fkColumnCombo.setDisable(!has);
        if (!has) {
            colNameField.setText("");
            colTypeCombo.setValue(PostgresDataType.TEXT);
            colDefaultField.setText("");
            enumNameField.setText("");
            enumValuesField.setText("");
            fkTableCombo.setValue(null);
            fkColumnCombo.setValue(null);
            colNullableCheck.setSelected(true);
            colPkCheck.setSelected(false);
            colUniqueCheck.setSelected(false);
            return;
        }
        colNameField.setText(c.getName());
        colTypeCombo.setValue(c.getDataType());
        colDefaultField.setText(c.getDefaultValue() == null ? "" : c.getDefaultValue());
        enumNameField.setText(c.getEnumName() == null ? "" : c.getEnumName());
        enumValuesField.setText(String.join(",", c.getEnumValues()));
        if (c.getReferencesTable() != null) {
            fkTableCombo.setValue((c.getReferencesSchema() == null ? "public" : c.getReferencesSchema()) + "." + c.getReferencesTable());
            refreshFkColumns();
            fkColumnCombo.setValue(c.getReferencesColumn());
        } else {
            fkTableCombo.setValue(null);
            fkColumnCombo.setValue(null);
        }
        colNullableCheck.setSelected(c.isNullable());
        colPkCheck.setSelected(c.isPrimaryKey());
        colUniqueCheck.setSelected(c.isUnique());
    }

    private void refreshFkTables() {
        String filter = activeSchemaFilter();
        fkTableCombo.getItems().setAll(schema.getTables().stream()
            .filter(t -> "ALL".equalsIgnoreCase(filter) || t.getSchema().equalsIgnoreCase(filter))
            .map(t -> t.getSchema() + "." + t.getName()).toList());
    }

    private void refreshFkColumns() {
        String selected = fkTableCombo.getValue();
        if (selected == null || !selected.contains(".")) {
            fkColumnCombo.getItems().clear();
            return;
        }
        Table t = schema.getTables().stream().filter(x -> (x.getSchema() + "." + x.getName()).equals(selected)).findFirst().orElse(null);
        fkColumnCombo.getItems().setAll(t == null ? java.util.List.of() : t.getColumns().stream().map(Column::getName).toList());
    }

    private void onSchemaChanged() {
        String filter = activeSchemaFilter();
        if (selectedTable != null && !"ALL".equalsIgnoreCase(filter) && !selectedTable.getSchema().equalsIgnoreCase(filter)) {
            selectedTable = null;
            bindTableToProperties(null);
        }
        refreshExplorer();
        refreshSchemaChoices();
        refreshStats();
        updateInsights();
        canvas.setSchemaFilter(activeSchemaFilter());
        refreshCodePreview();
        canvas.redraw();
    }

    private void addTableFromTemplate(String template) {
        pushUndoState();
        Table t = new Table();
        String activeFilter = activeSchemaFilter();
        if (selectedTable != null) {
            t.setSchema(selectedTable.getSchema());
        } else if (!"ALL".equalsIgnoreCase(activeFilter)) {
            t.setSchema(activeFilter);
        } else {
            t.setSchema("public");
        }
        switch (template) {
            case "Supabase Profiles" -> {
                t.setName("profiles");
                t.getColumns().add(pkUuid("id", "gen_random_uuid()"));
                t.getColumns().add(new Column("full_name", PostgresDataType.TEXT));
                t.getColumns().add(ts("updated_at"));
                t.setRealtimeEnabled(true);
            }
            case "Supabase Audit Log" -> {
                t.setName("audit_logs");
                t.getColumns().add(pkBigSerial("id"));
                t.getColumns().add(new Column("payload", PostgresDataType.JSONB));
                t.getColumns().add(ts("created_at"));
            }
            case "Supabase Realtime Messages" -> {
                t.setName("messages");
                t.getColumns().add(pkBigSerial("id"));
                t.getColumns().add(new Column("room_id", PostgresDataType.UUID));
                t.getColumns().add(new Column("content", PostgresDataType.TEXT));
                t.getColumns().add(ts("created_at"));
                t.setRealtimeEnabled(true);
            }
            default -> {
                t.setName("table_" + (schema.getTables().size() + 1));
                t.getColumns().add(pkUuid("id", "gen_random_uuid()"));
            }
        }
        placeTableWithoutOverlap(t);
        schema.getTables().add(t);
        onTableSelected(t);
        onSchemaChanged();
        Platform.runLater(() -> {
            canvas.focusOnTable(t);
            canvas.requestFocus();
        });
    }

    private void lockSplitPaneDividers() {
        lockSplitPaneDivider(outerSplit, 0.18);
        lockSplitPaneDivider(centerSplit, 0.72);
    }

    private void lockSplitPaneDivider(SplitPane pane, double position) {
        if (pane == null || pane.getDividers().isEmpty()) {
            return;
        }
        pane.setDividerPositions(position);
        pane.getDividers().get(0).positionProperty().addListener((obs, oldV, newV) -> {
            if (Math.abs(newV.doubleValue() - position) > 0.0001) {
                Platform.runLater(() -> pane.setDividerPositions(position));
            }
        });
        Platform.runLater(() -> pane.lookupAll(".split-pane-divider").forEach(node -> {
            node.setMouseTransparent(true);
            node.setDisable(true);
        }));
    }

    private void placeTableWithoutOverlap(Table candidate) {
        double width = estimateTableWidth(candidate);
        double height = Math.max(220, 44 + candidate.getColumns().size() * 22);
        candidate.setWidth(width);
        candidate.setHeight(height);
        final double margin = 56;
        final double stepX = 70;
        final double stepY = 60;

        for (int ring = 0; ring < 100; ring++) {
            double baseX = 80 + ring * stepX;
            double baseY = 80 + ring * stepY;
            if (!intersectsAnyTable(baseX, baseY, width, height, margin, null)) {
                candidate.setX(baseX);
                candidate.setY(baseY);
                return;
            }
            for (Table existing : schema.getTables()) {
                double rightX = existing.getX() + existing.getWidth() + margin;
                double sameY = existing.getY();
                if (!intersectsAnyTable(rightX, sameY, width, height, margin, null)) {
                    candidate.setX(rightX);
                    candidate.setY(sameY);
                    return;
                }
                double belowX = existing.getX();
                double belowY = existing.getY() + existing.getHeight() + margin;
                if (!intersectsAnyTable(belowX, belowY, width, height, margin, null)) {
                    candidate.setX(belowX);
                    candidate.setY(belowY);
                    return;
                }
            }
        }

        // Last-resort placement far to the right if project is unusually dense.
        candidate.setX(120 + schema.getTables().size() * 120);
        candidate.setY(120 + schema.getTables().size() * 80);
    }

    private boolean intersectsAnyTable(double x, double y, double w, double h, double margin, Table ignore) {
        double left = x - margin;
        double top = y - margin;
        double right = x + w + margin;
        double bottom = y + h + margin;
        String filter = activeSchemaFilter();
        for (Table t : schema.getTables()) {
            if (t == ignore) {
                continue;
            }
            if (!"ALL".equalsIgnoreCase(filter) && !t.getSchema().equalsIgnoreCase(filter)) {
                continue;
            }
            double effectiveWidth = Math.max(t.getWidth(), estimateTableWidth(t));
            double effectiveHeight = Math.max(t.getHeight(), 44 + t.getColumns().size() * 20);
            double tLeft = t.getX();
            double tTop = t.getY();
            double tRight = t.getX() + effectiveWidth;
            double tBottom = t.getY() + effectiveHeight;
            boolean overlaps = left < tRight && right > tLeft && top < tBottom && bottom > tTop;
            if (overlaps) {
                return true;
            }
        }
        return false;
    }

    private void saveProject(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zynth Project", "*.zynth"));
        File f = chooser.showSaveDialog(stage);
        if (f != null) {
            try {
                store.save(f.toPath(), schema);
            } catch (Exception ex) {
                showError("Save failed", ex.getMessage());
            }
        }
    }

    private void openProject(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zynth Project", "*.zynth"));
        File f = chooser.showOpenDialog(stage);
        if (f != null) {
            try {
                DatabaseSchema loaded = store.load(f.toPath());
                schema.setName(loaded.getName());
                schema.setTables(loaded.getTables());
                schema.setRelationships(loaded.getRelationships());
                schema.setComments(loaded.getComments());
                selectedTable = null;
                bindTableToProperties(null);
                baselineSchemaJson = snapshotSchemaJson();
                undoStack.clear();
                redoStack.clear();
                onSchemaChanged();
            } catch (Exception ex) {
                showError("Open failed", ex.getMessage());
            }
        }
    }

    private void showSupabaseConnectDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Connect to Supabase/PostgreSQL");
        dialog.getIcons().add(loadAppLogo());

        TextField url = new TextField("jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require");
        TextField user = new TextField("postgres.<project-ref>");
        PasswordField pass = new PasswordField();
        pass.setPromptText("password");
        CheckBox remember = new CheckBox("Remember this connection");
        remember.setSelected(true);
        Button connectOnly = new Button("Connect");
        Button connectImport = new Button("Connect + Import Schema");
        Label info = new Label("Connection is optional. You can still design offline.");

        connectOnly.setOnAction(e -> {
            try {
                ConnectionProfileStore.SavedProfile profile = connectAndRemember(url.getText(), user.getText(), pass.getText(), remember.isSelected());
                setActiveProfile(profile);
                dialog.close();
            } catch (Exception ex) {
                showConnectionError(ex);
            }
        });

        connectImport.setOnAction(e -> {
            try {
                ConnectionProfileStore.SavedProfile profile = connectAndRemember(url.getText(), user.getText(), pass.getText(), remember.isSelected());
                setActiveProfile(profile);
                DatabaseSchema imported = supabaseImporter.importSchema(new SupabaseImporter.ConnectionInfo(profile.jdbcUrl(), profile.username(),
                    profile.password()));
                schema.setName(imported.getName());
                schema.setTables(imported.getTables());
                schema.setRelationships(imported.getRelationships());
                schema.setComments(imported.getComments());
                selectedTable = null;
                bindTableToProperties(null);
                autoLayoutTables();
                baselineSchemaJson = snapshotSchemaJson();
                undoStack.clear();
                redoStack.clear();
                onSchemaChanged();
                dialog.close();
            } catch (Exception ex) {
                showConnectionError(ex);
            }
        });

        HBox actions = new HBox(8, connectOnly, connectImport);
        VBox root = new VBox(10, new Label("JDBC URL"), url, new Label("Username"), user, new Label("Password"), pass, remember, info, actions);
        root.setPadding(new Insets(12));
        Scene scene = new Scene(root, 760, 360);
        scene.getStylesheets().add(getClass().getResource("/zynth-theme.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void deleteSelectedTable() {
        if (selectedTable != null) {
            pushUndoState();
            schema.getTables().remove(selectedTable);
            selectedTable = null;
            bindTableToProperties(null);
            onSchemaChanged();
        }
    }

    private void refreshSchemaChoices() {
        Set<String> schemas = new LinkedHashSet<>();
        schemas.add("ALL");
        for (Table t : schema.getTables()) {
            if (t.getSchema() != null && !t.getSchema().isBlank()) {
                schemas.add(t.getSchema());
            }
        }
        if (schemas.size() == 1) {
            // Empty project: keep "public" as a convenient default target
            schemas.add("public");
        }
        schemaSelect.getItems().setAll(schemas);
        if (schemaSelect.getValue() == null && !schemas.isEmpty()) {
            schemaSelect.setValue(schemas.iterator().next());
        }
    }

    private void createNewSchema() {
        TextInputDialog dialog = new TextInputDialog("app");
        dialog.setTitle("Create Schema");
        dialog.setHeaderText("Create a new PostgreSQL schema");
        dialog.setContentText("Schema name:");
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            pushUndoState();
            String name = safeName(result.get(), "public").toLowerCase(Locale.ROOT);
            if (!schemaSelect.getItems().contains(name)) {
                schemaSelect.getItems().add(name);
            }
            schemaSelect.setValue(name);
            if (selectedTable != null) {
                selectedTable.setSchema(name);
            }
            onSchemaChanged();
        }
    }

    private void autoLayoutTables() {
        Map<String, java.util.List<Table>> bySchema = new LinkedHashMap<>();
        for (Table t : schema.getTables()) {
            bySchema.computeIfAbsent(t.getSchema(), k -> new java.util.ArrayList<>()).add(t);
        }
        double schemaStartX = 60;
        for (var entry : bySchema.entrySet()) {
            java.util.List<Table> tables = entry.getValue();
            int cols = Math.max(1, (int) Math.ceil(Math.sqrt(tables.size())));
            double x = schemaStartX;
            double y = 70;
            int col = 0;
            double rowMaxHeight = 0;
            for (Table t : tables) {
                double h = Math.max(t.getHeight(), 44 + t.getColumns().size() * 20) + 40;
                double w = estimateTableWidth(t);
                t.setWidth(w);
                t.setHeight(h - 20);
                t.setX(x);
                t.setY(y);
                rowMaxHeight = Math.max(rowMaxHeight, h);
                col++;
                if (col >= cols) {
                    col = 0;
                    x = schemaStartX;
                    y += rowMaxHeight + 30;
                    rowMaxHeight = 0;
                } else {
                    x += Math.max(360, w + 90);
                }
            }
            schemaStartX += cols * 360 + 180;
        }
    }

    private double estimateTableWidth(Table t) {
        int maxChars = (t.getSchema() + "." + t.getName()).length();
        for (Column c : t.getColumns()) {
            maxChars = Math.max(maxChars, (c.getName() + " : " + c.getDataType()).length());
        }
        return Math.max(260, Math.min(460, maxChars * 8.2 + 40));
    }

    private void refreshStats() {
        int tableCount = schema.getTables().size();
        int relCount = schema.getRelationships().size();
        int colCount = schema.getTables().stream().mapToInt(t -> t.getColumns().size()).sum();
        statsLabel.setText("Tables: " + tableCount + "   Columns: " + colCount + "   Relationships: " + relCount);
        long noPk = schema.getTables().stream().filter(t -> t.getColumns().stream().noneMatch(Column::isPrimaryKey)).count();
        long realtime = schema.getTables().stream().filter(Table::isRealtimeEnabled).count();
        int health = Math.max(0, 100 - (int) (noPk * 8));
        healthLabel.setText("Schema Health: " + health + "%   Realtime tables: " + realtime);
    }

    private void updateInsights() {
        if (selectedTable == null) {
            insightArea.setText("Select a table to see tips.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Table: ").append(selectedTable.getSchema()).append(".").append(selectedTable.getName()).append("\n");
        sb.append(selectedTable.isRealtimeEnabled() ? "- Realtime enabled\n" : "- Realtime disabled\n");
        boolean hasPk = selectedTable.getColumns().stream().anyMatch(Column::isPrimaryKey);
        sb.append(hasPk ? "- Primary key: OK\n" : "- Add a primary key\n");
        selectedTable.getColumns().stream()
            .filter(c -> c.getName().endsWith("_id") && c.getReferencesTable() == null)
            .sorted(Comparator.comparing(Column::getName))
            .forEach(c -> sb.append("- ").append(c.getName()).append(": add foreign key target\n"));
        insightArea.setText(sb.toString().strip());
    }

    private void pushUndoState() {
        undoStack.push(snapshotSchemaJson());
        if (undoStack.size() > 100) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(snapshotSchemaJson());
        restoreSchemaFromJson(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(snapshotSchemaJson());
        restoreSchemaFromJson(redoStack.pop());
    }

    private String snapshotSchemaJson() {
        try {
            return historyMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void restoreSchemaFromJson(String json) {
        try {
            DatabaseSchema restored = historyMapper.readValue(json, DatabaseSchema.class);
            schema.setName(restored.getName());
            schema.setTables(restored.getTables());
            schema.setRelationships(restored.getRelationships());
            schema.setComments(restored.getComments());
            selectedTable = null;
            bindTableToProperties(null);
            onSchemaChanged();
        } catch (Exception e) {
            showError("History restore failed", e.getMessage());
        }
    }

    private String generateMigrationDiff(DatabaseSchema current) {
        if (baselineSchemaJson == null || baselineSchemaJson.isBlank()) {
            return "-- Baseline missing. Load or import a schema first.";
        }
        try {
            DatabaseSchema base = historyMapper.readValue(baselineSchemaJson, DatabaseSchema.class);
            java.util.Set<String> baseTables = base.getTables().stream().map(t -> t.getSchema() + "." + t.getName())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            java.util.Set<String> curTables = current.getTables().stream().map(t -> t.getSchema() + "." + t.getName())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            StringBuilder out = new StringBuilder();
            for (String t : curTables) {
                if (!baseTables.contains(t)) {
                    Table table = current.getTables().stream().filter(x -> (x.getSchema() + "." + x.getName()).equals(t)).findFirst().orElse(null);
                    if (table != null) {
                        out.append("-- New table: ").append(t).append("\n");
                        out.append(sqlGenerator.generateCreateTable(table)).append("\n\n");
                    }
                }
            }
            for (String t : baseTables) {
                if (!curTables.contains(t)) {
                    out.append("-- Dropped table: ").append(t).append("\n");
                    out.append("DROP TABLE ").append("\"").append(t.replace(".", "\".\"")).append("\";\n\n");
                }
            }
            for (Table cur : current.getTables()) {
                Table old = base.getTables().stream()
                    .filter(t -> t.getSchema().equals(cur.getSchema()) && t.getName().equals(cur.getName()))
                    .findFirst().orElse(null);
                if (old == null) {
                    continue;
                }
                java.util.Set<String> oldCols = old.getColumns().stream().map(Column::getName).collect(java.util.stream.Collectors.toSet());
                for (Column c : cur.getColumns()) {
                    if (!oldCols.contains(c.getName())) {
                        out.append("ALTER TABLE \"").append(cur.getSchema()).append("\".\"").append(cur.getName()).append("\" ")
                            .append("ADD COLUMN \"").append(c.getName()).append("\" ")
                            .append(c.getDataType().name()).append(";\n");
                    }
                }
            }
            return out.length() == 0 ? "-- No schema changes detected against baseline." : out.toString().trim();
        } catch (Exception e) {
            return "-- Failed to build migration diff: " + e.getMessage();
        }
    }

    private void updateSqlHelper(String helper) {
        String text = switch (helper) {
            case "Policy: user owns row" -> """
                alter table public.your_table enable row level security;
                create policy "Users can manage own rows"
                on public.your_table
                for all
                using (auth.uid() = user_id)
                with check (auth.uid() = user_id);
                """;
            case "Timestamp trigger" -> """
                create or replace function public.set_updated_at()
                returns trigger as $$
                begin
                  new.updated_at = now();
                  return new;
                end;
                $$ language plpgsql;
                """;
            case "Enable Realtime" -> """
                alter publication supabase_realtime add table public.your_table;
                """;
            default -> """
                alter table public.your_table enable row level security;
                """;
        };
        sqlHelper.setText(text.strip());
    }

    private String activeSchemaFilter() {
        return schemaSelect.getValue() == null ? "ALL" : schemaSelect.getValue();
    }

    private DatabaseSchema buildFilteredSchemaForCurrentView() {
        String filter = activeSchemaFilter();
        if ("ALL".equalsIgnoreCase(filter)) {
            return schema;
        }
        DatabaseSchema out = new DatabaseSchema();
        out.setName(schema.getName());
        out.setComments(schema.getComments());
        for (Table t : schema.getTables()) {
            if (filter.equalsIgnoreCase(t.getSchema())) {
                out.getTables().add(t);
            }
        }
        for (Relationship r : schema.getRelationships()) {
            Table src = schema.getTables().stream().filter(t -> t.getId().equals(r.getSourceTableId())).findFirst().orElse(null);
            Table dst = schema.getTables().stream().filter(t -> t.getId().equals(r.getTargetTableId())).findFirst().orElse(null);
            if (src != null && dst != null && filter.equalsIgnoreCase(src.getSchema()) && filter.equalsIgnoreCase(dst.getSchema())) {
                out.getRelationships().add(r);
            }
        }
        return out;
    }

    private void showAddRelationshipDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Add Relationship");
        ComboBox<String> srcTable = new ComboBox<>();
        ComboBox<String> srcCol = new ComboBox<>();
        ComboBox<String> dstTable = new ComboBox<>();
        ComboBox<String> dstCol = new ComboBox<>();
        ComboBox<RelationshipType> type = new ComboBox<>();
        type.getItems().setAll(RelationshipType.values());
        type.setValue(RelationshipType.MANY_TO_ONE);

        srcTable.getItems().setAll(schema.getTables().stream().map(t -> t.getSchema() + "." + t.getName()).toList());
        dstTable.getItems().setAll(srcTable.getItems());
        srcTable.setOnAction(e -> populateColumnsForTable(srcTable.getValue(), srcCol));
        dstTable.setOnAction(e -> populateColumnsForTable(dstTable.getValue(), dstCol));

        Button create = new Button("Create");
        create.setOnAction(e -> {
            Table s = findTableByQualifiedName(srcTable.getValue());
            Table d = findTableByQualifiedName(dstTable.getValue());
            if (s == null || d == null || srcCol.getValue() == null || dstCol.getValue() == null) {
                showError("Missing fields", "Select source and target table/column.");
                return;
            }
            pushUndoState();
            Relationship rel = new Relationship();
            rel.setSourceTableId(s.getId());
            rel.setSourceColumnName(srcCol.getValue());
            rel.setTargetTableId(d.getId());
            rel.setTargetColumnName(dstCol.getValue());
            rel.setType(type.getValue());
            schema.getRelationships().add(rel);

            for (Column c : s.getColumns()) {
                if (srcCol.getValue().equals(c.getName())) {
                    c.setReferencesSchema(d.getSchema());
                    c.setReferencesTable(d.getName());
                    c.setReferencesColumn(dstCol.getValue());
                    break;
                }
            }
            onSchemaChanged();
            dialog.close();
        });

        VBox root = new VBox(8, new Label("Source table"), srcTable, new Label("Source column"), srcCol,
            new Label("Target table"), dstTable, new Label("Target column"), dstCol, new Label("Type"), type, create);
        root.setPadding(new Insets(12));
        Scene scene = new Scene(root, 420, 460);
        scene.getStylesheets().add(getClass().getResource("/zynth-theme.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void populateColumnsForTable(String qualifiedName, ComboBox<String> target) {
        Table t = findTableByQualifiedName(qualifiedName);
        target.getItems().setAll(t == null ? java.util.List.of() : t.getColumns().stream().map(Column::getName).toList());
    }

    private Table findTableByQualifiedName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        return schema.getTables().stream().filter(t -> (t.getSchema() + "." + t.getName()).equals(qualifiedName)).findFirst().orElse(null);
    }

    private WritableImage createLogoImage() {
        Canvas iconCanvas = new Canvas(64, 64);
        GraphicsContext g = iconCanvas.getGraphicsContext2D();
        g.setFill(Color.web("#121b2f"));
        g.fillRoundRect(0, 0, 64, 64, 14, 14);
        g.setStroke(Color.web("#53d3ff"));
        g.setLineWidth(5);
        g.strokeLine(15, 16, 48, 16);
        g.strokeLine(48, 16, 15, 48);
        g.strokeLine(15, 48, 48, 48);
        g.setFill(Color.web("#8f7dff"));
        g.setFont(Font.font("Arial Black", 13));
        g.fillText("Z", 26, 38);
        WritableImage img = new WritableImage(64, 64);
        iconCanvas.snapshot(null, img);
        return img;
    }

    private Image loadAppLogo() {
        try {
            File icoFile = new File("src/logo/logo.ico");
            if (icoFile.exists()) {
                try (FileInputStream in = new FileInputStream(icoFile)) {
                    return new Image(in);
                }
            }
            File file = new File("src/logo/logo.png");
            if (file.exists()) {
                try (FileInputStream in = new FileInputStream(file)) {
                    return new Image(in);
                }
            }
            File jpgFile = new File("src/logo/logo.jpg");
            if (jpgFile.exists()) {
                try (FileInputStream in = new FileInputStream(jpgFile)) {
                    return new Image(in);
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return createLogoImage();
    }

    private void maybePromptConnectSavedProfile(Stage owner) {
        Optional<ConnectionProfileStore.SavedProfile> saved = profileStore.load();
        if (saved.isEmpty()) {
            return;
        }
        Alert ask = new Alert(Alert.AlertType.CONFIRMATION);
        ask.initOwner(owner);
        ask.setTitle("Saved Connection");
        ask.setHeaderText("Use saved database connection?");
        ask.setContentText("Choose Connect to use saved credentials, Skip for offline mode, or New to enter another connection.");
        ButtonType connect = new ButtonType("Connect");
        ButtonType skip = new ButtonType("Skip");
        ButtonType fresh = new ButtonType("New");
        ask.getButtonTypes().setAll(connect, skip, fresh);
        Optional<ButtonType> result = ask.showAndWait();
        if (result.isEmpty() || result.get() == skip) {
            return;
        }
        if (result.get() == fresh) {
            showSupabaseConnectDialog(owner);
            return;
        }
        try {
            ConnectionProfileStore.SavedProfile profile = saved.get();
            testConnection(profile.jdbcUrl(), profile.username(), profile.password());
            setActiveProfile(profile);
        } catch (Exception ex) {
            showConnectionError(ex);
        }
    }

    private ConnectionProfileStore.SavedProfile connectAndRemember(String jdbcUrl, String username, String password, boolean remember)
        throws Exception {
        String normalized = normalizeJdbcUrl(jdbcUrl);
        testConnection(normalized, username, password);
        ConnectionProfileStore.SavedProfile profile = new ConnectionProfileStore.SavedProfile(normalized, username, password);
        if (remember) {
            profileStore.save(profile);
        }
        return profile;
    }

    private void testConnection(String jdbcUrl, String username, String password) throws Exception {
        try (Connection ignored = DriverManager.getConnection(jdbcUrl, username, password)) {
            // successful open/close means valid credentials and network reachability
        }
    }

    private void setActiveProfile(ConnectionProfileStore.SavedProfile profile) {
        activeProfile = profile;
        dbStatusLabel.setText("DB: Connected (" + profile.username() + ")");
        if (selectSchemaFromDbBtn != null) {
            selectSchemaFromDbBtn.setDisable(false);
        }
        if (applyToDbBtn != null) {
            applyToDbBtn.setDisable(false);
        }
        if (disconnectBtn != null) {
            disconnectBtn.setDisable(false);
        }
    }

    private void disconnectFromDatabase() {
        activeProfile = null;
        dbStatusLabel.setText("DB: Disconnected");
        if (selectSchemaFromDbBtn != null) {
            selectSchemaFromDbBtn.setDisable(true);
        }
        if (applyToDbBtn != null) {
            applyToDbBtn.setDisable(true);
        }
        if (disconnectBtn != null) {
            disconnectBtn.setDisable(true);
        }
        if (backupBtn != null) {
            backupBtn.setDisable(false);
        }
        if (functionsBtn != null) {
            functionsBtn.setDisable(false);
        }

        // Clear everything (tables, relationships, comments) for a clean slate
        schema.setName("untitled");
        schema.getTables().clear();
        schema.getRelationships().clear();
        schema.getComments().clear();

        selectedTable = null;
        bindTableToProperties(null);

        // Reset view state
        schemaSelect.getItems().setAll("ALL", "public");
        schemaSelect.setValue("ALL");
        canvas.setSchemaFilter("ALL");

        baselineSchemaJson = snapshotSchemaJson();
        undoStack.clear();
        redoStack.clear();

        refreshExplorer();
        refreshSchemaChoices();
        refreshStats();
        updateInsights();
        refreshCodePreview();
        canvas.redraw();
    }

    private void exportSchemaBackupSql(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export SQL Backup");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQL File", "*.sql"));
        chooser.setInitialFileName("zynth-backup.sql");
        File f = chooser.showSaveDialog(owner);
        if (f == null) {
            return;
        }
        try {
            java.nio.file.Files.writeString(f.toPath(), sqlGenerator.generate(schema));
            new Alert(Alert.AlertType.INFORMATION, "Backup exported to:\n" + f.getAbsolutePath()).showAndWait();
        } catch (Exception ex) {
            showError("Backup failed", ex.getMessage());
        }
    }

    private void showPostgresFunctionsDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("PostgreSQL Function Templates");
        dialog.getIcons().add(loadAppLogo());
        ComboBox<String> templates = new ComboBox<>();
        templates.getItems().addAll("updated_at trigger", "soft delete function", "uuid default helper", "audit log trigger");
        templates.setValue("updated_at trigger");
        TextArea body = new TextArea();
        body.setEditable(false);
        body.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13;");
        Runnable refresh = () -> body.setText(functionTemplate(templates.getValue()));
        templates.setOnAction(e -> refresh.run());
        refresh.run();
        Button copyToPreview = new Button("Send To Code Preview");
        copyToPreview.setOnAction(e -> {
            generatorSelect.setValue("SQL");
            currentGenerator = "SQL";
            codePreview.setText(body.getText());
            dialog.close();
        });
        VBox root = new VBox(10, new Label("Template"), templates, body, copyToPreview);
        root.setPadding(new Insets(12));
        Scene scene = new Scene(root, 760, 520);
        scene.getStylesheets().add(getClass().getResource("/zynth-theme.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private String functionTemplate(String key) {
        return switch (key) {
            case "soft delete function" -> """
                create or replace function public.soft_delete_row()
                returns trigger as $$
                begin
                  new.deleted_at = now();
                  return new;
                end;
                $$ language plpgsql;
                """;
            case "uuid default helper" -> """
                create extension if not exists pgcrypto;
                -- use in tables:
                -- id uuid primary key default gen_random_uuid()
                """;
            case "audit log trigger" -> """
                create or replace function public.log_changes()
                returns trigger as $$
                begin
                  insert into public.audit_logs(table_name, action, payload, created_at)
                  values (tg_table_name, tg_op, to_jsonb(new), now());
                  return new;
                end;
                $$ language plpgsql;
                """;
            default -> """
                create or replace function public.set_updated_at()
                returns trigger as $$
                begin
                  new.updated_at = now();
                  return new;
                end;
                $$ language plpgsql;
                """;
        };
    }

    private void applySchemaToConnectedDatabase() {
        if (activeProfile == null) {
            showError("No active connection", "Connect to Supabase/PostgreSQL first.");
            return;
        }
        String sql = sqlGenerator.generate(schema);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Apply current generated SQL to connected database?");
        confirm.setHeaderText("This will execute schema SQL");
        Optional<ButtonType> picked = confirm.showAndWait();
        if (picked.isEmpty() || picked.get() != ButtonType.OK) {
            return;
        }
        try (Connection conn = DriverManager.getConnection(activeProfile.jdbcUrl(), activeProfile.username(), activeProfile.password());
            Statement st = conn.createStatement()) {
            for (String statement : sql.split(";")) {
                String s = statement.trim();
                if (!s.isBlank()) {
                    st.execute(s);
                }
            }
            new Alert(Alert.AlertType.INFORMATION, "Schema SQL applied successfully.").showAndWait();
        } catch (Exception ex) {
            showError("Apply failed", ex.getMessage());
        }
    }

    private void selectSchemaFromConnectedDatabase() {
        if (activeProfile == null) {
            showError("Not connected", "Connect to database first.");
            return;
        }
        try (Connection conn = DriverManager.getConnection(activeProfile.jdbcUrl(), activeProfile.username(), activeProfile.password());
            Statement st = conn.createStatement()) {
            var rs = st.executeQuery("""
                select schema_name
                from information_schema.schemata
                where schema_name not in ('pg_catalog','information_schema')
                order by schema_name
                """);
            java.util.List<String> schemas = new java.util.ArrayList<>();
            while (rs.next()) {
                schemas.add(rs.getString("schema_name"));
            }
            if (schemas.isEmpty()) {
                showError("No schemas found", "No visible schemas were returned by this database user.");
                return;
            }
            ChoiceDialog<String> dialog = new ChoiceDialog<>(schemas.get(0), schemas);
            dialog.setTitle("Select Schema");
            dialog.setHeaderText("Select schema from connected database");
            dialog.setContentText("Schema:");
            Optional<String> picked = dialog.showAndWait();
            if (picked.isEmpty()) {
                return;
            }
            String schemaName = picked.get();

            // Replace current diagram content with ONLY the chosen schema
            schema.setName("supabase-import");
            schema.getTables().clear();
            schema.getRelationships().clear();
            schema.getComments().clear();

            DatabaseSchema imported = supabaseImporter.importSchema(
                new SupabaseImporter.ConnectionInfo(activeProfile.jdbcUrl(), activeProfile.username(), activeProfile.password()),
                schemaName
            );
            schema.setTables(imported.getTables());
            schema.setRelationships(imported.getRelationships());
            schema.setComments(imported.getComments());
            schema.setName(imported.getName());

            selectedTable = null;
            bindTableToProperties(null);

            schemaSelect.setValue(schemaName);
            canvas.setSchemaFilter(schemaName);

            autoLayoutTables();
            baselineSchemaJson = snapshotSchemaJson();
            undoStack.clear();
            redoStack.clear();

            onSchemaChanged();
        } catch (Exception ex) {
            showError("Schema selection failed", ex.getMessage());
        }
    }

    private String normalizeJdbcUrl(String jdbcUrl) {
        String value = jdbcUrl == null ? "" : jdbcUrl.trim();
        if (!value.startsWith("jdbc:postgresql://")) {
            value = "jdbc:postgresql://" + value.replaceFirst("^https?://", "");
        }
        if (value.contains("/postgressslmode=") && !value.contains("?sslmode=")) {
            value = value.replace("/postgressslmode=", "/postgres?sslmode=");
        }
        if (value.contains("/postgres/sslmode=") && !value.contains("?sslmode=")) {
            value = value.replace("/postgres/sslmode=", "/postgres?sslmode=");
        }
        if (!value.contains("sslmode=")) {
            value = value + (value.contains("?") ? "&sslmode=require" : "?sslmode=require");
        }
        return value;
    }

    private void showConnectionError(Exception ex) {
        String msg = ex.getMessage() == null ? "Unknown connection error" : ex.getMessage();
        showError("Connection failed",
            msg + "\n\nTips:\n- Use username like postgres.<project-ref>\n- Ensure JDBC URL includes sslmode=require\n- Confirm DB password from Supabase settings");
    }

    private void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message == null ? "Unknown error" : message);
        a.setTitle(title);
        a.setHeaderText(title);
        a.showAndWait();
    }

    private String safeName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().replace(' ', '_');
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private java.util.List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
    }

    private Column pkUuid(String name, String defaultExpr) {
        Column c = new Column(name, PostgresDataType.UUID);
        c.setPrimaryKey(true);
        c.setNullable(false);
        c.setDefaultValue(defaultExpr);
        return c;
    }

    private Column pkBigSerial(String name) {
        Column c = new Column(name, PostgresDataType.BIGSERIAL);
        c.setPrimaryKey(true);
        c.setNullable(false);
        return c;
    }

    private Column ts(String name) {
        Column c = new Column(name, PostgresDataType.TIMESTAMPTZ);
        c.setDefaultValue("now()");
        c.setNullable(false);
        return c;
    }

    private DatabaseSchema seedSchema() {
        DatabaseSchema s = new DatabaseSchema();
        s.setName("sample");
        Table users = new Table();
        users.setName("users");
        users.setX(80);
        users.setY(80);
        users.getColumns().add(pkUuid("id", "gen_random_uuid()"));
        users.getColumns().add(new Column("email", PostgresDataType.VARCHAR));
        users.getColumns().get(1).setUnique(true);
        users.getColumns().add(ts("created_at"));
        Table posts = new Table();
        posts.setName("posts");
        posts.setX(420);
        posts.setY(180);
        posts.getColumns().add(pkBigSerial("id"));
        Column userId = new Column("user_id", PostgresDataType.UUID);
        userId.setReferencesSchema("public");
        userId.setReferencesTable("users");
        userId.setReferencesColumn("id");
        posts.getColumns().add(userId);
        posts.getColumns().add(new Column("title", PostgresDataType.TEXT));
        s.getTables().add(users);
        s.getTables().add(posts);
        return s;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
