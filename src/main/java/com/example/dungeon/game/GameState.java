package com.example.dungeon.game;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
public class GameState implements Serializable {
    private Player currentPlayer;
    private Player opponentPlayer;
    private boolean isPlayerTurn;
    private String gameStatus;
}
