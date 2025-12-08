package com.example.dungeon.game;

import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GameEngine — игровая логика, не зависит от JavaFX UI.
 * Теперь работает с персонажами и их множителями.
 */
public class GameEngine {

    private Player player;
    private Player opponent;
    private final Random rnd = new Random();

    @Setter
    private GameEventListener listener;
    private boolean isPlayerTurn = true;
    private boolean gameOver = false;

    public boolean isPlayerTurn() { return isPlayerTurn; }

    public boolean isPlayerWinner() {
        return player.isAlive() && !opponent.isAlive();
    }

    public void startSinglePlayer() {
        // Создаем игрока и противника с рандомными персонажами
        player = new Player("Герой");
        opponent = new Player("Противник");

        // Логируем выбор персонажей
        System.out.println("🎭 Игрок выбран как: " + player.getCharacter().getName());
        System.out.println("🎭 Противник выбран как: " + opponent.getCharacter().getName());

        // Очищаем руки и сбрасываем состояние
        player.getHand().clear();
        opponent.getHand().clear();

        // Начальные карты для игрока
        List<Card> initialHand = generateInitialHand();
        player.getHand().addAll(initialHand);

        // Даем противнику 3 карты для отображения
        List<Card> opponentHand = generateInitialHand();
        opponent.getHand().addAll(opponentHand.subList(0, 3)); // Только 3 карты для отображения

        isPlayerTurn = true;
        gameOver = false;

        if (listener != null) {
            // Отправляем информацию о персонажах
            listener.onActionOccurred("⚔️ БИТВА НАЧИНАЕТСЯ!");
            listener.onActionOccurred("Ваш персонаж: " + player.getCharacter().getName());
            listener.onActionOccurred("Противник: " + opponent.getCharacter().getName());

            listener.onGameStatusUpdated("🎯 ВАШ ХОД");
            listener.onHealthUpdated(
                player.getHealth(),
                player.getShield(),
                opponent.getHealth(),
                opponent.getShield()
            );
            listener.onHandUpdated(new ArrayList<>(player.getHand()));
            listener.onOpponentHandCountUpdated(opponent.getHand().size());
        }
    }

