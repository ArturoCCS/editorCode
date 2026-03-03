package com.modelo;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

public class SyntaxTreeNodeRow {
    private final SimpleIntegerProperty nodeId;
    private final SimpleStringProperty token;
    private final ObjectProperty<Integer> parentId;

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

    public Integer getParentId() {
        return parentId.get();
    }

    public ObjectProperty<Integer> parentIdProperty() {
        return parentId;
    }
}