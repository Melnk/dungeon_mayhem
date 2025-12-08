package com.example.dungeon.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import com.example.dungeon.game.Card;

public class AnimationManager {

    private final Canvas battleCanvas;

    public AnimationManager(Canvas battleCanvas) {
        this.battleCanvas = battleCanvas;
    }

    public void showCardAnimation(Card card) {
        Platform.runLater(() -> {
            GraphicsContext gc = battleCanvas.getGraphicsContext2D();
            battleCanvas.setVisible(true);
            battleCanvas.setOpacity(1);
            gc.clearRect(0,0,battleCanvas.getWidth(), battleCanvas.getHeight());

            Color animationColor;
            String animationText = "";
            String effectText = "";

            switch (card.getType()) {
                case ATTACK -> { animationColor = Color.rgb(231,76,60,0.8); animationText = "⚔ АТАКА! ⚔"; effectText = "2 УРОНА"; }
                case DEFEND -> { animationColor = Color.rgb(52,152,219,0.8); animationText = "🛡 ЗАЩИТА 🛡"; effectText = "+1 ЩИТ"; }
                case HEAL -> { animationColor = Color.rgb(46,204,113,0.8); animationText = "❤ ЛЕЧЕНИЕ ❤"; effectText = "+1 HP"; }
                default -> { animationColor = Color.GRAY; animationText = "ДЕЙСТВИЕ"; }
            }

            gc.setFill(animationColor);
            gc.fillRect(0,0,battleCanvas.getWidth(), battleCanvas.getHeight());

            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font("Arial", 28));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(animationText, battleCanvas.getWidth()/2, 50);
            gc.setFont(javafx.scene.text.Font.font("Arial", 20));
            gc.fillText(effectText, battleCanvas.getWidth()/2, 80);

            FadeTransition fadeOut = new FadeTransition(Duration.seconds(1.2), battleCanvas);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> battleCanvas.setVisible(false));
            fadeOut.play();
        });
    }
}
