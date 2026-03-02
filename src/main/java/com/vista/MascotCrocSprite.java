package com.vista;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class MascotCrocSprite extends StackPane {

    private static final int SPRITE_SIZE = 100;
    private static final int BUBBLE_MAX_WIDTH = 250;

    private final ImageView spriteView;
    private final StackPane bubbleContainer;
    private final Label messageLabel;
    private final Path bubbleTail;

    private Timeline blinkAnimation;
    private Timeline talkAnimation;

    private boolean isTalking = false;
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
        setMaxSize(SPRITE_SIZE + BUBBLE_MAX_WIDTH, SPRITE_SIZE + 50);

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
        bubbleBackground.setStrokeWidth(2);

        messageLabel = new Label();
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(BUBBLE_MAX_WIDTH - 30);
        messageLabel.setStyle(
                "-fx-text-fill: white;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 12px;" +
                        "-fx-font-family: 'Monospaced';"
        );
        messageLabel.getStyleClass().add("mascot-bubble-text");

        bubbleTail = new Path();
        bubbleTail.setFill(Color.web("#2C3E50", 0.95));
        bubbleTail.setStroke(Color.web("#3498DB"));
        bubbleTail.setStrokeWidth(2);

        bubbleBackground.widthProperty().bind(messageLabel.widthProperty().add(30));
        bubbleBackground.heightProperty().bind(messageLabel.heightProperty().add(30));

        bubbleContainer.getChildren().addAll(bubbleBackground, messageLabel);

        StackPane.setAlignment(bubbleContainer, Pos.CENTER_LEFT);
        StackPane.setMargin(bubbleContainer, new Insets(0, 0, 20, -30));

        StackPane.setAlignment(spriteView, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(spriteView, new Insets(0, 10, 10, 0));

        getChildren().addAll(bubbleContainer, spriteView);

        setupAnimations();
        setupBlinkAnimation();
        setupAutoHideTimer();
    }

    private void setupAnimations() {
        bubbleContainer.layoutBoundsProperty().addListener((obs, old, bounds) -> {
            updateBubbleTail(bounds);
        });
    }

    private void updateBubbleTail(javafx.geometry.Bounds bounds) {
        bubbleTail.getElements().clear();

        double tailX = bounds.getMaxX() - 10;
        double tailY = bounds.getMaxY() - 30;
        double endX = SPRITE_SIZE - 30;
        double endY = SPRITE_SIZE - 30;

        MoveTo moveTo = new MoveTo(tailX, tailY);
        CubicCurveTo curveTo = new CubicCurveTo(
                tailX + 30, tailY - 10,
                endX - 20, endY - 20,
                endX, endY
        );

        bubbleTail.getElements().addAll(moveTo, curveTo);
    }

    private void setupBlinkAnimation() {
        blinkAnimation = new Timeline(
                new KeyFrame(Duration.seconds(3), e -> {
                    if (!isTalking) {
                        spriteView.setImage(SPRITE_BLINK);
                    }
                }),
                new KeyFrame(Duration.seconds(3.2), e -> {
                    if (!isTalking) {
                        spriteView.setImage(SPRITE_NORMAL);
                    }
                })
        );
        blinkAnimation.setCycleCount(Timeline.INDEFINITE);
        blinkAnimation.play();
    }

    private void setupAutoHideTimer() {
        autoHideTimer = new PauseTransition(Duration.seconds(0.25));
        autoHideTimer.setOnFinished(e -> {
            if (autoHideEnabled) {
                hideMessage();
            }
        });
    }

    public void keepMessageVisible() {
        autoHideEnabled = false;
        autoHideTimer.stop();
    }

    public void enableAutoHideAndRestart() {
        autoHideEnabled = true;
        autoHideTimer.playFromStart();
    }

    public void showMessage(String message, MessageType type) {
        if (talkAnimation != null) {
            talkAnimation.stop();
        }

        autoHideEnabled = true;
        autoHideTimer.stop();

        messageLabel.setText(message);

        bubbleContainer.setVisible(true);

        isTalking = true;
        spriteView.setImage(SPRITE_TALK);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), bubbleContainer);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), bubbleContainer);
        scaleIn.setFromX(0.9);
        scaleIn.setFromY(0.9);
        scaleIn.setToX(1);
        scaleIn.setToY(1);

        ParallelTransition enterAnimation = new ParallelTransition(fadeIn, scaleIn);

        talkAnimation = new Timeline(
                new KeyFrame(Duration.seconds(0.0), e -> spriteView.setImage(SPRITE_TALK)),
                new KeyFrame(Duration.seconds(0.4), e -> spriteView.setImage(SPRITE_NORMAL))
        );
        talkAnimation.setCycleCount(Animation.INDEFINITE);

        enterAnimation.setOnFinished(e -> {
            talkAnimation.play();
            if (autoHideEnabled) {
                autoHideTimer.playFromStart();
            }
        });

        enterAnimation.play();
    }

    public void showError(String errorMessage) {
        showMessage(errorMessage, MessageType.ERROR);
    }

    public void showSuggestion(String suggestion) {
        showMessage(suggestion, MessageType.INFO);
    }

    public void hideMessage() {
        autoHideTimer.stop();

        if (!bubbleContainer.isVisible()) {
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), bubbleContainer);
        fadeOut.setFromValue(bubbleContainer.getOpacity());
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            bubbleContainer.setVisible(false);
            if (talkAnimation != null) {
                talkAnimation.stop();
            }
            spriteView.setImage(SPRITE_NORMAL);
            isTalking = false;
        });
        fadeOut.play();
    }

    public enum MessageType {
        ERROR, WARNING, INFO, SUCCESS
    }
}