package com.example.dungeon.network;

import java.io.Serializable;

public class NetworkMessage implements Serializable {
    private MessageType type;
    private Object data;

    public NetworkMessage(MessageType type, Object data) {
        this.type = type;
        this.data = data;
    }

    public MessageType getType() { return type; }
    public Object getData() { return data; }
    public void setType(MessageType type) { this.type = type; }
    public void setData(Object data) { this.data = data; }
}
