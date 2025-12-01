module com.example.dungeon_mayhem {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires static lombok;

    // FXMLLoader должен иметь доступ к контроллерам через reflection
    opens com.example.dungeon.ui to javafx.fxml;

    // Если в FXML используются классы из network (редко, но вдруг)
    opens com.example.dungeon.network to javafx.fxml;

    // Экспортируем публичные API пакеты (если нужно)
    exports com.example.dungeon;
    exports com.example.dungeon.ui;
    exports com.example.dungeon.network;
}
