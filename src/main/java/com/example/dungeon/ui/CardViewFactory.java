package com.example.dungeon.ui;

import com.example.dungeon.game.Card;
import com.example.dungeon.game.CardType;
import javafx.scene.control.Label;
import javafx.scene.effect.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.Consumer;

public class CardViewFactory {

    public StackPane createCardPane(Card card, int index, boolean enabled, Consumer<Card> clickHandler) {
        StackPane cardPane = new StackPane();
        cardPane.getStyleClass().add("card-pane");

        // Основной прямоугольник карты
        Rectangle cardBg = new Rectangle(100, 140);
        cardBg.setArcWidth(15);
        cardBg.setArcHeight(15);

        // Градиент в зависимости от типа карты
        String gradientColor = getColorForCard(card.getType());
        cardBg.setStyle(String.format(
            "-fx-fill: linear-gradient(from 0%% 0%% to 100%% 100%%, %s, #000000);",
            gradientColor
        ));

        // Эффекты для карты
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web(gradientColor.replace("0x", "#")));
        shadow.setRadius(15);
        shadow.setSpread(0.3);
        shadow.setOffsetX(0);
        shadow.setOffsetY(0);

        InnerShadow innerShadow = new InnerShadow();
        innerShadow.setColor(Color.BLACK);
        innerShadow.setRadius(10);
        innerShadow.setOffsetX(2);
        innerShadow.setOffsetY(2);

        // ИСПРАВЛЕНИЕ: Комбинируем эффекты через setInput
        Blend blendEffect = new Blend();
        blendEffect.setMode(BlendMode.MULTIPLY);
        blendEffect.setTopInput(innerShadow);
        blendEffect.setBottomInput(shadow);

        // Для хранения текущего эффекта (будем менять его динамически)
        Blend[] currentEffect = new Blend[1]; // Массив для хранения ссылки
        currentEffect[0] = blendEffect;
        cardBg.setEffect(currentEffect[0]);

        // Текст на карте
        Label nameLabel = new Label(card.getName());
        nameLabel.getStyleClass().add("card-name");
        nameLabel.setFont(Font.font("Arial Black", FontWeight.BLACK, 12));
        nameLabel.setTranslateY(-45);

        Label typeLabel = new Label(card.getType().getDisplayName());
        typeLabel.getStyleClass().add("card-type");
        typeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        typeLabel.setTranslateY(45);

        Label valueLabel = new Label(String.valueOf(card.getValue()));
        valueLabel.getStyleClass().add("card-value");
        valueLabel.setFont(Font.font("Arial Black", FontWeight.BLACK, 20));

        // Иконка типа карты
        Label iconLabel = new Label(card.getType().getIcon());
        iconLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        iconLabel.setTranslateY(-10);

        // Добавляем все элементы на карту
        cardPane.getChildren().addAll(cardBg, nameLabel, typeLabel, valueLabel, iconLabel);

        // Добавляем эффект при наведении только если карта доступна
        if (enabled) {
            cardPane.setOnMouseEntered(e -> {
                // Эффект свечения при наведении
                Glow glow = new Glow();
                glow.setLevel(0.3);

                // Создаем новый Blend с эффектом свечения
                Blend glowBlend = new Blend(BlendMode.MULTIPLY, glow, blendEffect);
                cardBg.setEffect(glowBlend);

                cardPane.setTranslateY(-10);
                cardPane.setRotate((Math.random() * 6) - 3); // Случайный небольшой поворот

                // Обновляем текущий эффект
                currentEffect[0] = glowBlend;
            });

            cardPane.setOnMouseExited(e -> {
                cardBg.setEffect(blendEffect);
                cardPane.setTranslateY(0);
                cardPane.setRotate(0);
                currentEffect[0] = blendEffect;
            });

            cardPane.setOnMouseClicked(e -> {
                // Анимация при клике
                javafx.animation.ScaleTransition clickAnim = new javafx.animation.ScaleTransition(
                    javafx.util.Duration.millis(150), cardPane);
                clickAnim.setToX(0.9);
                clickAnim.setToY(0.9);
                clickAnim.setAutoReverse(true);
                clickAnim.setCycleCount(2);
                clickAnim.play();

                clickHandler.accept(card);
            });
        } else {
            // Для недоступных карт делаем менее яркими
            ColorAdjust dull = new ColorAdjust();
            dull.setBrightness(-0.3);
            dull.setSaturation(-0.5);

            // Создаем Blend с эффектом затемнения
            Blend dullBlend = new Blend(BlendMode.MULTIPLY, dull, blendEffect);
            cardBg.setEffect(dullBlend);
            currentEffect[0] = dullBlend;
        }

