package com.controlador;

import com.modelo.SyntaxTreeNodeRow;
import com.modelo.TokenRow;
import com.vista.CodeEditorTab;
import com.modelo.SymbolRow;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.media.AudioClip;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

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

        setupTokenTable();
        setupSymbolTable();
        setupSyntaxTreeTable();

        Platform.runLater(() -> createNewTab("Main.js", "js"));
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
        colParent.setCellFactory(col -> new javafx.scene.control.TableCell<SyntaxTreeNodeRow, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (item == null ? "NULL" : item.toString()));
            }
        });

        syntaxTreeTable.getColumns().addAll(colNodeId, colToken, colParent);

        Label title = new Label("Árbol Sintáctico");
        title.getStyleClass().add("token-title");

        if (tokenBox != null) {
            tokenBox.getChildren().add(new Separator());
            tokenBox.getChildren().add(title);
            tokenBox.getChildren().add(syntaxTreeTable);
        }

        syntaxTreeTable.setItems(tokenManager.getSyntaxTreeRows());
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

        Label title = new Label("Tabla de Tokens");
        title.getStyleClass().add("token-title");

        if (tokenBox != null) {
            tokenBox.getChildren().add(title);
            tokenBox.getChildren().add(tokenTable);
        }

        tokenTable.setItems(tokenManager.getTokensObservable());
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

        Label title = new Label("Tabla de Símbolos");
        title.getStyleClass().add("token-title");

        if (tokenBox != null) {
            tokenBox.getChildren().add(new Separator());
            tokenBox.getChildren().add(title);
            tokenBox.getChildren().add(symbolTable);
        }

        symbolTable.setItems(tokenManager.getSymbolsObservable());
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