package com.example.dungeon.game;

import java.util.List;

public interface GameEventListener {
    void onHealthUpdated(int playerHP, int playerShield, int opponentHP, int opponentShield);
    void onHandUpdated(List<Card> playerHand);
    void onOpponentHandCountUpdated(int count);
    void onGameStatusUpdated(String status);
    void onActionOccurred(String description);
    void onGameOver(boolean playerWon, int playerHP, int opponentHP);
    void onCardPlayed(Card card, boolean byOpponent);
}