        // Анимация появления карты
        cardPane.setTranslateY(50);
        cardPane.setOpacity(0);
        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(300 + index * 50), cardPane);
        fadeIn.setToValue(1);
        fadeIn.play();

        javafx.animation.TranslateTransition slideIn = new javafx.animation.TranslateTransition(
            javafx.util.Duration.millis(300 + index * 50), cardPane);
        slideIn.setToY(0);
        slideIn.play();

        // Добавляем небольшой начальный поворот для эффекта веера
        double rotation = (index - cardPane.getChildren().size() / 2.0) * 3;
        cardPane.setRotate(rotation);
        javafx.animation.RotateTransition rotateIn = new javafx.animation.RotateTransition(
            javafx.util.Duration.millis(400 + index * 50), cardPane);
        rotateIn.setToAngle(0);
        rotateIn.play();

        return cardPane;
    }

    private String getColorForCard(CardType type) {
        switch (type) {
            case ATTACK:
            case DOUBLE_ATTACK:
            case BERSERK_RAGE:
            case FIREBALL:
                return "#FF0000"; // Красный для атаки
            case DEFEND:
            case SUPER_SHIELD:
                return "#0000FF"; // Синий для защиты
            case HEAL:
            case ULTIMATE_HEAL:
            case HOLY_LIGHT:
                return "#00FF00"; // Зеленый для лечения
            default:
                return "#FF8800"; // Оранжевый для остальных
        }
    }

    public StackPane createHiddenCard(int index) {
        StackPane hiddenCard = new StackPane();

        Rectangle cardBg = new Rectangle(100, 140);
        cardBg.setArcWidth(15);
        cardBg.setArcHeight(15);
        cardBg.setStyle("-fx-fill: linear-gradient(from 0% 0% to 100% 100%, #660000, #000000);");
        cardBg.setStroke(Color.web("#880000"));
        cardBg.setStrokeWidth(3);

        // Эффект для скрытой карты
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web("#660000"));
        shadow.setRadius(10);
        shadow.setOffsetX(3);
        shadow.setOffsetY(3);

        InnerShadow innerGlow = new InnerShadow();
        innerGlow.setColor(Color.web("#FF0000"));
        innerGlow.setRadius(15);
        innerGlow.setOffsetX(0);
        innerGlow.setOffsetY(0);

        // Создаем Blend эффект для скрытой карты
        Blend hiddenBlend = new Blend(BlendMode.MULTIPLY, innerGlow, shadow);
        cardBg.setEffect(hiddenBlend);

        // Знак вопроса на скрытой карте
        Label questionMark = new Label("?");
        questionMark.setFont(Font.font("Arial Black", FontWeight.BLACK, 48));
        questionMark.setTextFill(Color.web("#FF0000"));

        // Эффект для знака вопроса
        DropShadow textShadow = new DropShadow();
        textShadow.setColor(Color.BLACK);
        textShadow.setRadius(8);
        textShadow.setOffsetX(3);
        textShadow.setOffsetY(3);
        questionMark.setEffect(textShadow);

        hiddenCard.getChildren().addAll(cardBg, questionMark);
        hiddenCard.setOpacity(0.9);

        // Анимация для скрытой карты (пульсация)
        hiddenCard.setRotate((index * 7) - 10); // Немного поворачиваем для эффекта

        // Эффект пульсации для скрытых карт
        javafx.animation.FadeTransition pulse = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(1500 + index * 200), hiddenCard);
        pulse.setFromValue(0.7);
        pulse.setToValue(1.0);
        pulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();

        return hiddenCard;
    }
}
