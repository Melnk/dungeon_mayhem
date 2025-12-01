package com.example.dungeon.game;

import lombok.Getter;
import java.io.Serializable;

@Getter
public class Card implements Serializable {
    private CardType type;
    private String name;

    public Card(CardType type, String name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public String toString() {
        return name + " (" + type + ")";
    }
}
