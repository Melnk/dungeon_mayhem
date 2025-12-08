package com.example.dungeon.game;

import java.util.Random;

public class OpponentAI {
    private Random rnd = new Random();
    public Card chooseCard() {
        int r = rnd.nextInt(3);
        return switch (r) {
            case 0 -> new Card(CardType.ATTACK, "Темный удар");
            case 1 -> new Card(CardType.DEFEND, "Теневой щит");
            default -> new Card(CardType.HEAL, "Темное зелье");
        };
    }
}
