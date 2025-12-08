package com.example.dungeon.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Рисует полосу здоровья, поддерживает произвольный maxHP и отображение щита.
 * Автоматически использует размер Canvas, не хардкодит ширину.
 */
public class HealthBarRenderer {

    /**
     * Универсальный метод: рисует полосу здоровья по текущему и максимальному ХП.
     *
     * @param gc        GraphicsContext (gc.getCanvas() должен быть корректно инициализирован)
     * @param health    текущие HP (>=0)
     * @param maxHealth максимальные HP (>0)
     * @param shield    текущее значение щита (>=0)
     * @param isOpponent true если рисуем полосу противника (текст выравнивается вправо)
     */
    public void drawHealthBar(GraphicsContext gc, int health, int maxHealth, int shield, boolean isOpponent) {
        if (gc == null || gc.getCanvas() == null) return;

        double width = Math.max(10, gc.getCanvas().getWidth());
        double height = Math.max(8, gc.getCanvas().getHeight());

        // Защита от деления на ноль
        int safeMax = Math.max(1, maxHealth);
        int safeHealth = Math.max(0, health);
        int safeShield = Math.max(0, shield);

        double fraction = Math.max(0.0, Math.min(1.0, (double) safeHealth / safeMax));
        double shieldFraction = Math.max(0.0, Math.min(1.0, (double) safeShield / safeMax));

        // Очистка
        gc.clearRect(0, 0, width, height);

        // Фон (темная полоса)
        gc.setFill(Color.web("#2f2f2f"));
        gc.fillRoundRect(0, 0, width, height, height, height);

        // Полоса здоровья
        double healthWidth = fraction * width;
        Color healthColor = pickHealthColor(fraction);
        gc.setFill(healthColor);
        gc.fillRoundRect(0, 0, Math.max(1, healthWidth), height, height, height);

        // Щит (идёт поверх HP, полупрозрачный)
        if (safeShield > 0) {
            double shieldWidth = Math.min(shieldFraction * width, width);
            gc.setGlobalAlpha(0.8);
            gc.setFill(Color.web("#3498db")); // синий для щита
            gc.fillRoundRect(0, 0, Math.max(1, shieldWidth), height, height, height);
            gc.setGlobalAlpha(1.0);
        }

        // Рамка
        gc.setStroke(Color.web("#222222"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(0.5, 0.5, width - 1, height - 1, height, height);

        // Текст (❤current/max и щит)
        int fontSize = (int) Math.max(10, height * 0.6);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", fontSize));

        String hpText = "❤ " + safeHealth + "/" + safeMax;
        String shieldText = safeShield > 0 ? "🛡 " + safeShield : "";

        if (isOpponent) {
            gc.setTextAlign(TextAlignment.RIGHT);
            // hp left of right edge taking into account shield label
            double xHP = width - 4;
            gc.fillText(hpText, xHP, height * 0.75);
            if (!shieldText.isEmpty()) {
                gc.setFill(Color.web("#dfefff"));
                gc.fillText(shieldText, xHP - gc.getFont().getSize() * 6, height * 0.75);
            }
        } else {
            gc.setTextAlign(TextAlignment.LEFT);
            double xHP = 4;
            gc.fillText(hpText, xHP, height * 0.75);
            if (!shieldText.isEmpty()) {
                gc.setFill(Color.web("#dfefff"));
                gc.fillText(shieldText, xHP + gc.getFont().getSize() * 6, height * 0.75);
            }
        }
    }

    /**
     * Backward-compatible method — если нет maxHP, используется 10.
     */
    public void drawHealthBar(GraphicsContext gc, int health, int shield, boolean isOpponent) {
        drawHealthBar(gc, health, 10, shield, isOpponent);
    }

    private Color pickHealthColor(double fraction) {
        if (fraction > 0.66) return Color.web("#66ff66");        // зеленый
        if (fraction > 0.33) return Color.web("#ffcc33");        // желтый
        return Color.web("#ff5c5c");                             // красный
    }
}
