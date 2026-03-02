module com.editorcode {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;
    requires org.fxmisc.undo;
    requires javafx.graphics;
    opens com.editorcode to javafx.fxml;
    exports com.editorcode;
    exports com.vista;
    opens com.vista to javafx.fxml;
    exports com.modelo;
    opens com.modelo to javafx.fxml;
    exports com.controlador;
    opens com.controlador to javafx.fxml;
}