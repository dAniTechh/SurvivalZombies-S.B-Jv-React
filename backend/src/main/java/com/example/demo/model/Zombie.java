package com.example.demo.model;

import java.util.UUID;

public class Zombie {

    public enum TipoZombie {
        CAMINANTE, CORREDOR
    }
    public double getVelocidad() { 
        return velocidad; 
    }

    private final String id;
    private final TipoZombie tipo; // Para que el frontend sepa de qué color pintarlo
    private Position pos;
    private double velocidad;
    private int saludMax;
    private int saludActual;
    private boolean vivo = true;

    private long ultimoAtaque = 0;
    public static final long COOLDOWN_ATAQUE_MS = 700;
    private String targetPlayerId;

    public Zombie(Position spawnPos, double velocidad, int salud, TipoZombie tipo) {
        this.id          = UUID.randomUUID().toString().substring(0, 8);
        this.pos         = spawnPos;
        this.velocidad   = velocidad;
        this.saludMax    = salud;
        this.saludActual = salud;
        this.tipo        = tipo;
    }

    public void moverHacia(double targetX, double targetY, double deltaTime) {
        double dx = targetX - pos.x;
        double dy = targetY - pos.y;
        double distancia = Math.sqrt(dx * dx + dy * dy);

        if (distancia < 1.0) return;

        double paso = velocidad * deltaTime;
        pos.x += (dx / distancia) * paso;
        pos.y += (dy / distancia) * paso;
    }

    public boolean puedeAtacar() { return System.currentTimeMillis() - ultimoAtaque >= COOLDOWN_ATAQUE_MS; }
    public void registrarAtaque() { this.ultimoAtaque = System.currentTimeMillis(); }
    public void recibirDaño(int daño) {
        this.saludActual = Math.max(0, this.saludActual - daño);
        if (this.saludActual == 0) this.vivo = false;
    }

    // Getters
    public String getId() { return id; }
    public Position getPos() { return pos; }
    public boolean isVivo() { return vivo; }
    public TipoZombie getTipo() { return tipo; }
    public String getTargetPlayerId() { return targetPlayerId; }
    public void setTargetPlayerId(String t) { this.targetPlayerId = t; }
    public int getSaludActual() { return saludActual; }
    public int getSaludMax() { return saludMax; }
}