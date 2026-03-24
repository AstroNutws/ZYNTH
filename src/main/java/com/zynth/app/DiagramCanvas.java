package com.zynth.app;

import com.zynth.model.DatabaseSchema;
import com.zynth.model.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class DiagramCanvas extends Canvas {
    private static final double GRID = 25.0;
    private final DatabaseSchema schema;

    private double zoom = 1.0;
    private double panX = 0;
    private double panY = 0;
    private double lastMouseX;
    private double lastMouseY;
    private Table selectedTable;
    private Table dragTable;
    private double dragOffsetX;
    private double dragOffsetY;
    private Consumer<Table> onSelectionChanged;
    private Runnable onSchemaChanged;
    private String schemaFilter = "ALL";
    private boolean spacePanEnabled;
    private boolean panOnEmptyDrag;

    public DiagramCanvas(DatabaseSchema schema) {
        this.schema = schema;
        setFocusTraversable(true);
        widthProperty().addListener((obs, oldVal, newVal) -> redraw());
        heightProperty().addListener((obs, oldVal, newVal) -> redraw());
        setOnMouseEntered(e -> requestFocus());

        setOnScroll(e -> {
            double delta = e.getDeltaY() > 0 ? 0.1 : -0.1;
            zoom = clamp(zoom + delta, 0.3, 3.0);
            redraw();
        });

        setOnMousePressed(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            Table hit = findTableAtScreen(e.getX(), e.getY());
            if (e.isPrimaryButtonDown() && hit != null) {
                selectedTable = hit;
                dragTable = hit;
                double worldX = screenToWorldX(e.getX());
                double worldY = screenToWorldY(e.getY());
                dragOffsetX = worldX - hit.getX();
                dragOffsetY = worldY - hit.getY();
                if (onSelectionChanged != null) {
                    onSelectionChanged.accept(selectedTable);
                }
                redraw();
                return;
            }

            selectedTable = hit;
            panOnEmptyDrag = hit == null && (e.isPrimaryButtonDown() || e.isSecondaryButtonDown());
            if (onSelectionChanged != null) {
                onSelectionChanged.accept(selectedTable);
            }
            redraw();
        });

        setOnMouseDragged(e -> {
            if (dragTable != null && e.isPrimaryButtonDown() && !spacePanEnabled) {
                double worldX = screenToWorldX(e.getX());
                double worldY = screenToWorldY(e.getY());
                dragTable.setX(worldX - dragOffsetX);
                dragTable.setY(worldY - dragOffsetY);
                redraw();
                return;
            }
            if (e.isMiddleButtonDown() || panOnEmptyDrag || (spacePanEnabled && e.isPrimaryButtonDown()) || (spacePanEnabled && e.isSecondaryButtonDown())) {
                panX += e.getX() - lastMouseX;
                panY += e.getY() - lastMouseY;
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                redraw();
            }
        });

        setOnMouseReleased(e -> {
            if (dragTable != null) {
                dragTable.setX(snap(dragTable.getX()));
                dragTable.setY(snap(dragTable.getY()));
                dragTable = null;
                if (onSchemaChanged != null) {
                    onSchemaChanged.run();
                }
                redraw();
            }
            panOnEmptyDrag = false;
        });
    }

    public void redraw() {
        GraphicsContext g = getGraphicsContext2D();
        g.setFill(Color.web("#121722"));
        g.fillRect(0, 0, getWidth(), getHeight());

        drawGrid(g);
        g.save();
        g.translate(panX, panY);
        g.scale(zoom, zoom);
        drawRelationships(g);
        drawTables(g);
        g.restore();
    }

    private void drawGrid(GraphicsContext g) {
        g.setStroke(Color.web("#1c2636"));
        g.setLineWidth(1);
        for (double x = 0; x < getWidth(); x += GRID) {
            g.strokeLine(x, 0, x, getHeight());
        }
        for (double y = 0; y < getHeight(); y += GRID) {
            g.strokeLine(0, y, getWidth(), y);
        }
    }

    private void drawTables(GraphicsContext g) {
        g.setFont(Font.font("Consolas", 13));
        for (Table t : visibleTables()) {
            double x = t.getX();
            double y = t.getY();
            double w = t.getWidth();
            double h = Math.max(t.getHeight(), 44 + t.getColumns().size() * 20);

            g.setFill(Color.web("#1f2a3d"));
            g.fillRoundRect(x, y, w, h, 12, 12);
            g.setStroke(t == selectedTable ? Color.web("#58a6ff") : Color.web("#314662"));
            g.setLineWidth(t == selectedTable ? 2 : 1);
            g.strokeRoundRect(x, y, w, h, 12, 12);

            g.setFill(Color.web("#2a3b55"));
            g.fillRoundRect(x, y, w, 28, 12, 12);
            g.setFill(Color.WHITE);
            g.fillText(t.getSchema() + "." + t.getName(), x + 8, y + 18);

            g.setFill(Color.web("#d5dfef"));
            double cy = y + 44;
            for (var c : t.getColumns()) {
                String marker = c.isPrimaryKey() ? "PK " : c.isUnique() ? "UQ " : "";
                g.fillText(marker + c.getName() + " : " + c.getDataType().name(), x + 8, cy);
                cy += 20;
            }
        }
    }

    private void drawRelationships(GraphicsContext g) {
        g.setStroke(Color.web("#4e637f"));
        g.setLineWidth(1.2);
        for (var rel : schema.getRelationships()) {
            Table src = findById(rel.getSourceTableId());
            Table dst = findById(rel.getTargetTableId());
            if (src == null || dst == null) {
                continue;
            }
            if (!isVisible(src) || !isVisible(dst)) {
                continue;
            }
            double x1 = src.getX() + src.getWidth();
            double y1 = src.getY() + 30;
            double x2 = dst.getX();
            double y2 = dst.getY() + 30;
            double c1x = x1 + 60;
            double c2x = x2 - 60;
            g.beginPath();
            g.moveTo(x1, y1);
            g.bezierCurveTo(c1x, y1, c2x, y2, x2, y2);
            g.stroke();
        }
    }

    private Table findById(java.util.UUID id) {
        if (id == null) {
            return null;
        }
        for (Table t : schema.getTables()) {
            if (id.equals(t.getId())) {
                return t;
            }
        }
        return null;
    }

    private Table findTableAtScreen(double sx, double sy) {
        double wx = screenToWorldX(sx);
        double wy = screenToWorldY(sy);
        List<Table> visible = visibleTables();
        for (int i = visible.size() - 1; i >= 0; i--) {
            Table t = visible.get(i);
            double h = Math.max(t.getHeight(), 44 + t.getColumns().size() * 20);
            if (wx >= t.getX() && wx <= t.getX() + t.getWidth() && wy >= t.getY() && wy <= t.getY() + h) {
                return t;
            }
        }
        return null;
    }

    private double screenToWorldX(double sx) {
        return (sx - panX) / zoom;
    }

    private double screenToWorldY(double sy) {
        return (sy - panY) / zoom;
    }

    private double snap(double value) {
        return Math.round(value / GRID) * GRID;
    }

    public void setOnSelectionChanged(Consumer<Table> onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    public void setOnSchemaChanged(Runnable onSchemaChanged) {
        this.onSchemaChanged = onSchemaChanged;
    }

    public Table getSelectedTable() {
        return selectedTable;
    }

    public void setSchemaFilter(String schemaFilter) {
        this.schemaFilter = schemaFilter == null ? "ALL" : schemaFilter;
        if (selectedTable != null && !isVisible(selectedTable)) {
            selectedTable = null;
            if (onSelectionChanged != null) {
                onSelectionChanged.accept(null);
            }
        }
        redraw();
    }

    public void setSpacePanEnabled(boolean enabled) {
        this.spacePanEnabled = enabled;
        if (enabled) {
            dragTable = null;
        }
    }

    public void focusOnTable(Table table) {
        if (table == null) {
            return;
        }
        selectedTable = table;
        if (zoom < 0.85) {
            zoom = 1.0;
        }
        double targetX = table.getX() + table.getWidth() / 2.0;
        double targetY = table.getY() + Math.max(table.getHeight(), 44 + table.getColumns().size() * 20) / 2.0;
        panX = getWidth() / 2.0 - targetX * zoom;
        panY = getHeight() / 2.0 - targetY * zoom;
        redraw();
    }

    public void resetView() {
        zoom = 1.0;
        panX = 0;
        panY = 0;
        redraw();
    }

    private boolean isVisible(Table t) {
        return "ALL".equalsIgnoreCase(schemaFilter) || t.getSchema().equalsIgnoreCase(schemaFilter);
    }

    private List<Table> visibleTables() {
        if ("ALL".equalsIgnoreCase(schemaFilter)) {
            return schema.getTables();
        }
        List<Table> out = new ArrayList<>();
        for (Table t : schema.getTables()) {
            if (isVisible(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
