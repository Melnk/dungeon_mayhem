package com.example.dungeon.game;

public enum CardType {
    // Базовые карты
    ATTACK("Атака", "⚔️", "#FF4444", 2),
    DEFEND("Защита", "🛡️", "#4444FF", 1),
    HEAL("Лечение", "❤️", "#44FF44", 1),

    // Специальные карты
    DOUBLE_ATTACK("Двойная атака", "⚔️⚔️", "#FF0000", 3),
    SUPER_SHIELD("Супер щит", "🛡️🛡️", "#0000FF", 3),
    ULTIMATE_HEAL("Супер лечение", "❤️❤️", "#00FF00", 3),

    // Комбо карты
    COMBO_ATTACK("Комбо атака", "⚔️✨", "#FF8800", 2),
    COUNTER_ATTACK("Контратака", "🔄", "#8800FF", 2),

    // Особые карты для персонажей
    BERSERK_RAGE("Ярость берсерка", "😡", "#FF0000", 4),
    HOLY_LIGHT("Святой свет", "✨", "#FFFF00", 3),
    BACKSTAB("Удар в спину", "🗡️", "#666666", 5),
    FIREBALL("Огненный шар", "🔥", "#FF6600", 4);

    private final String displayName;
    private final String icon;
    private final String color;
    private final int baseValue;

    CardType(String displayName, String icon, String color, int baseValue) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.baseValue = baseValue;
    }

    public String getDisplayName() { return displayName; }
    public String getIcon() { return icon; }
    public String getColor() { return color; }
    public int getBaseValue() { return baseValue; }
}
