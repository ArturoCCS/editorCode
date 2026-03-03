package com.controlador;

import com.modelo.SyntaxTreeNodeRow;
import com.modelo.TokenRow;
import com.vista.CodeEditorTab;
import com.modelo.SymbolRow;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.media.AudioClip;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainController {

    @FXML private Button btnNew;
    @FXML private Button btnOpen;
    @FXML private ToggleButton toggleTheme;
    @FXML private TabPane tabPane;
    @FXML private VBox tokenBox;

    private AudioClip keySound;

    private final TokenManager tokenManager = new TokenManager();

    private TableView<TokenRow> tokenTable;
    private TableView<SymbolRow> symbolTable;
    private TableView<SyntaxTreeNodeRow> syntaxTreeTable;

    private final Map<String, TitledPane> panels = new LinkedHashMap<>();
    private final Accordion accordion = new Accordion();

    @FXML
    public void initialize() {
        try {
            URL soundUrl = getClass().getResource("/com/editorcode/sounds/key.wav");
            if (soundUrl != null) {
                keySound = new AudioClip(soundUrl.toExternalForm());
                keySound.setVolume(0.12);
            }
        } catch (Exception ex) {
            keySound = null;
        }

        btnNew.setOnAction(e -> createNewTab(null, "txt"));
        btnOpen.setOnAction(e -> openFileAction());

        setupSymbolTable();
        setupTokenTable();
        setupSyntaxTreeTable();

        setupPanelsToggleUI();
        mountPanelsArea();

        Platform.runLater(() -> createNewTab("Main.js", "js"));
    }

    private void mountPanelsArea() {
        if (tokenBox == null) return;
        tokenBox.getChildren().clear();

        TitledPane toggles = buildPanelsTogglePane();
        toggles.getStyleClass().add("token-panel");

        accordion.getPanes().setAll(panels.values());
        accordion.setExpandedPane(panels.get("symbols"));

        tokenBox.getChildren().addAll(toggles, accordion);
    }

    private TitledPane buildPanelsTogglePane() {
        VBox checks = new VBox(6);
        checks.getStyleClass().add("panel-toggle-box");

        for (Map.Entry<String, TitledPane> e : panels.entrySet()) {
            String id = e.getKey();
            TitledPane pane = e.getValue();

            CheckBox cb = new CheckBox(pane.getText());
            cb.setSelected(true);
            cb.selectedProperty().addListener((obs, oldV, newV) -> {
                if (newV) {
                    if (!accordion.getPanes().contains(pane)) accordion.getPanes().add(pane);
                } else {
                    accordion.getPanes().remove(pane);
                }
            });

            cb.setUserData(id);
            checks.getChildren().add(cb);
        }

        TitledPane toggles = new TitledPane("Paneles", checks);
        toggles.setExpanded(true);
        return toggles;
    }

    private void setupPanelsToggleUI() {
        if (tokenBox == null) return;
    }

    private void registerPanel(String id, String title, Node content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setCollapsible(true);
        pane.setExpanded(false);
        pane.getStyleClass().add("token-panel");
        panels.put(id, pane);
    }

    private void setupSyntaxTreeTable() {
        syntaxTreeTable = new TableView<>();
        syntaxTreeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        syntaxTreeTable.setPlaceholder(new Label("Sin árbol"));

        TableColumn<SyntaxTreeNodeRow, Number> colNodeId = new TableColumn<>("Nodo");
        colNodeId.setCellValueFactory(data -> data.getValue().nodeIdProperty());

        TableColumn<SyntaxTreeNodeRow, String> colToken = new TableColumn<>("Token");
        colToken.setCellValueFactory(data -> data.getValue().tokenProperty());

        TableColumn<SyntaxTreeNodeRow, Integer> colParent = new TableColumn<>("Padre");
        colParent.setCellValueFactory(data -> data.getValue().parentIdProperty());
        colParent.setCellFactory(col -> new TableCell<SyntaxTreeNodeRow, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (item == null ? "NULL" : item.toString()));
            }
        });

        syntaxTreeTable.getColumns().addAll(colNodeId, colToken, colParent);
        syntaxTreeTable.setItems(tokenManager.getSyntaxTreeRows());

        registerPanel("syntaxTree", "Árbol Sintáctico", syntaxTreeTable);
    }

    private void setupTokenTable() {
        tokenTable = new TableView<>();
        tokenTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tokenTable.setPlaceholder(new Label("Sin tokens"));

        TableColumn<TokenRow, String> colLexema = new TableColumn<>("Lexema");
        colLexema.setCellValueFactory(data -> data.getValue().lexemaProperty());

        TableColumn<TokenRow, String> colToken = new TableColumn<>("Token");
        colToken.setCellValueFactory(data -> data.getValue().tokenProperty());

        tokenTable.getColumns().addAll(colLexema, colToken);
        tokenTable.setItems(tokenManager.getTokensObservable());

        registerPanel("tokens", "Tabla de Tokens", tokenTable);
    }

    private void setupSymbolTable() {
        symbolTable = new TableView<>();
        symbolTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        symbolTable.setPlaceholder(new Label("Sin símbolos"));

        TableColumn<SymbolRow, String> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(data -> data.getValue().idProperty());

        TableColumn<SymbolRow, String> colTipo = new TableColumn<>("Tipo");
        colTipo.setCellValueFactory(data -> data.getValue().tipoProperty());

        TableColumn<SymbolRow, String> colValor = new TableColumn<>("Valor");
        colValor.setCellValueFactory(data -> data.getValue().valorProperty());

        symbolTable.getColumns().addAll(colId, colTipo, colValor);
        symbolTable.setItems(tokenManager.getSymbolsObservable());

        registerPanel("symbols", "Tabla de Símbolos", symbolTable);
    }

    private void createNewTab(String initialPath, String ext) {
        String title = (initialPath != null) ? Path.of(initialPath).getFileName().toString() : ("untitled." + ext);
        CodeEditorTab editorTab = new CodeEditorTab(title, keySound, tokenManager);
        Tab t = new Tab(title);
        t.setContent(editorTab.getNode());
        t.setUserData(editorTab);
        tabPane.getTabs().add(t);
        tabPane.getSelectionModel().select(t);
        Platform.runLater(() -> editorTab.getCodeArea().requestFocus());

        tokenManager.updateFromText(editorTab.getCodeArea().getText());
    }

    private void openFileAction() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open file");
        Stage stage = (Stage) tabPane.getScene().getWindow();
        var selected = chooser.showOpenDialog(stage);
        if (selected == null) return;
        Path file = selected.toPath();
        try {
            String content = Files.readString(file);
            CodeEditorTab editorTab = new CodeEditorTab(file.getFileName().toString(), keySound, tokenManager);
            editorTab.getCodeArea().replaceText(content);
            Tab t = new Tab(file.getFileName().toString());
            t.setContent(editorTab.getNode());
            t.setUserData(editorTab);
            tabPane.getTabs().add(t);
            tabPane.getSelectionModel().select(t);
            Platform.runLater(() -> editorTab.getCodeArea().requestFocus());

            tokenManager.updateFromText(editorTab.getCodeArea().getText());
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Cannot open file: " + ex.getMessage()).showAndWait();
        }
    }
}