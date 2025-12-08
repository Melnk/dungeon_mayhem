package com.example.dungeon.game;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class Card implements Serializable {
    private CardType type;
    private String name;
    private int value;
    private String description;
    private String specialEffect;

    public Card(CardType type, String name) {
        this.type = type;
        this.name = name;
        this.value = type.getBaseValue();
        this.description = generateDescription();
    }

    public Card(CardType type, String name, int value) {
        this.type = type;
        this.name = name;
        this.value = value;
        this.description = generateDescription();
    }

    private String generateDescription() {
        switch (type) {
            case ATTACK:
                return String.format("Наносит %d урона противнику", value);
            case DEFEND:
                return String.format("Дает %d единиц защиты", value);
            case HEAL:
                return String.format("Восстанавливает %d здоровья", value);
            case DOUBLE_ATTACK:
                return String.format("Наносит %d урона (двойная атака)", value);
            case SUPER_SHIELD:
                return String.format("Дает %d защиты (усиленный щит)", value);
            case ULTIMATE_HEAL:
                return String.format("Восстанавливает %d здоровья (усиленное лечение)", value);
            case COMBO_ATTACK:
                return String.format("Наносит %d урона и дает 1 карту", value);
            case COUNTER_ATTACK:
                return String.format("Наносит %d урона и блокирует следующую атаку", value);
            case BERSERK_RAGE:
                return String.format("Наносит %d урона, но вы получаете 2 урона", value);
            case HOLY_LIGHT:
                return String.format("Восстанавливает %d здоровья всем союзникам", value);
            case BACKSTAB:
                return String.format("Наносит %d урона (игнорирует защиту)", value);
            case FIREBALL:
                return String.format("Наносит %d урона всем противникам", value);
            default:
                return "Особая карта";
        }
    }

    public String getFullName() {
        return type.getIcon() + " " + name;
    }

    public String getCardInfo() {
        return String.format("%s\n%s\nЗначение: %d", getFullName(), description, value);
    }
}
