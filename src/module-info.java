module com.example.dungeon {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.example.dungeon to javafx.fxml;
    opens com.example.dungeon.ui to javafx.fxml;
    opens com.example.dungeon.network to javafx.fxml;
    opens com.example.dungeon.game to javafx.fxml;

    exports com.example.dungeon;
    exports com.example.dungeon.ui;
    exports com.example.dungeon.network;
    exports com.example.dungeon.game;
}
