package com.example.dungeon.ui;

import com.example.dungeon.game.Card;
import com.example.dungeon.game.CardType;
import javafx.animation.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.function.Consumer;

public class CardViewFactory {

    public Pane createCardPane(Card card, int index, boolean isEnabled, Consumer<Card> onPlay) {
        Pane pane = new Pane();
        pane.setPrefSize(100, 150);
        pane.getStyleClass().add("card-pane");
        pane.setId("card-" + index);

        Canvas canvas = new Canvas(100, 150);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        Color cardColor;
        String cardDescription = "";

        switch (card.getType()) {
            case ATTACK -> { cardColor = Color.rgb(231, 76, 60); cardDescription = "Наносит 2 урона"; }
            case DEFENSE -> { cardColor = Color.rgb(52, 152, 219); cardDescription = "Даёт +1 щит"; }
            case HEAL -> { cardColor = Color.rgb(46, 204, 113); cardDescription = "Восстанавливает 1 HP"; }
            default -> cardColor = Color.GRAY;
        }

        gc.setFill(cardColor);
        gc.fillRoundRect(2,2,96,146,15,15);
        gc.setFill(Color.rgb(0,0,0,0.25));
        gc.fillRoundRect(2,2,96,40,15,15);

        gc.setStroke(isEnabled ? Color.WHITE : Color.GRAY);
        gc.setLineWidth(2);
        gc.strokeRoundRect(2,2,96,146,15,15);

        gc.setFill(Color.WHITE);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(javafx.scene.text.Font.font("Arial", 24));

        String symbol = switch (card.getType()) {
            case ATTACK -> "⚔";
            case DEFENSE -> "🛡";
            case HEAL -> "❤";
            default -> "";
        };
        gc.fillText(symbol, 50, 40);

        gc.setFont(javafx.scene.text.Font.font("Arial", 11));
        gc.fillText(card.getName(), 50, 80);

        gc.setFont(javafx.scene.text.Font.font("Arial", 9));
        String typeText = switch (card.getType()) {
            case ATTACK -> "АТАКА";
            case DEFENSE -> "ЗАЩИТА";
            case HEAL -> "ЛЕЧЕНИЕ";
            default -> "";
        };
        gc.fillText(typeText, 50, 100);

        gc.setFont(javafx.scene.text.Font.font("Arial", 8));
        gc.fillText(cardDescription, 50, 115);

        pane.getChildren().add(canvas);

        pane.setDisable(!isEnabled);
        pane.setOpacity(isEnabled ? 1.0 : 0.45);

        pane.setOnMouseClicked(e -> {
            if (!pane.isDisabled() && onPlay != null) onPlay.accept(card);
        });

        pane.setOnMouseEntered(e -> {
            if (!pane.isDisabled()) pane.setStyle("-fx-effect: dropshadow(gaussian, rgba(243,156,18,0.7), 20, 0, 0, 5); -fx-translate-y: -5;");
        });
        pane.setOnMouseExited(e -> pane.setStyle("-fx-translate-y: 0;"));

        // entrance animation
        pane.setOpacity(0);
        TranslateTransition tt = new TranslateTransition(Duration.millis(240), pane);
        tt.setFromY(20); tt.setToY(0);
        FadeTransition ft = new FadeTransition(Duration.millis(240), pane);
        ft.setFromValue(0); ft.setToValue(isEnabled ? 1.0 : 0.45);
        ParallelTransition pt = new ParallelTransition(tt, ft);
        pt.setDelay(Duration.millis(index * 60));
        pt.play();

        return pane;
    }

    /**
     * Вид карты противника — серый фон, вопросительный знак.
     */
    public Pane createHiddenCard(int index) {
        Pane pane = new Pane();
        pane.setPrefSize(100,150);
        pane.getStyleClass().add("card-pane-hidden");

        Canvas canvas = new Canvas(100,150);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.setFill(Color.rgb(40,40,50));
        gc.fillRoundRect(2,2,96,146,15,15);

        gc.setFill(Color.rgb(70,70,80));
        gc.fillRoundRect(2,2,96,40,15,15);

        gc.setFill(Color.rgb(90,90,100));
        gc.setFont(javafx.scene.text.Font.font("Arial",48));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("?", 50, 95);

        pane.getChildren().add(canvas);
        pane.setOpacity(0.8);

        // simple entrance
        pane.setOpacity(0);
        TranslateTransition tt = new TranslateTransition(Duration.millis(250), pane);
        tt.setFromY(-30); tt.setToY(0);
        FadeTransition ft = new FadeTransition(Duration.millis(250), pane);
        ft.setFromValue(0); ft.setToValue(0.85);
        ParallelTransition pt = new ParallelTransition(tt, ft);
        pt.setDelay(Duration.millis(index * 60));
        pt.play();

        return pane;
    }
}
