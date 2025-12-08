package com.example.dungeon.network;

import com.example.dungeon.game.*;
import lombok.Getter;
import lombok.Setter;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Server — улучшенная версия.
 *  - clients: synchronized list
 *  - клиент сообщает серверу о готовности streams -> сервер ждёт готовых клиентов перед startGame()
 *  - generateInitialHand безопасен и не вызывает OOB
 */
public class Server implements Runnable {
    private int port;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private List<ClientHandler> clients;
    private Set<ClientHandler> readyHandlers;
    private GameSession gameSession;
    private ExecutorService pool;

    // Статический экземпляр для доступа из контроллеров
    private static Server instance;

    public Server(int port) throws IOException {
        this.port = port;
        this.running = true;
        this.serverSocket = new ServerSocket(port);
        // синхронный список — безопаснее при многопоточном доступе
        this.clients = Collections.synchronizedList(new ArrayList<>());
        this.readyHandlers = ConcurrentHashMap.newKeySet();
        this.pool = Executors.newCachedThreadPool();
        this.gameSession = new GameSession();

        instance = this;
    }

    public static Server getInstance() {
        return instance;
    }

    @Override
    public void run() {
        System.out.println("🎮 Сервер запущен на порту " + port);

        try {
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("🔌 Новое подключение: " + clientSocket.getInetAddress());

                synchronized (clients) {
                    if (clients.size() < 2) {
                        ClientHandler clientHandler = new ClientHandler(clientSocket, this, clients.size() + 1);
                        clients.add(clientHandler);
                        pool.execute(clientHandler);

                        // Не назначаем роль и не стартуем игру здесь —
                        // дождёмся, пока клиент инициализирует streams и вызовет onClientReady().
                    } else {
                        System.out.println("❌ Игра уже заполнена, отказ в подключении");
                        clientSocket.close();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Ошибка сервера: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    /**
     * Вызывается из ClientHandler после успешной инициализации streams.
     */
    public void onClientReady(ClientHandler handler) {
        // Помечаем как готового
        readyHandlers.add(handler);

        // Назначаем понятное имя/роль (на основе позиции в списке clients)
        int idx;
        synchronized (clients) {
            idx = clients.indexOf(handler);
        }
        String playerRole = (idx == 0) ? "Игрок 1 (Создатель)" : "Игрок 2 (Присоединившийся)";
        handler.setPlayerName(playerRole);

        // Рассылаем всем, что этот игрок подключился
        broadcast(new NetworkMessage(MessageType.PLAYER_JOIN,
            playerRole + " подключился к игре"), null);

        System.out.println("▶ Клиент готов: " + playerRole + " (готовых " + readyHandlers.size() + ")");

        // Если все клиенты готовы и их ровно 2 — стартуем игру
        if (readyHandlers.size() == clients.size() && clients.size() == 2) {
            System.out.println("🎲 Все клиенты готовы — стартуем игру");
            startGame();
        }
    }

    private void startGame() {
        System.out.println("=== НАЧАЛО ИГРЫ ===");
        System.out.println("Клиентов: " + clients.size());

        gameSession.initializeGame();

        for (int i = 0; i < clients.size(); i++) {
            ClientHandler client = clients.get(i);
            Player player = (i == 0) ? gameSession.getPlayer1() : gameSession.getPlayer2();
            Player opponent = (i == 0) ? gameSession.getPlayer2() : gameSession.getPlayer1();

            // Четко определяем, чей сейчас ход - только первый игрок!
            boolean isPlayerTurn = (i == 0);

            System.out.println("Игрок " + (i+1) + ": " + player.getName() +
                " | Ход: " + (isPlayerTurn ? "ДА" : "НЕТ"));

            GameState playerGameState = new GameState(
                player,
                opponent,
                isPlayerTurn,
                isPlayerTurn ? "Ваш ход! Выберите карту" : "Ход противника. Ожидайте..."
            );

            // Отправляем начальные карты (без аварий)
            List<Card> initialHand = generateInitialHand();
            player.getHand().addAll(initialHand);

            // Отправляем состояние клиенту
            client.sendMessage(new NetworkMessage(MessageType.GAME_UPDATE, playerGameState));
            client.sendMessage(new NetworkMessage(MessageType.CHAT_MESSAGE,
                "🎮 Игра началась! Вы " + (i == 0 ? "игрок 1 (ходит первым)" : "игрок 2 (ожидайте)")));

            // Отправляем явное сообщение о ходе
            client.sendMessage(new NetworkMessage(MessageType.YOUR_TURN, isPlayerTurn));
        }

        broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE,
            "⚔ БИТВА НАЧАЛАСЬ! ⚔"), null);
        System.out.println("▶ Отправлены GAME_UPDATE и YOUR_TURN всем клиентам");
    }

    /**
     * Безопасное создание начальной руки — имя карты выбирается в зависимости от категории типа карты.
     */
    private List<Card> generateInitialHand() {
        List<Card> hand = new ArrayList<>();
        Random random = new Random();

        String[] attackNames = {
            "Огненный шар", "Ледяная стрела", "Молния", "Удар тени", "Колючий выпад",
            "Громовой удар", "Теневой выпад"
        };
        String[] defendNames = {
            "Железный щит", "Магический барьер", "Каменная кожа", "Энергетическое поле", "Кристальная защита"
        };
        String[] healNames = {
            "Целебное зелье", "Эликсир жизни", "Бальзам здоровья", "Восстанавливающий нектар", "Божественное исцеление"
        };

        CardType[] allTypes = CardType.values();

        // Делаем 5 карт в начальной руке
        for (int i = 0; i < 5; i++) {
            // выбираем случайный CardType из enum
            CardType t = allTypes[random.nextInt(allTypes.length)];
            String name = chooseNameForType(t, attackNames, defendNames, healNames, random);
            hand.add(new Card(t, name));
        }

        return hand;
    }

    private String chooseNameForType(CardType t, String[] attackNames, String[] defendNames, String[] healNames, Random random) {
        switch (t) {
            case ATTACK, DOUBLE_ATTACK, BACKSTAB, FIREBALL, BERSERK_RAGE -> {
                return attackNames[random.nextInt(attackNames.length)];
            }
            case DEFEND, SUPER_SHIELD -> {
                return defendNames[random.nextInt(defendNames.length)];
            }
            case HEAL, ULTIMATE_HEAL, HOLY_LIGHT -> {
                return healNames[random.nextInt(healNames.length)];
            }
            default -> {
                // На всякий случай — выбираем из атакующих
                return attackNames[random.nextInt(attackNames.length)];
            }
        }
    }

    public synchronized void handleCardPlayed(Card card, ClientHandler player) {
        System.out.println("🎴 Игрок " + player.getPlayerId() + " сыграл карту: " + card.getName());

        // Применяем эффект карты в игровой сессии
        String result = gameSession.playCard(card, player.getPlayerId());

        // Если ход был успешным, меняем текущего игрока
        if (!result.startsWith("⚠")) {
            gameSession.switchTurn();
        }

        // Отправляем результат всем игрокам
        broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE, result), null);

        // Обновляем состояние у всех игроков
        for (int i = 0; i < clients.size(); i++) {
            ClientHandler client = clients.get(i);
            Player currentPlayer = (i == 0) ? gameSession.getPlayer1() : gameSession.getPlayer2();
            Player opponent = (i == 0) ? gameSession.getPlayer2() : gameSession.getPlayer1();

            // Определяем, чей сейчас ход
            boolean isPlayerTurn = gameSession.isPlayerTurn(currentPlayer);

            GameState updatedState = new GameState(
                currentPlayer,
                opponent,
                isPlayerTurn,
                isPlayerTurn ? "🎯 ВАШ ХОД" : "⏳ ХОД ПРОТИВНИКА"
            );

            client.sendMessage(new NetworkMessage(MessageType.GAME_UPDATE, updatedState));

            // Отправляем явное указание о ходе
            client.sendMessage(new NetworkMessage(MessageType.YOUR_TURN, isPlayerTurn));

            // Если ход клиента, даем ему новую карту (только если ход успешный)
            if (isPlayerTurn && !result.startsWith("⚠") && gameSession.getCurrentPlayer().equals(currentPlayer)) {
                Card newCard = drawRandomCard();
                if (newCard != null) {
                    currentPlayer.getHand().add(newCard);
                    client.sendMessage(new NetworkMessage(MessageType.CHAT_MESSAGE,
                        "🎴 Вы получили новую карту: " + newCard.getName()));
                }
            }
        }

        // Проверяем условия победы
        String victoryMessage = gameSession.checkVictory();
        if (victoryMessage != null) {
            broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE, victoryMessage), null);
            broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE,
                "🔄 Игра завершена. Создайте новую игру для повторной битвы."), null);

            // Отправляем сообщение о завершении игры
            broadcast(new NetworkMessage(MessageType.GAME_OVER, victoryMessage), null);
        }
    }

    private Card drawRandomCard() {
        try {
            Random random = new Random();
            CardType[] types = CardType.values();
            CardType randomType = types[random.nextInt(types.length)];

            String[] attackNames = {
                "Огненный шар", "Ледяная стрела", "Молния", "Удар кинжалом", "Ядовитый укус"
            };
            String[] defendNames = {
                "Железный щит", "Магический барьер", "Доспех дракона", "Эгида защиты", "Священный щит"
            };
            String[] healNames = {
                "Целебное зелье", "Эликсир жизни", "Нектар здоровья", "Бальзам восстановления", "Настойка выносливости"
            };

            String name;
            switch (randomType) {
                case ATTACK, DOUBLE_ATTACK, BACKSTAB, FIREBALL, BERSERK_RAGE -> name = attackNames[random.nextInt(attackNames.length)];
                case DEFEND, SUPER_SHIELD -> name = defendNames[random.nextInt(defendNames.length)];
                case HEAL, ULTIMATE_HEAL, HOLY_LIGHT -> name = healNames[random.nextInt(healNames.length)];
                default -> name = attackNames[random.nextInt(attackNames.length)];
            }
            return new Card(randomType, name);
        } catch (Exception e) {
            System.err.println("❌ Ошибка при создании карты: " + e.getMessage());
            return null;
        }
    }

    public synchronized void broadcast(NetworkMessage message, ClientHandler exclude) {
        // Копия списка, чтобы итерация была безопасной
        ClientHandler[] snapshot;
        synchronized (clients) {
            snapshot = clients.toArray(new ClientHandler[0]);
        }
        for (ClientHandler client : snapshot) {
            if (client != exclude) {
                client.sendMessage(message);
            }
        }
    }

    public void removeClient(ClientHandler client) {
        synchronized (clients) {
            clients.remove(client);
        }
        readyHandlers.remove(client);

        System.out.println("👋 Клиент отключен. Осталось игроков: " + clients.size());

        if (clients.size() < 2) {
            broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE,
                "⚠ Один из игроков покинул игру. Игра приостановлена."), null);
        }
    }

    public void shutdown() {
        running = false;
        pool.shutdownNow();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            ClientHandler[] snapshot;
            synchronized (clients) {
                snapshot = clients.toArray(new ClientHandler[0]);
            }
            for (ClientHandler client : snapshot) {
                client.disconnect();
            }
        } catch (IOException e) {
            System.err.println("❌ Ошибка при закрытии сервера: " + e.getMessage());
        }
    }

    // Внутренний класс для обработки игровой сессии (без изменений)
    private class GameSession {
        private Player player1;
        private Player player2;
        private Player currentPlayer; // Текущий игрок
        private Random random;

        public GameSession() {
            this.random = new Random();
        }

        public void initializeGame() {
            player1 = new Player("Игрок 1");
            player2 = new Player("Игрок 2");
            currentPlayer = player1; // Первый игрок ходит первым
            System.out.println("🔄 Игровая сессия инициализирована. Первый ход: " + currentPlayer.getName());
        }

        public void switchTurn() {
            if (currentPlayer == player1) {
                currentPlayer = player2;
            } else {
                currentPlayer = player1;
            }
            System.out.println("🔄 Смена хода. Теперь ходит: " + currentPlayer.getName());
        }

        public boolean isPlayerTurn(Player player) {
            return currentPlayer != null && currentPlayer.equals(player);
        }

        public String playCard(Card card, int playerId) {
            Player currentPlayer = (playerId == 1) ? player1 : player2;
            Player opponent = (playerId == 1) ? player2 : player1;

            // Проверяем, правильный ли игрок ходит
            if (!isPlayerTurn(currentPlayer)) {
                System.out.println("⚠ Неправильный ход! Игрок " + playerId +
                    " пытался походить, но сейчас ход игрока " +
                    (this.currentPlayer == player1 ? "1" : "2"));
                return "⚠ Не ваш ход!";
            }

            // Проверяем, есть ли карта в руке
            Optional<Card> cardInHand = currentPlayer.getHand().stream()
                .filter(c -> c.getName().equals(card.getName()) && c.getType() == card.getType())
                .findFirst();

            if (cardInHand.isEmpty()) {
                return "⚠ Карта не найдена в руке!";
            }

            // Удаляем карту из руки
            currentPlayer.getHand().remove(cardInHand.get());

            // Применяем эффект карты
            String actionMessage = applyCardEffect(card, currentPlayer, opponent);

            return actionMessage;
        }

        private String applyCardEffect(Card card, Player currentPlayer, Player opponent) {
            StringBuilder message = new StringBuilder();

            // Применяем множители персонажа
            switch (card.getType()) {
                case ATTACK:
                case DOUBLE_ATTACK:
                case BACKSTAB:
                case FIREBALL:
                    int baseDamage = card.getValue();
                    int actualDamage = currentPlayer.calculateAttackDamage(baseDamage);

                    if (card.getType() == CardType.FIREBALL) {
                        opponent.takeDamage(actualDamage);
                        message.append("🔥 ").append(currentPlayer.getName())
                            .append(" (").append(currentPlayer.getCharacter().getName())
                            .append(") бросает огненный шар! Нанесено ")
                            .append(actualDamage).append(" урона.");
                    } else {
                        opponent.takeDamage(actualDamage);
                        message.append("⚔ ").append(currentPlayer.getName())
                            .append(" (").append(currentPlayer.getCharacter().getName())
                            .append(") атакует! Нанесено ")
                            .append(actualDamage).append(" урона.");

                        if (card.getType() == CardType.BACKSTAB) {
                            message.append(" (Игнорирует защиту!)");
                        }
                    }
                    break;

                case DEFEND:
                case SUPER_SHIELD:
                    int baseShield = card.getValue();
                    int actualShield = currentPlayer.calculateShield(baseShield);
                    currentPlayer.addShield(actualShield);

                    message.append("🛡 ").append(currentPlayer.getName())
                        .append(" (").append(currentPlayer.getCharacter().getName())
                        .append(") ставит щит! +").append(actualShield)
                        .append(" защиты.");
                    break;

                case HEAL:
                case ULTIMATE_HEAL:
                    int baseHeal = card.getValue();
                    int actualHeal = currentPlayer.calculateHealing(baseHeal);
                    currentPlayer.heal(actualHeal);

                    message.append("❤ ").append(currentPlayer.getName())
                        .append(" (").append(currentPlayer.getCharacter().getName())
                        .append(") лечится! +").append(actualHeal)
                        .append(" здоровья.");
                    break;

                case BERSERK_RAGE:
                    int rageDamage = currentPlayer.calculateAttackDamage(card.getValue());
                    opponent.takeDamage(rageDamage);
                    currentPlayer.takeDamage(2); // Сам получает урон

                    message.append("😡 ").append(currentPlayer.getName())
                        .append(" (").append(currentPlayer.getCharacter().getName())
                        .append(") впадает в ярость! Нанесено ")
                        .append(rageDamage).append(" урона, но сам получил 2 урона.");
                    break;

                case HOLY_LIGHT:
                    int holyHeal = currentPlayer.calculateHealing(card.getValue());
                    currentPlayer.heal(holyHeal);
                    currentPlayer.addShield(1);

                    message.append("✨ ").append(currentPlayer.getName())
                        .append(" (").append(currentPlayer.getCharacter().getName())
                        .append(") использует святой свет! +")
                        .append(holyHeal).append(" здоровья и +1 защита.");
                    break;
            }

            return message.toString();
        }

        public String checkVictory() {
            if (player1.getHealth() <= 0) {
                return "🏆 " + player2.getName() + " ПОБЕДИЛ! " + player1.getName() + " повержен!";
            } else if (player2.getHealth() <= 0) {
                return "🏆 " + player1.getName() + " ПОБЕДИЛ! " + player2.getName() + " повержен!";
            }
            return null;
        }

        public Player getPlayer1() { return player1; }
        public Player getPlayer2() { return player2; }
        public boolean isPlayer1Turn() { return currentPlayer == player1; }
        public Player getCurrentPlayer() { return currentPlayer; }
    }

    // Внутренний класс для обработки клиентов
    private class ClientHandler implements Runnable {
        private Socket socket;
        private Server server;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        @Getter
        private int playerId;
        @Getter
        @Setter
        private String playerName;
        private volatile boolean connected;

        public ClientHandler(Socket socket, Server server, int playerId) {
            this.socket = socket;
            this.server = server;
            this.playerId = playerId;
            this.connected = true;
            this.playerName = "Игрок " + playerId;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush(); // <- важно
                in = new ObjectInputStream(socket.getInputStream());

                System.out.println("🔗 ClientHandler[" + playerId + "]: streams initialized for " + socket.getInetAddress());

                // Сообщаем серверу, что этот handler готов (streams готовы)
                server.onClientReady(this);

                // Отправляем приветственное сообщение
                sendMessage(new NetworkMessage(MessageType.CHAT_MESSAGE,
                    "🎮 Добро пожаловать в Dungeon Mayhem! Вы " + playerName));

                if (playerId == 2) {
                    sendMessage(new NetworkMessage(MessageType.CHAT_MESSAGE,
                        "⏳ Ожидайте начала игры..."));
                }

                // Основной цикл обработки сообщений
                while (connected && !socket.isClosed()) {
                    try {
                        NetworkMessage message = (NetworkMessage) in.readObject();
                        handleMessage(message);
                    } catch (EOFException | SocketException e) {
                        System.out.println("📡 Клиент " + playerId + " отключился: " + e.getMessage());
                        break; // Клиент отключился
                    } catch (ClassNotFoundException e) {
                        System.err.println("❌ Ошибка десериализации от игрока " + playerId + ": " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("❌ Ошибка обработки клиента " + playerId + ": " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private void handleMessage(NetworkMessage message) {
            try {
                if (message == null || message.getType() == null) return;

                switch (message.getType()) {
                    case CARD_PLAYED -> {
                        Card card = (Card) message.getData();
                        server.handleCardPlayed(card, this);
                    }
                    case CHAT_MESSAGE -> {
                        String chatMessage = (String) message.getData();
                        // Форматируем сообщение
                        String formattedMessage = playerName + ": " + chatMessage;
                        // Отправляем всем, включая отправителя
                        server.broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE, formattedMessage), null);
                    }
                    default -> {
                        System.out.println("❓ Неизвестный тип сообщения от игрока " + playerId + ": " + message.getType());
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка обработки сообщения от игрока " + playerId + ": " + e.getMessage());
            }
        }

        public synchronized void sendMessage(NetworkMessage message) {
            if (!connected || out == null) return;
            try {
                out.writeObject(message);
                out.flush();
                out.reset();
                System.out.println("📤 Server -> player" + playerId + ": " + message.getType() +
                    (message.getData() != null ? " (данные отправлены)" : " (без данных)"));
            } catch (IOException e) {
                System.err.println("❌ Ошибка отправки сообщения игроку " + playerId + ": " + e.getMessage());
                disconnect();
            }
        }

        private void disconnect() {
            if (!connected) return;

            connected = false;
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
                server.removeClient(this);

                System.out.println("👋 Игрок " + playerId + " отключен");
            } catch (IOException e) {
                System.err.println("❌ Ошибка при отключении игрока " + playerId + ": " + e.getMessage());
            }
        }
    }
}
