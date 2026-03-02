package com.modelo;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class SyntaxTreeNodeRow {
    private final SimpleIntegerProperty nodeId;
    private final SimpleStringProperty token;
    private final SimpleIntegerProperty parentId;

    public SyntaxTreeNodeRow(int nodeId, String token, int parentId) {
        this.nodeId = new SimpleIntegerProperty(nodeId);
        this.token = new SimpleStringProperty(token);
        this.parentId = new SimpleIntegerProperty(parentId);
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

    public int getParentId() {
        return parentId.get();
    }

    public SimpleIntegerProperty parentIdProperty() {
        return parentId;
    }
}