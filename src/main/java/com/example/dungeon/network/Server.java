package com.example.dungeon.network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import com.example.dungeon.game.*;

public class Server implements Runnable {
    private int port;
    private boolean running;
    private ServerSocket serverSocket;
    private List<ClientHandler> clients;
    private GameSession gameSession;
    private ExecutorService pool;

    // Статический экземпляр для доступа из контроллеров
    private static Server instance;

    public Server(int port) throws IOException {
        this.port = port;
        this.running = true;
        this.serverSocket = new ServerSocket(port);
        this.clients = new ArrayList<>();
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
                System.out.println("🔗 Новое подключение: " + clientSocket.getInetAddress());

                if (clients.size() < 2) {
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this, clients.size() + 1);
                    clients.add(clientHandler);
                    pool.execute(clientHandler);

                    // Определяем роль игрока
                    String playerRole = (clients.size() == 1) ? "Игрок 1 (Создатель)" : "Игрок 2 (Присоединившийся)";
                    clientHandler.setPlayerName(playerRole);

                    // Уведомляем о подключении
                    broadcast(new NetworkMessage(MessageType.PLAYER_JOIN,
                        playerRole + " подключился к игре"), null);

                    if (clients.size() == 2) {
                        System.out.println("🎲 Оба игрока подключены! Начинаем игру...");
                        startGame();
                    }
                } else {
                    System.out.println("❌ Игра уже заполнена, отказ в подключении");
                    clientSocket.close();
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Ошибка сервера: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void startGame() {
        // Инициализируем игровую сессию
        gameSession.initializeGame();

        // Отправляем начальное состояние каждому игроку
        for (int i = 0; i < clients.size(); i++) {
            ClientHandler client = clients.get(i);
            Player player = (i == 0) ? gameSession.getPlayer1() : gameSession.getPlayer2();
            Player opponent = (i == 0) ? gameSession.getPlayer2() : gameSession.getPlayer1();

            // Создаем персональное состояние игры для каждого клиента
            GameState playerGameState = new GameState(
                player,
                opponent,
                i == 0, // Первый игрок ходит первым
                i == 0 ? "Ваш ход! Выберите карту" : "Ход противника. Ожидайте..."
            );

            // Отправляем начальные карты
            List<Card> initialHand = generateInitialHand();
            player.getHand().addAll(initialHand);

            // Отправляем состояние клиенту
            client.sendMessage(new NetworkMessage(MessageType.GAME_UPDATE, playerGameState));
            client.sendMessage(new NetworkMessage(MessageType.CHAT_MESSAGE,
                "🎮 Игра началась! Вы " + (i == 0 ? "игрок 1" : "игрок 2")));
        }

        // Уведомляем всех о начале игры
        broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE,
            "⚔ БИТВА НАЧАЛАСЬ! ⚔"), null);
    }

    private List<Card> generateInitialHand() {
        List<Card> hand = new ArrayList<>();
        Random random = new Random();
        CardType[] types = CardType.values();

        String[][] cardNames = {
            {"Огненный шар", "Ледяная стрела", "Молния", "Кислотный плевок", "Удар тени"},
            {"Железный щит", "Магический барьер", "Каменная кожа", "Энергетическое поле", "Кристальная защита"},
            {"Целебное зелье", "Эликсир жизни", "Бальзам здоровья", "Восстанавливающий нектар", "Божественное исцеление"}
        };

        // Даем по 3 карты каждого типа для баланса
        for (int i = 0; i < 3; i++) {
            CardType type = types[i];
            String name = cardNames[i][random.nextInt(cardNames[i].length)];
            hand.add(new Card(type, name));
        }

        // Добавляем еще 2 случайные карты
        for (int i = 0; i < 2; i++) {
            CardType randomType = types[random.nextInt(types.length)];
            int typeIndex = randomType.ordinal();
            String name = cardNames[typeIndex][random.nextInt(cardNames[typeIndex].length)];
            hand.add(new Card(randomType, name));
        }

        return hand;
    }

    public synchronized void handleCardPlayed(Card card, ClientHandler player) {
        System.out.println("🎴 Игрок " + player.getPlayerId() + " сыграл карту: " + card.getName());

        // Применяем эффект карты в игровой сессии
        String result = gameSession.playCard(card, player.getPlayerId());

        // Отправляем результат всем игрокам
        broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE, result), null);

        // Обновляем состояние у всех игроков
        for (int i = 0; i < clients.size(); i++) {
            ClientHandler client = clients.get(i);
            Player currentPlayer = (i == 0) ? gameSession.getPlayer1() : gameSession.getPlayer2();
            Player opponent = (i == 0) ? gameSession.getPlayer2() : gameSession.getPlayer1();

            // Проверяем, чей сейчас ход
            boolean isPlayerTurn = (gameSession.isPlayer1Turn() && i == 0) ||
                (!gameSession.isPlayer1Turn() && i == 1);

            GameState updatedState = new GameState(
                currentPlayer,
                opponent,
                isPlayerTurn,
                isPlayerTurn ? "Ваш ход! Выберите карту" : "Ход противника. Ожидайте..."
            );

            client.sendMessage(new NetworkMessage(MessageType.GAME_UPDATE, updatedState));

            // Если ход клиента, даем ему новую карту
            if (isPlayerTurn) {
                Card newCard = drawRandomCard();
                currentPlayer.getHand().add(newCard);
                client.sendMessage(new NetworkMessage(MessageType.CHAT_MESSAGE,
                    "🎴 Вы получили новую карту: " + newCard.getName()));
            }
        }

        // Проверяем условия победы
        String victoryMessage = gameSession.checkVictory();
        if (victoryMessage != null) {
            broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE, victoryMessage), null);
            broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE,
                "🔄 Игра завершена. Создайте новую игру для повторной битвы."), null);
        }
    }

    private Card drawRandomCard() {
        Random random = new Random();
        CardType[] types = CardType.values();
        CardType randomType = types[random.nextInt(types.length)];

        String[][] cardNames = {
            {"Огненный шар", "Ледяная стрела", "Молния", "Удар кинжалом", "Ядовитый укус"},
            {"Железный щит", "Магический барьер", "Доспех дракона", "Эгида защиты", "Священный щит"},
            {"Целебное зелье", "Эликсир жизни", "Нектар здоровья", "Бальзам восстановления", "Настойка выносливости"}
        };

        String name = cardNames[randomType.ordinal()][random.nextInt(cardNames[randomType.ordinal()].length)];
        return new Card(randomType, name);
    }

    public synchronized void broadcast(NetworkMessage message, ClientHandler exclude) {
        for (ClientHandler client : clients) {
            if (client != exclude) {
                client.sendMessage(message);
            }
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("👋 Клиент отключен. Осталось игроков: " + clients.size());

        if (clients.size() < 2) {
            broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE,
                "⚠ Один из игроков покинул игру. Игра приостановлена."), null);
        }
    }

    public void shutdown() {
        running = false;
        pool.shutdown();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler client : clients) {
                client.disconnect();
            }
        } catch (IOException e) {
            System.err.println("❌ Ошибка при закрытии сервера: " + e.getMessage());
        }
    }

    // Внутренний класс для обработки игровой сессии
    private class GameSession {
        private Player player1;
        private Player player2;
        private boolean isPlayer1Turn;
        private Random random;

        public GameSession() {
            this.random = new Random();
        }

        public void initializeGame() {
            player1 = new Player("Игрок 1");
            player2 = new Player("Игрок 2");
            isPlayer1Turn = true; // Первым ходит создатель игры

            System.out.println("🔄 Игровая сессия инициализирована");
        }

        public String playCard(Card card, int playerId) {
            Player currentPlayer = (playerId == 1) ? player1 : player2;
            Player opponent = (playerId == 1) ? player2 : player1;

            // Проверяем, правильный ли игрок ходит
            if ((playerId == 1 && !isPlayer1Turn) || (playerId == 2 && isPlayer1Turn)) {
                return "⚠ Не ваш ход!";
            }

            // Удаляем карту из руки
            boolean cardRemoved = currentPlayer.getHand().removeIf(c ->
                c.getName().equals(card.getName()) && c.getType() == card.getType());

            if (!cardRemoved) {
                return "⚠ Карта не найдена в руке!";
            }

            // Применяем эффект карты
            String actionMessage = applyCardEffect(card, currentPlayer, opponent);

            // Меняем ход
            isPlayer1Turn = !isPlayer1Turn;

            return actionMessage;
        }

        private String applyCardEffect(Card card, Player currentPlayer, Player opponent) {
            StringBuilder message = new StringBuilder();

            switch (card.getType()) {
                case ATTACK:
                    int damage = 2;
                    if (opponent.getShield() > 0) {
                        int remainingShield = opponent.getShield() - damage;
                        if (remainingShield >= 0) {
                            opponent.setShield(remainingShield);
                            message.append("⚔ ").append(currentPlayer.getName())
                                .append(" атакует! Щит противника поглотил ").append(damage).append(" урона.");
                        } else {
                            opponent.setShield(0);
                            opponent.setHealth(opponent.getHealth() + remainingShield); // remainingShield отрицательный
                            message.append("⚔ ").append(currentPlayer.getName())
                                .append(" атакует! Пробит щит и нанесено ").append(-remainingShield).append(" урона!");
                        }
                    } else {
                        opponent.setHealth(opponent.getHealth() - damage);
                        message.append("⚔ ").append(currentPlayer.getName())
                            .append(" атакует! Нанесено ").append(damage).append(" урона!");
                    }
                    break;

                case DEFENSE:
                    currentPlayer.setShield(currentPlayer.getShield() + 1);
                    message.append("🛡 ").append(currentPlayer.getName())
                        .append(" укрепляет защиту! +1 щит.");
                    break;

                case HEAL:
                    int newHealth = Math.min(10, currentPlayer.getHealth() + 1);
                    int healed = newHealth - currentPlayer.getHealth();
                    currentPlayer.setHealth(newHealth);
                    message.append("❤ ").append(currentPlayer.getName())
                        .append(" лечится! Восстановлено ").append(healed).append(" HP.");
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
        public boolean isPlayer1Turn() { return isPlayer1Turn; }
    }

    // Внутренний класс для обработки клиентов
    private class ClientHandler implements Runnable {
        private Socket socket;
        private Server server;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private int playerId;
        private String playerName;
        private boolean connected;

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
                in = new ObjectInputStream(socket.getInputStream());

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
                        break; // Клиент отключился
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("❌ Ошибка обработки клиента " + playerId + ": " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private void handleMessage(NetworkMessage message) {
            switch (message.getType()) {
                case CARD_PLAYED:
                    Card card = (Card) message.getData();
                    server.handleCardPlayed(card, this);
                    break;

                case CHAT_MESSAGE:
                    String chatMessage = (String) message.getData();
                    // Добавляем имя отправителя
                    String formattedMessage = playerName + ": " + chatMessage;
                    server.broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE, formattedMessage), this);
                    break;

                default:
                    System.out.println("❓ Неизвестный тип сообщения от игрока " + playerId + ": " + message.getType());
            }
        }

        public synchronized void sendMessage(NetworkMessage message) {
            if (!connected || out == null) return;

            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                System.err.println("❌ Ошибка отправки сообщения игроку " + playerId + ": " + e.getMessage());
                disconnect();
            }
        }

        private void disconnect() {
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

        public int getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String name) { this.playerName = name; }
    }
}
