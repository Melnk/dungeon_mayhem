package com.example.dungeon.network;

import com.example.dungeon.game.*;

public class TestNetwork {
    public static void main(String[] args) {
        System.out.println("=== ТЕСТ СЕТЕВОГО ВЗАИМОДЕЙСТВИЯ ===");

        // Тест создания карт
        Card attackCard = new Card(CardType.ATTACK, "Огненный шар");
        Card defenseCard = new Card(CardType.DEFEND, "Магический щит");
        Card healCard = new Card(CardType.HEAL, "Целебное зелье");

        System.out.println("Карты созданы:");
        System.out.println("1. " + attackCard);
        System.out.println("2. " + defenseCard);
        System.out.println("3. " + healCard);

        // Тест игроков
        Player player1 = new Player("Герой");
        Player player2 = new Player("Враг");

        System.out.println("\nИгроки созданы:");
        System.out.println("Игрок 1: " + player1.getName() + " (HP: " + player1.getHealth() + ")");
        System.out.println("Игрок 2: " + player2.getName() + " (HP: " + player2.getHealth() + ")");

        // Тест механики боя
        System.out.println("\n=== ТЕСТ БОЯ ===");

        // Игрок 1 атакует
        System.out.println("Игрок 1 атакует на 2 урона");
        player2.takeDamage(2);
        System.out.println("Игрок 2: HP=" + player2.getHealth() + ", Щит=" + player2.getShield());

        // Игрок 2 использует защиту
        System.out.println("\nИгрок 2 использует защиту (+1 щит)");
        player2.setShield(player2.getShield() + 1);
        System.out.println("Игрок 2: HP=" + player2.getHealth() + ", Щит=" + player2.getShield());

        // Игрок 1 снова атакует
        System.out.println("\nИгрок 1 снова атакует на 2 урона");
        player2.takeDamage(2);
        System.out.println("Игрок 2: HP=" + player2.getHealth() + ", Щит=" + player2.getShield());

        // Игрок 2 лечится
        System.out.println("\nИгрок 2 лечится (+1 HP)");
        player2.setHealth(player2.getHealth() + 1);
        System.out.println("Игрок 2: HP=" + player2.getHealth() + ", Щит=" + player2.getShield());

        System.out.println("\n=== ТЕСТ ЗАВЕРШЕН ===");
    }
}
