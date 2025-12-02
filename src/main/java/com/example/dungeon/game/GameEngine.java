package com.example.dungeon.game;

import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GameEngine — игровая логика, не зависит от JavaFX UI.
 * Умеет стартовать одиночную игру, применять карты, проверять победу и запускать ход противника.
 */
public class GameEngine {

    private int playerHP = 10;
    private int playerShield = 0;
    private int opponentHP = 10;
    private int opponentShield = 0;

    private final List<Card> playerHand = new ArrayList<>();
    private final Random rnd = new Random();

    @Setter
    private GameEventListener listener;
    private boolean isPlayerTurn = true;
    private boolean gameOver = false;

    public boolean isPlayerTurn() { return isPlayerTurn; }

    public void startSinglePlayer() {
        playerHP = 10; playerShield = 0;
        opponentHP = 10; opponentShield = 0;
        playerHand.clear();
        // начальная рука
        playerHand.add(new Card(CardType.ATTACK, "Огненный шар"));
        playerHand.add(new Card(CardType.DEFENSE, "Железный щит"));
        playerHand.add(new Card(CardType.HEAL, "Целебное зелье"));
        playerHand.add(new Card(CardType.ATTACK, "Удар кинжалом"));
        playerHand.add(new Card(CardType.DEFENSE, "Магический барьер"));

        isPlayerTurn = true;
        gameOver = false;

        if (listener != null) {
            listener.onGameStatusUpdated("🎯 ВАШ ХОД");
            listener.onHealthUpdated(playerHP, playerShield, opponentHP, opponentShield);
            listener.onHandUpdated(new ArrayList<>(playerHand));
            listener.onOpponentHandCountUpdated(3); // примерное количество
        }
    }

    /**
     * Игрок или оппонент пытается сыграть карту. byOpponent==false — игрок.
     * Внутри проверяется очередность.
     */
    public synchronized void playCard(Card card, boolean byOpponent) {
        if (gameOver) return;

        if (!byOpponent) {
            // игрок
            if (!isPlayerTurn) {
                if (listener != null) listener.onActionOccurred("Сейчас не ваш ход!");
                return;
            }
            // удаляем карту из руки (по имени+тип)
            boolean removed = playerHand.removeIf(c -> c.getName().equals(card.getName()) && c.getType() == card.getType());
            if (!removed) {
                if (listener != null) listener.onActionOccurred("Карта не найдена в руке!");
                return;
            }
            applyCardEffect(card, false);
            if (listener != null) {
                listener.onCardPlayed(card, false);
                listener.onHandUpdated(new ArrayList<>(playerHand));
            }

            //Смена хода
            isPlayerTurn = false;
            if (listener != null) listener.onGameStatusUpdated("⏳ ХОД ПРОТИВНИКА");

            // Запускаем ход противника с заметной задержкой (1300-1600ms)
            new Thread(() -> {
                try { Thread.sleep(1400); } catch (InterruptedException ignored) {}
                opponentMakesMove();
                try { Thread.sleep(800); } catch (InterruptedException ignored) {} // даём время показать ход
                addRandomCardToHand();
                isPlayerTurn = true;
                if (listener != null) listener.onGameStatusUpdated("🎯 ВАШ ХОД");
            }, "AI-Move-Thread").start();


        } else {
            // ход противника (в одиночной игре)
            applyCardEffect(card, true);
            if (listener != null) listener.onCardPlayed(card, true);
        }
    }

    private void applyCardEffect(Card card, boolean byOpponent) {
        switch (card.getType()) {
            case ATTACK -> {
                int dmg = 2;
                if (byOpponent) {
                    if (playerShield > 0) {
                        playerShield -= dmg;
                        if (playerShield < 0) { playerHP += playerShield; playerShield = 0; }
                    } else playerHP = Math.max(0, playerHP - dmg);
                } else {
                    if (opponentShield > 0) {
                        opponentShield -= dmg;
                        if (opponentShield < 0) { opponentHP += opponentShield; opponentShield = 0; }
                    } else opponentHP = Math.max(0, opponentHP - dmg);
                }
            }
            case DEFENSE -> {
                if (byOpponent) opponentShield = Math.min(10, opponentShield + 1);
                else playerShield = Math.min(10, playerShield + 1);
            }
            case HEAL -> {
                if (byOpponent) opponentHP = Math.min(10, opponentHP + 1);
                else playerHP = Math.min(10, playerHP + 1);
            }
        }

        if (listener != null) listener.onHealthUpdated(playerHP, playerShield, opponentHP, opponentShield);
        checkWinCondition();
    }

    private void opponentMakesMove() {
        if (gameOver) return;
        int action = rnd.nextInt(3);
        Card card;
        switch (action) {
            case 0 -> card = new Card(CardType.ATTACK, "Темный удар");
            case 1 -> card = new Card(CardType.DEFENSE, "Теневой щит");
            default -> card = new Card(CardType.HEAL, "Темное зелье");
        }
        playCard(card, true);
    }

    private void addRandomCardToHand() {
        if (playerHand.size() >= 5) {
            if (listener != null) listener.onActionOccurred("Рука полна, карта не взята.");
            return;
        }
        Card[] possible = {
            new Card(CardType.ATTACK, "Огненный шар"), new Card(CardType.DEFENSE, "Железный щит"),
            new Card(CardType.HEAL, "Целебное зелье"), new Card(CardType.ATTACK, "Удар кинжалом"),
            new Card(CardType.DEFENSE, "Магический барьер"), new Card(CardType.HEAL, "Эликсир жизни")
        };
        Card n = possible[rnd.nextInt(possible.length)];
        playerHand.add(n);
        if (listener != null) listener.onHandUpdated(new ArrayList<>(playerHand));
    }

    private void checkWinCondition() {
        if (gameOver) return;
        if (opponentHP <= 0 && playerHP > 0) {
            gameOver = true;
            if (listener != null) listener.onGameOver(true, playerHP, opponentHP);
        } else if (playerHP <= 0 && opponentHP > 0) {
            gameOver = true;
            if (listener != null) listener.onGameOver(false, playerHP, opponentHP);
        } else if (playerHP <= 0 && opponentHP <= 0) {
            // ничья — считаем поражением игрока
            gameOver = true;
            if (listener != null) listener.onGameOver(false, playerHP, opponentHP);
        }
    }
}
