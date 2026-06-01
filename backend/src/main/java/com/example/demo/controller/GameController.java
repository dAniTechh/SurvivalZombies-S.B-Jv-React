package com.example.demo.controller;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.example.demo.engine.GameEngine;

@Controller
public class GameController {

    private final GameEngine engine;
    private final SimpMessagingTemplate messagingTemplate;

    public GameController(GameEngine engine, SimpMessagingTemplate messagingTemplate) {
        this.engine = engine;
        this.messagingTemplate = messagingTemplate;
    }


    @EventListener
    public void onDisconnect(SessionDisconnectEvent e) {
        engine.desconectarJugador(e.getSessionId());
    }


    @MessageMapping("/switchWeapon")
    public void switchWeapon(org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        engine.procesarCambioArma(sessionId);
    }
   
@MessageMapping("/join")
public void join(SimpMessageHeaderAccessor h, Map<String, String> body) {
    String sessionId = h.getSessionId();
    String nombre = body.getOrDefault("nombre", "Anónimo");
    // Extraemos la skin enviada desde el frontend, o usamos una por defecto
    String skin = body.getOrDefault("skin", "jugador.png");

    // Registra el jugador enviando la skin extraída
    engine.registrarJugador(sessionId, nombre, skin);

    // 🔥 FIX MULTIJUGADOR: Forzamos las cabeceras de sesión nativas
    SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
    headerAccessor.setSessionId(sessionId);
    headerAccessor.setLeaveMutable(true);

    messagingTemplate.convertAndSendToUser(
        sessionId,
        "/queue/init",
        Map.of("sessionId", sessionId),
        headerAccessor.getMessageHeaders()
    );
}


    @MessageMapping("/input")
    public void input(SimpMessageHeaderAccessor h, Map<String, Boolean> keys) {
        engine.procesarInput(h.getSessionId(), new boolean[]{
            keys.getOrDefault("w", false),
            keys.getOrDefault("a", false),
            keys.getOrDefault("s", false),
            keys.getOrDefault("d", false)
        });
    }

    @MessageMapping("/sprint")
    public void sprint(SimpMessageHeaderAccessor h, Map<String, Boolean> body) {
        engine.procesarSprint(
            h.getSessionId(),
            body.getOrDefault("activo", false)
        );
    }

    @MessageMapping("/shoot")
    public void shoot(SimpMessageHeaderAccessor h, Map<String, Object> body) {
        String sessionId = h.getSessionId();
        String zombieId = (String) body.get("zombieId");
        boolean headshot = (Boolean) body.getOrDefault("headshot", false);

        System.out.printf(">>> [/shoot] session=%s zombieId=%s headshot=%s%n",
                sessionId, zombieId, headshot);

        engine.procesarDisparo(
            sessionId,
            zombieId,
            headshot
        );
    }


    @MessageMapping("/interact")
    public void interact(SimpMessageHeaderAccessor h, Map<String, Object> body) {
        engine.procesarInteraccion(h.getSessionId());
    }

    @MessageMapping("/restart")
    public void restart(SimpMessageHeaderAccessor h) {
        engine.reiniciarPartidaManual();
    }
}