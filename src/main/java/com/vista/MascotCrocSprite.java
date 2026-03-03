package com.vista;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class MascotCrocSprite extends StackPane {

    public enum MessageType { INFO, ERROR }

    private static final int SPRITE_SIZE = 84;
    private static final int BUBBLE_MAX_WIDTH = 340;

    private final ImageView spriteView;
    private final StackPane bubbleContainer;
    private final Label messageLabel;
    private final Path bubbleTail;

    private boolean autoHideEnabled = true;
    private PauseTransition autoHideTimer;

    private static final Image SPRITE_NORMAL = new Image(
            MascotCrocSprite.class.getResourceAsStream("/com/editorcode/sprites/idle.png")
    );
    private static final Image SPRITE_TALK = new Image(
            MascotCrocSprite.class.getResourceAsStream("/com/editorcode/sprites/blink.png")
    );
    private static final Image SPRITE_BLINK = new Image(
            MascotCrocSprite.class.getResourceAsStream("/com/editorcode/sprites/fail.png")
    );

    public MascotCrocSprite() {
        setAlignment(Pos.BOTTOM_RIGHT);
        setPickOnBounds(false);

        spriteView = new ImageView(SPRITE_NORMAL);
        spriteView.setFitWidth(SPRITE_SIZE);
        spriteView.setFitHeight(SPRITE_SIZE);
        spriteView.setPreserveRatio(true);

        bubbleContainer = new StackPane();
        bubbleContainer.setMaxWidth(BUBBLE_MAX_WIDTH);
        bubbleContainer.setVisible(false);
        bubbleContainer.setOpacity(0);
        bubbleContainer.setAlignment(Pos.CENTER);
        bubbleContainer.getStyleClass().add("mascot-bubble");

        Rectangle bubbleBackground = new Rectangle();
        bubbleBackground.setFill(Color.web("#2C3E50", 0.95));
        bubbleBackground.setArcWidth(20);
        bubbleBackground.setArcHeight(20);
        bubbleBackground.setStrokeWidth(0);

        messageLabel = new Label();
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(BUBBLE_MAX_WIDTH - 30);
        messageLabel.getStyleClass().add("mascot-bubble-text");

        bubbleTail = new Path();
        bubbleTail.setVisible(false);
        bubbleTail.setManaged(false);

        bubbleBackground.widthProperty().bind(messageLabel.widthProperty().add(30));
        bubbleBackground.heightProperty().bind(messageLabel.heightProperty().add(30));

        bubbleContainer.getChildren().addAll(bubbleBackground, messageLabel);

        StackPane.setAlignment(bubbleContainer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(bubbleContainer, new Insets(0, 96, 62, 0));

        StackPane.setAlignment(spriteView, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(spriteView, new Insets(0, 12, 10, 0));

        getChildren().addAll(bubbleContainer, bubbleTail, spriteView);

        setupAutoHideTimer();
    }

    public void showMessage(String message, MessageType type) {
        messageLabel.setText(message);

        bubbleContainer.setVisible(true);

        FadeTransition ft = new FadeTransition(Duration.millis(180), bubbleContainer);
        ft.setFromValue(bubbleContainer.getOpacity());
        ft.setToValue(1.0);
        ft.play();

        setSprite(type == MessageType.ERROR ? SPRITE_BLINK : SPRITE_TALK);

        if (autoHideEnabled) {
            autoHideTimer.stop();
            autoHideTimer.playFromStart();
        }
    }

    public void keepMessageVisible() {
        if (autoHideEnabled) {
            autoHideTimer.stop();
            autoHideTimer.playFromStart();
        }
    }

    public void enableAutoHideAndRestart() {
        autoHideEnabled = true;
        autoHideTimer.stop();
        autoHideTimer.playFromStart();
    }

    private void setupAutoHideTimer() {
        autoHideTimer = new PauseTransition(Duration.seconds(2.6));
        autoHideTimer.setOnFinished(e -> hideMessage());
    }

    private void hideMessage() {
        FadeTransition ft = new FadeTransition(Duration.millis(180), bubbleContainer);
        ft.setFromValue(bubbleContainer.getOpacity());
        ft.setToValue(0.0);
        ft.setOnFinished(e -> bubbleContainer.setVisible(false));
        ft.play();
        setSprite(SPRITE_NORMAL);
    }

    private void setSprite(Image img) {
        spriteView.setImage(img);
    }
}