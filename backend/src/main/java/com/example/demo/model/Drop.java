package com.example.demo.model;

import java.util.UUID;

/**
 * Power-Up drop que aparece cuando muere un zombie.
 * Inspirado en el sistema de drops de Call of Duty: Black Ops 1 Zombies.
 */
public class Drop {

    public enum TipoDrop {
        // ─── Drops instantáneos ───────────────────────────────────────────────
        NUKE,           // Mata a todos los zombies del mapa
        MAX_AMMO,       // Recarga armas + 300 pts a todos
        CARPENTER,      // Cura a todos los jugadores al máximo

        // ─── Drops temporales ─────────────────────────────────────────────────
        INSTA_KILL,     // 20s  – disparos matan de un golpe
        DOUBLE_POINTS,  // 30s  – kills dan x2 puntos
        DEATH_MACHINE,  // 30s  – daño x4 (minigun mode)
        FIRE_SALE;      // 30s  – Caja Mágica cuesta sólo 10 pts

        /** Duración del EFECTO una vez recogido (0 = instantáneo). */
        public long duracionEfectoMs() {
            switch (this) {
                case INSTA_KILL:    return 20_000L;
                case DOUBLE_POINTS: return 30_000L;
                case DEATH_MACHINE: return 30_000L;
                case FIRE_SALE:     return 30_000L;
                default:            return 0L;
            }
        }

        /** True si el efecto persiste en el tiempo. */
        public boolean esTemporal() {
            return duracionEfectoMs() > 0;
        }
    }

    // ─── Probabilidades de aparición (weighted pool) ──────────────────────────
    // 30 entradas totales → cada entrada = 3,33 %
    // NUKE×3=10%  MAX_AMMO×3=10%  CARPENTER×3=10%
    // INSTA_KILL×5=17%  DOUBLE_POINTS×8=27%
    // DEATH_MACHINE×4=13%  FIRE_SALE×4=13%
    private static final TipoDrop[] POOL = {
        TipoDrop.NUKE,           TipoDrop.NUKE,           TipoDrop.NUKE,           // 10 %
        TipoDrop.MAX_AMMO,       TipoDrop.MAX_AMMO,       TipoDrop.MAX_AMMO,       // 10 %
        TipoDrop.CARPENTER,      TipoDrop.CARPENTER,      TipoDrop.CARPENTER,      // 10 %
        TipoDrop.INSTA_KILL,     TipoDrop.INSTA_KILL,     TipoDrop.INSTA_KILL,     TipoDrop.INSTA_KILL, TipoDrop.INSTA_KILL, // 17 %
        TipoDrop.DOUBLE_POINTS,  TipoDrop.DOUBLE_POINTS,  TipoDrop.DOUBLE_POINTS,  TipoDrop.DOUBLE_POINTS,
        TipoDrop.DOUBLE_POINTS,  TipoDrop.DOUBLE_POINTS,  TipoDrop.DOUBLE_POINTS,  TipoDrop.DOUBLE_POINTS,           // 27 %
        TipoDrop.DEATH_MACHINE,  TipoDrop.DEATH_MACHINE,  TipoDrop.DEATH_MACHINE,  TipoDrop.DEATH_MACHINE,           // 13 %
        TipoDrop.FIRE_SALE,      TipoDrop.FIRE_SALE,      TipoDrop.FIRE_SALE,      TipoDrop.FIRE_SALE                // 13 %
    };

    // ─── Campos ───────────────────────────────────────────────────────────────
    private final String   id;
    private final TipoDrop tipo;
    private final Position pos;
    private final long     creadoEnMs;

    /** Tiempo que el drop permanece en el suelo antes de desvanecerse (30 s). */
    private static final long VIDA_SUELO_MS = 30_000L;

    public Drop(TipoDrop tipo, Position pos) {
        this.id         = UUID.randomUUID().toString().substring(0, 8);
        this.tipo       = tipo;
        this.pos        = pos;
        this.creadoEnMs = System.currentTimeMillis();
    }

    // ─── Factory ──────────────────────────────────────────────────────────────
    public static Drop aleatorio(Position pos) {
        TipoDrop t = POOL[new java.util.Random().nextInt(POOL.length)];
        return new Drop(t, pos);
    }

    // ─── Getters ──────────────────────────────────────────────────────────────
    public String   getId()         { return id; }
    public TipoDrop getTipo()       { return tipo; }
    public Position getPos()        { return pos; }
    public long     getCreadoEnMs() { return creadoEnMs; }

    /** Milisegundos de vida restantes en el suelo (para el frontend). */
    public long msRestantesEnSuelo(long ahoraMs) {
        return Math.max(0, VIDA_SUELO_MS - (ahoraMs - creadoEnMs));
    }

    public boolean expirado(long ahoraMs) {
        return ahoraMs - creadoEnMs >= VIDA_SUELO_MS;
    }
}
