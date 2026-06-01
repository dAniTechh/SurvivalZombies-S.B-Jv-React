package com.example.demo.model;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Representa una puerta desbloqueable ("door" tipo BO3).
 * - Si abierta=false actúa como muro sólido para jugadores y zombies.
 * - Si abierta=true se ignora como obstáculo.
 * - Usa AtomicBoolean para ser Thread-Safe en multijugador.
 */
public class Puerta {

    private final String id;
    private final double x;
    private final double y;
    private final double w;
    private final double h;
    private final int coste;
    private final String nombreZonaDestino;

    // 🔥 Cambio clave: Thread-Safe
    private final AtomicBoolean abierta;

    public Puerta(String id,
                  double x,
                  double y,
                  double w,
                  double h,
                  int coste,
                  String nombreZonaDestino,
                  boolean abierta) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.coste = coste;
        this.nombreZonaDestino = nombreZonaDestino;
        // Inicializamos el AtomicBoolean
        this.abierta = new AtomicBoolean(abierta);
    }

    public String getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getW() {
        return w;
    }

    public double getH() {
        return h;
    }

    public int getCoste() {
        return coste;
    }

    public String getNombreZonaDestino() {
        return nombreZonaDestino;
    }

    // Los getters y setters envuelven los métodos get() y set() del AtomicBoolean
    // para que no tengas que cambiar el resto de tu código
    public boolean isAbierta() {
        return abierta.get();
    }

    public void setAbierta(boolean estadoAbierta) {
        this.abierta.set(estadoAbierta);
    }
}