    private List<Card> generateInitialHand() {
        List<Card> hand = new ArrayList<>();
        CardType[] types = CardType.values();

        // Балансировка: даем по 2 карты каждого базового типа
        String[][] cardNames = {
            {"Огненный шар", "Ледяная стрела", "Молния", "Удар кинжалом", "Ядовитый укус"},
            {"Железный щит", "Магический барьер", "Доспех дракона", "Эгида защиты", "Священный щит"},
            {"Целебное зелье", "Эликсир жизни", "Нектар здоровья", "Бальзам восстановления", "Настойка выносливости"}
        };

        // По 2 карты каждого базового типа (Атака, Защита, Лечение)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                String name = cardNames[i][rnd.nextInt(cardNames[i].length)];
                hand.add(new Card(types[i], name));
            }
        }

        // Добавляем 1 специальную карту (если есть больше типов)
        if (types.length > 3) {
            CardType specialType = types[3 + rnd.nextInt(types.length - 3)];
            String specialName = getSpecialCardName(specialType);
            hand.add(new Card(specialType, specialName));
        }

        return hand;
    }

    private String getSpecialCardName(CardType type) {
        switch (type) {
            case DOUBLE_ATTACK: return "Двойная атака";
            case SUPER_SHIELD: return "Супер щит";
            case ULTIMATE_HEAL: return "Супер лечение";
            case COMBO_ATTACK: return "Комбо удар";
            case COUNTER_ATTACK: return "Контратака";
            case BERSERK_RAGE: return "Ярость берсерка";
            case HOLY_LIGHT: return "Святой свет";
            case BACKSTAB: return "Удар в спину";
            case FIREBALL: return "Огненный шар";
            default: return "Особая карта";
        }
    }

    /**
     * Игрок или оппонент пытается сыграть карту. byOpponent==false — игрок.
     * Внутри проверяется очередность.
     */
    public synchronized void playCard(Card card, boolean byOpponent) {
        if (gameOver) return;

        if (!byOpponent) {
            // Ход игрока
            if (!isPlayerTurn) {
                if (listener != null) listener.onActionOccurred("Сейчас не ваш ход!");
                return;
            }

            // Проверяем, есть ли карта в руке
            boolean removed = player.getHand().removeIf(c ->
                c.getName().equals(card.getName()) && c.getType() == card.getType()
            );

            if (!removed) {
                if (listener != null) listener.onActionOccurred("Карта не найдена в руке!");
                return;
            }

            // Применяем эффект карты
            applyCardEffect(card, false);

            if (listener != null) {
                listener.onCardPlayed(card, false);
                listener.onHandUpdated(new ArrayList<>(player.getHand()));
            }

            // Смена хода
            isPlayerTurn = false;
            if (listener != null) listener.onGameStatusUpdated("⏳ ХОД ПРОТИВНИКА");

            // Запускаем ход противника с заметной задержкой
            new Thread(() -> {
                try { Thread.sleep(1400); } catch (InterruptedException ignored) {}
                opponentMakesMove();
                try { Thread.sleep(800); } catch (InterruptedException ignored) {} // даём время показать ход
                addRandomCardToHand();
                isPlayerTurn = true;
                if (listener != null) listener.onGameStatusUpdated("🎯 ВАШ ХОД");
            }, "AI-Move-Thread").start();

        } else {
            // Ход противника (в одиночной игре)
            applyCardEffect(card, true);
            if (listener != null) listener.onCardPlayed(card, true);
        }
    }

    private void applyCardEffect(Card card, boolean byOpponent) {
        Player caster = byOpponent ? opponent : player;
        Player target = byOpponent ? player : opponent;

        StringBuilder actionMessage = new StringBuilder();

        switch (card.getType()) {
            case ATTACK:
                int baseDamage = card.getValue();
                int actualDamage = caster.calculateAttackDamage(baseDamage);
                target.takeDamage(actualDamage);

                actionMessage.append("⚔ ").append(caster.getCharacter().getName())
                    .append(" атакует! Нанесено ").append(actualDamage).append(" урона.");
                break;

            case DEFEND:
                int baseShield = card.getValue();
                int actualShield = caster.calculateShield(baseShield);
                caster.addShield(actualShield);

                actionMessage.append("🛡 ").append(caster.getCharacter().getName())
                    .append(" ставит щит! +").append(actualShield).append(" защиты.");
                break;

            case HEAL:
                int baseHeal = card.getValue();
                int actualHeal = caster.calculateHealing(baseHeal);
                caster.heal(actualHeal);

                actionMessage.append("❤ ").append(caster.getCharacter().getName())
                    .append(" лечится! +").append(actualHeal).append(" здоровья.");
                break;

            case DOUBLE_ATTACK:
                // Двойная атака: наносит урон дважды
                int doubleDamage = caster.calculateAttackDamage(card.getValue());
                target.takeDamage(doubleDamage);
                // Второй удар
                target.takeDamage(doubleDamage / 2); // Второй удар слабее

                actionMessage.append("⚔⚔ ").append(caster.getCharacter().getName())
                    .append(" проводит двойную атаку! Нанесено ").append(doubleDamage + doubleDamage / 2).append(" урона.");
                break;

            case SUPER_SHIELD:
                int superShield = caster.calculateShield(card.getValue() * 2);
                caster.addShield(superShield);

                actionMessage.append("🛡🛡 ").append(caster.getCharacter().getName())
                    .append(" создает супер щит! +").append(superShield).append(" защиты.");
                break;

            case ULTIMATE_HEAL:
                int ultimateHeal = caster.calculateHealing(card.getValue() * 2);
                caster.heal(ultimateHeal);

                actionMessage.append("❤❤ ").append(caster.getCharacter().getName())
                    .append(" использует супер лечение! +").append(ultimateHeal).append(" здоровья.");
                break;

            case BERSERK_RAGE:
                // Ярость берсерка: много урона, но и сам получает урон
                int rageDamage = caster.calculateAttackDamage(card.getValue() * 2);
                target.takeDamage(rageDamage);
                caster.takeDamage(2); // Сам получает урон

                actionMessage.append("😡 ").append(caster.getCharacter().getName())
                    .append(" впадает в ярость! Нанесено ").append(rageDamage)
                    .append(" урона, но сам получил 2 урона.");
                break;

            case HOLY_LIGHT:
                // Святой свет: лечение и защита
                int holyHeal = caster.calculateHealing(card.getValue());
                caster.heal(holyHeal);
                caster.addShield(2);

                actionMessage.append("✨ ").append(caster.getCharacter().getName())
                    .append(" использует святой свет! +").append(holyHeal)
                    .append(" здоровья и +2 защиты.");
                break;

            case BACKSTAB:
                // Удар в спину: игнорирует часть защиты
                int backstabDamage = caster.calculateAttackDamage(card.getValue());
                int currentShield = target.getShield();
                if (currentShield > 0) {
                    target.setShield(currentShield / 2); // Уменьшает щит вдвое
                }
                target.takeDamage(backstabDamage);

                actionMessage.append("🗡️ ").append(caster.getCharacter().getName())
                    .append(" наносит удар в спину! Пробивает защиту и наносит ")
                    .append(backstabDamage).append(" урона.");
                break;

            case FIREBALL:
                // Огненный шар: урон по всем (в будущем для многопользовательской игры)
                int fireDamage = caster.calculateAttackDamage(card.getValue());
                target.takeDamage(fireDamage);

                actionMessage.append("🔥 ").append(caster.getCharacter().getName())
                    .append(" бросает огненный шар! Нанесено ").append(fireDamage).append(" урона.");
                break;

            default:
                // Для других типов карт - базовая атака
                int defaultDamage = caster.calculateAttackDamage(card.getValue());
                target.takeDamage(defaultDamage);
                actionMessage.append(caster.getCharacter().getName()).append(" использует ").append(card.getName());
                break;
        }

        if (listener != null) {
            listener.onHealthUpdated(
                player.getHealth(),
                player.getShield(),
                opponent.getHealth(),
                opponent.getShield()
            );
            listener.onActionOccurred(actionMessage.toString());
        }

        checkWinCondition();
    }

    private void opponentMakesMove() {
        if (gameOver) return;

        // Выбираем случайную карту из руки противника (или создаем новую)
        Card card;
        if (!opponent.getHand().isEmpty()) {
            // Берем случайную карту из руки
            card = opponent.getHand().get(rnd.nextInt(opponent.getHand().size()));
            opponent.getHand().remove(card);
        } else {
            // Если рука пуста, создаем случайную карту
            CardType[] types = CardType.values();
            CardType randomType = types[rnd.nextInt(Math.min(3, types.length))]; // Только базовые типы
            String[] cardNames = {
                "Темный удар", "Теневой щит", "Темное зелье",
                "Удар призрака", "Теневой барьер", "Некротическое зелье"
            };
            card = new Card(randomType, cardNames[rnd.nextInt(cardNames.length)]);
        }

        playCard(card, true);
    }

    private void addRandomCardToHand() {
        if (player.getHand().size() >= 7) { // Увеличили лимит руки
            if (listener != null) listener.onActionOccurred("Рука полна, карта не взята.");
            return;
        }

        // Создаем случайную карту
        CardType[] types = CardType.values();
        CardType randomType = types[rnd.nextInt(types.length)];

        String[] cardNames;
        switch (randomType) {
            case ATTACK:
                cardNames = new String[]{"Огненный шар", "Ледяная стрела", "Молния", "Удар кинжалом", "Ядовитый укус"};
                break;
            case DEFEND:
                cardNames = new String[]{"Железный щит", "Магический барьер", "Доспех дракона", "Эгида защиты", "Священный щит"};
                break;
            case HEAL:
                cardNames = new String[]{"Целебное зелье", "Эликсир жизни", "Нектар здоровья", "Бальзам восстановления", "Настойка выносливости"};
                break;
            default:
                cardNames = new String[]{"Особая карта", "Магический артефакт", "Древний свиток", "Мистическая реликвия"};
                break;
        }

        String name = cardNames[rnd.nextInt(cardNames.length)];
        Card newCard = new Card(randomType, name);
        player.getHand().add(newCard);

        if (listener != null) {
            listener.onHandUpdated(new ArrayList<>(player.getHand()));
            listener.onActionOccurred("🎴 Вы получили новую карту: " + newCard.getName());
        }
    }

    private void checkWinCondition() {
        if (gameOver) return;

        if (!opponent.isAlive() && player.isAlive()) {
            gameOver = true;
            if (listener != null) listener.onGameOver(true, player.getHealth(), opponent.getHealth());
        } else if (!player.isAlive() && opponent.isAlive()) {
            gameOver = true;
            if (listener != null) listener.onGameOver(false, player.getHealth(), opponent.getHealth());
        } else if (!player.isAlive() && !opponent.isAlive()) {
            // Ничья — считаем поражением игрока
            gameOver = true;
            if (listener != null) listener.onGameOver(false, player.getHealth(), opponent.getHealth());
        }
    }

    // Геттеры для доступа к игрокам
    public Player getPlayer() {
        return player;
    }

    public Player getOpponent() {
        return opponent;
    }

    public void resetGame() {
        gameOver = false;
        isPlayerTurn = true;
        if (player != null) player.resetForNewGame();
        if (opponent != null) opponent.resetForNewGame();
    }
}
