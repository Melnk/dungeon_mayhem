package com.example.dungeon.game;

import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Player implements Serializable {
    private String name;
    private int health;
    private int maxHealth;
    private int shield;
    private List<Card> hand;
    private CharacterType character;
    private boolean hasUsedSpecialAbility = false;

    // Статистика
    private int totalDamageDealt = 0;
    private int totalDamageTaken = 0;
    private int totalHealing = 0;
    private int cardsPlayed = 0;

    public Player(String name) {
        this.name = name;
        this.shield = 0;
        this.hand = new ArrayList<>();

        // Рандомный персонаж
        this.character = CharacterType.getRandom();
        this.maxHealth = character.getBaseHealth();
        this.health = maxHealth;

        System.out.println("🎭 Создан игрок " + name + " как " + character.getName());
    }

    public Player(String name, CharacterType character) {
        this.name = name;
        this.character = character;
        this.shield = 0;
        this.hand = new ArrayList<>();
        this.maxHealth = character.getBaseHealth();
        this.health = maxHealth;
    }

    public void takeDamage(int damage) {
        // Сначала удар по щиту
        if (shield > 0) {
            // Применяем множитель защиты
            double actualDamage = damage * (1.0 / character.getDefenseMultiplier());
            int damageToShield = (int) Math.min(shield, actualDamage);
            shield -= damageToShield;
            damage -= (int)(damageToShield * character.getDefenseMultiplier());
        }

        // Затем по здоровью
        if (damage > 0) {
            health -= damage;
            totalDamageTaken += damage;
        }

        health = Math.max(0, health);
    }

    public int calculateAttackDamage(int baseDamage) {
        // Применяем множитель атаки персонажа
        int damage = (int)(baseDamage * character.getAttackMultiplier());
        totalDamageDealt += damage;
        cardsPlayed++;
        return damage;
    }

    public int calculateHealing(int baseHeal) {
        // Применяем множитель лечения
        int heal = (int)(baseHeal * character.getHealMultiplier());
        totalHealing += heal;
        cardsPlayed++;
        return heal;
    }

    public int calculateShield(int baseShield) {
        // Применяем множитель защиты для щита
        return (int)(baseShield * character.getDefenseMultiplier());
    }

    public void heal(int amount) {
        int actualHeal = Math.min(maxHealth - health, amount);
        health += actualHeal;
        totalHealing += actualHeal;
    }

    public void addShield(int amount) {
        shield += amount;
    }

    public boolean isAlive() {
        return health > 0;
    }

    public String getCharacterInfo() {
        return character.getStats();
    }

    public String getShortInfo() {
        return String.format("%s %s (❤%d/%d 🛡%d)",
            character.getIcon(), character.getName(), health, maxHealth, shield);
    }

    public void resetForNewGame() {
        this.shield = 0;
        this.health = maxHealth;
        this.hand.clear();
        this.hasUsedSpecialAbility = false;
        this.totalDamageDealt = 0;
        this.totalDamageTaken = 0;
        this.totalHealing = 0;
        this.cardsPlayed = 0;
    }
}
