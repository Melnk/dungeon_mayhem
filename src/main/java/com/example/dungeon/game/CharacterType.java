package com.example.dungeon.game;

import lombok.Getter;

@Getter
public enum CharacterType {
    // Базовые параметры: название, здоровье, атака, защита, лечение, иконка, цвет
    BARBARIAN("Варвар", "⚔️", 35, 1.5, 0.8, 0.7, "#FF6B6B",
        "Берсерк - получает на 50% больше урона от атак!"),

    PALADIN("Паладин", "🛡️", 40, 0.9, 1.6, 1.2, "#4ECDC4",
        "Святой щит - защита на 60% эффективнее!"),

    ROGUE("Плут", "🗡️", 25, 1.8, 0.7, 0.9, "#FFD166",
        "Критический удар - шанс на двойной урон!"),

    WIZARD("Маг", "🔮", 30, 1.3, 1.0, 1.5, "#9D4EDD",
        "Магический барьер - часть урона поглощается маной!");

    private final String name;
    private final String icon;
    private final int baseHealth;
    private final double attackMultiplier;
    private final double defenseMultiplier;
    private final double healMultiplier;
    private final String color;
    private final String specialAbility;

    CharacterType(String name, String icon, int baseHealth,
                  double attackMultiplier, double defenseMultiplier,
                  double healMultiplier, String color, String specialAbility) {
        this.name = name;
        this.icon = icon;
        this.baseHealth = baseHealth;
        this.attackMultiplier = attackMultiplier;
        this.defenseMultiplier = defenseMultiplier;
        this.healMultiplier = healMultiplier;
        this.color = color;
        this.specialAbility = specialAbility;
    }

    public static CharacterType getRandom() {
        CharacterType[] values = values();
        return values[(int) (Math.random() * values.length)];
    }

    public String getStats() {
        return String.format("%s %s\n❤ HP: %d | ⚔ Атака: x%.1f | 🛡 Защита: x%.1f | ❤ Лечение: x%.1f\n✨ %s",
            icon, name, baseHealth, attackMultiplier, defenseMultiplier, healMultiplier, specialAbility);
    }
}
