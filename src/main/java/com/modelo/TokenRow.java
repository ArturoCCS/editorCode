package com.modelo;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class TokenRow {
    private final StringProperty lexema = new SimpleStringProperty();
    private final StringProperty token = new SimpleStringProperty();

    public TokenRow(String lexema, String token) {
        this.lexema.set(lexema);
        this.token.set(token);
    }

    public StringProperty lexemaProperty() { return lexema; }
    public StringProperty tokenProperty() { return token; }

}