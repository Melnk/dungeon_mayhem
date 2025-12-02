package com.example.dungeon.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

public class HealthBarRenderer {

    public void drawHealthBar(GraphicsContext gc, int health, int shield, boolean isOpponent) {
        double width = 150;
        double height = 20;
        gc.clearRect(0, 0, width, height);

        gc.setFill(Color.rgb(50,50,50));
        gc.fillRect(0,0,width,height);

        double healthWidth = (health / 10.0) * width;
        gc.setFill(Color.rgb(46,204,113));
        gc.fillRect(0,0,healthWidth,height);

        if (shield > 0) {
            double shieldWidth = Math.min(shield, 10) / 10.0 * width;
            gc.setFill(Color.rgb(52,152,219,0.7));
            gc.fillRect(0,0,shieldWidth,height);

            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font("Arial", 10));
            if (isOpponent) {
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText("🛡" + shield, width - 3, 14);
            } else {
                gc.setTextAlign(TextAlignment.LEFT);
                gc.fillText("🛡" + shield, 3, 14);
            }
        }

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeRect(0,0,width,height);

        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font("Arial", 10));
        if (isOpponent) {
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText("❤" + health, width - (shield > 0 ? 25 : 5), 14);
        } else {
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText("❤" + health, (shield > 0 ? 25 : 5), 14);
        }
    }
}
