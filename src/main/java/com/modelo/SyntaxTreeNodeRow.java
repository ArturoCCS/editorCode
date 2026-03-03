package com.modelo;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

public class SyntaxTreeNodeRow {
    private final SimpleIntegerProperty nodeId;
    private final SimpleStringProperty token;
    private final ObjectProperty<Integer> parentId;

    /** Creates a row where {@code parentId} may be {@code null} (used to denote the root, PROGRAMA). */
    public SyntaxTreeNodeRow(int nodeId, String token, Integer parentId) {
        this.nodeId = new SimpleIntegerProperty(nodeId);
        this.token = new SimpleStringProperty(token);
        this.parentId = new SimpleObjectProperty<>(parentId);
    }

    public int getNodeId() {
        return nodeId.get();
    }

    public SimpleIntegerProperty nodeIdProperty() {
        return nodeId;
    }

    public String getToken() {
        return token.get();
    }

    public SimpleStringProperty tokenProperty() {
        return token;
    }

    /** Returns the parent node ID, or {@code null} if this is the root node (PROGRAMA). */
    public Integer getParentId() {
        return parentId.get();
    }

    public ObjectProperty<Integer> parentIdProperty() {
        return parentId;
    }
}