package com.example.interview.interview.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 下行消息结构：{"type":"...","payload":{...},"ts":...}（计划 §5.6）。
 */
@Data
@AllArgsConstructor
public class WsMessage {

    private WsMessageType type;
    private Map<String, Object> payload;
    private long ts;

    public static WsMessage of(WsMessageType type, Map<String, Object> payload) {
        return new WsMessage(type, payload, System.currentTimeMillis());
    }
}
