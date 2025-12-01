package com.example.dungeon.game;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Player implements Serializable {
    private String name;
    private int health;
    private int shield; // щит, броня, похйу
    private List<Card> hand;

    public Player(String name) {
        this.name = name;
        this.shield = 0;
        this.health = 10;
        this.hand = new ArrayList<>();
    }

    public String getName() {return name;}
    public void setName(String name) {this.name = name;}
    public int getHealth() {return health;}
    public void setHealth(int health) {this.health = health;}
    public int getShield() {return shield;}
    public void setShield(int shield) {this.shield = shield;}
    public List<Card> getHand() {return hand;}
    public void setHand(List<Card> hand) {this.hand = hand;}

    public void takeDamage(int damage) {
        if (shield > 0) {
            int remainingDamage = damage - shield;
            shield = Math.max(0, shield - damage);
            if (remainingDamage > 0) {
                health = Math.max(0, health - remainingDamage);
            }
        } else {
            health = Math.max(0, health - damage);
        }
    }

    public boolean isAlive() {
        return health > 0;
    }
}
