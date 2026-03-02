package com.modelo;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class SymbolRow {
    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty tipo = new SimpleStringProperty();
    private final StringProperty valor = new SimpleStringProperty();

    public SymbolRow(String id, String tipo, String valor) {
        this.id.set(id);
        this.tipo.set(tipo);
        this.valor.set(valor);
    }

    public StringProperty idProperty() { return id; }
    public StringProperty tipoProperty() { return tipo; }
    public StringProperty valorProperty() { return valor; }

    public String getId() { return id.get(); }
    public void setId(String v) { id.set(v); }

    public String getTipo() { return tipo.get(); }
    public void setTipo(String v) { tipo.set(v); }

    public String getValor() { return valor.get(); }
    public void setValor(String v) { valor.set(v); }
}