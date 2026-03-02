package com.editorcode;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        String fxmlPath = "/com/editorcode/main.fxml";
        String cssPath = "/styles.css";

        URL fxmlUrl = getClass().getResource(fxmlPath);
        if (fxmlUrl == null) {
            throw new IllegalStateException("FXML resource missing: " + fxmlPath);
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        Scene scene = new Scene(root, 1280, 600);

        URL cssUrl = getClass().getResource(cssPath);
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

        stage.setTitle("Modern JavaFX Code Editor — FXML");
        stage.setScene(scene);

        stage.show();
    }

    public static void main(String[] args) { launch(args); }
}