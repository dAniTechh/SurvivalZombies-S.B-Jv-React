package com.example.demo.engine;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class MensajeBroker {

    private final SimpMessagingTemplate template;

    public MensajeBroker(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void enviarATodos(String destino, Object payload) {
        template.convertAndSend(destino, payload);
    }

    public void enviarAJugador(String sessionId, String destino, Object payload) {
        template.convertAndSendToUser(sessionId, destino, payload);
    }
}