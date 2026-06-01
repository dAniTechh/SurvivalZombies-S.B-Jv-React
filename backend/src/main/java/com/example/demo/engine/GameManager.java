package com.example.demo.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.example.demo.model.Position;
import com.example.demo.model.Zombie;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  CURVA DE DIFICULTAD EXPONENCIAL
 * ══════════════════════════════════════════════════════════════════════════════
 *
 *  CANTIDAD  N(r) = floor( 6 × 1.18^(r-1) ) + jugadores × 3
 *            R1 → 9+3J  |  R5 → 13+3J  |  R10 → 31+3J  |  R20 → 173+3J
 *
 *  SALUD     HP(r) = 100 × 1.20^(r-1)
 *            R1 → 100  |  R5 → 207  |  R10 → 519  |  R20 → 3834
 *
 *  VELOCIDAD base caminante: 75 + 4×(r-1), tope 200 px/s
 *            runners aparecen desde ronda 3, +8%/ronda, tope 85%
 *            velocidad runner: 185 + 8.5×(r-1), tope 340 px/s
 *
 *  COOLDOWN  ataque zombie: 800ms × 0.95^(r-1), mínimo 150ms
 *
 *  SPAWN     lote inicial = min(8+ronda, total)
 *            desde ronda 8: 25% chance de spawn interior
 *
 *  Thread-Safe: todos los métodos públicos son synchronized.
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Component
public class GameManager {

    // ── Estado de la partida ──────────────────────────────────────────────────
    private int     rondaActual       = 0;
    private int     zombiesRestantes  = 0;
    private int     zombiesSpawneados = 0;
    private int     totalZombiesRonda = 0;
    private boolean rondaEnCurso      = false;

    // ── Parámetros base de dificultad ─────────────────────────────────────────
    private static final double CANTIDAD_BASE       = 6.0;   // zombies ronda 1 sin jugadores
    private static final double CANTIDAD_EXP        = 1.18;  // ×1.18 cada ronda
    private static final int    CANTIDAD_MAX        = 250;   // tope absoluto

    private static final int    SALUD_BASE          = 100;   // HP ronda 1
    private static final double SALUD_EXP           = 1.20;  // ×1.20 cada ronda
    private static final int    SALUD_MAX           = 50_000;

    private static final double VEL_CAMINANTE_BASE  = 75.0;  // px/s ronda 1
    private static final double VEL_CAMINANTE_INC   = 4.0;   // +px/s por ronda
    private static final double VEL_CAMINANTE_MAX   = 200.0;
    private static final double VEL_RUNNER_BASE     = 185.0; // px/s ronda 1
    private static final double VEL_RUNNER_INC      = 8.5;   // +px/s por ronda
    private static final double VEL_RUNNER_MAX      = 340.0;

    private static final int    RUNNER_RONDA_INICIO = 3;
    private static final double RUNNER_PROB_INC     = 0.06;  // +6% prob runner por ronda
    private static final double RUNNER_PROB_MAX     = 0.85;

    private static final long   COOLDOWN_BASE_MS    = 800L;
    private static final double COOLDOWN_DEC        = 0.05;  // -5% por ronda
    private static final long   COOLDOWN_MIN_MS     = 150L;

    // ── Puntos de spawn ───────────────────────────────────────────────────────
    private static final Position[] SPAWNS_BORDE = {
        new Position(20,   20),  new Position(1200,  20),  new Position(2380,  20),
        new Position(20,  900),  new Position(2380,  900),
        new Position(20, 1780),  new Position(1200,1780),  new Position(2380,1780),
    };
  private static final Position[] SPAWNS_INTERIOR = {
        new Position(300,  250),   // Z0 Laboratorio: Esquina superior izquierda (esquiva la mesa central de 330,320)
        new Position(1200, 450),   // Z1 Pasillos: Pasillo libre inferior (esquiva el muro largo de 1100,200)
        new Position(2000, 450),   // Z2 Armería: Zona sur despejada (esquiva el mostrador de 1850,180)
        new Position(250,  800),   // Z3 Enfermería: Lane izquierdo libre (esquiva las camillas de la derecha)
        new Position(1050, 750),   // Z4 Patio: Pasillo superior izquierdo (¡SACA EL SPAWN del pilar central de 1200,900!)
        new Position(1900, 900),   // Z5 Búnker: Zona central-izquierda limpia (esquiva la pared de la derecha)
        new Position(950,  1350)   // Z6 Generadores: Hueco superior entre los primeros generadores
    };

    private final Random rng = new Random();

    // ── API pública ───────────────────────────────────────────────────────────

    /** Inicia la siguiente ronda. Devuelve el lote inicial de zombies. */
    public synchronized List<Zombie> iniciarSiguienteRonda(int jugadoresConectados) {
        rondaActual++;
        rondaEnCurso      = true;
        zombiesSpawneados = 0;

        totalZombiesRonda = Math.min(CANTIDAD_MAX,
            (int)(CANTIDAD_BASE * Math.pow(CANTIDAD_EXP, rondaActual - 1))
            + jugadoresConectados * 3);
        zombiesRestantes = totalZombiesRonda;

        int loteInicial = Math.min(8 + rondaActual, totalZombiesRonda);

        System.out.printf(
            "[GAME] RONDA %d | Zombies: %d | HP: %d | Cooldown: %dms | Runners desde R%d (%.0f%%) | Jugadores: %d%n",
            rondaActual, totalZombiesRonda, calcularSalud(), calcularCooldownAtaque(),
            RUNNER_RONDA_INICIO, calcularProbRunner() * 100, jugadoresConectados);

        return spawnLote(loteInicial);
    }

    /** Notifica muerte de zombie; devuelve uno nuevo si quedan por spawnear. */
    public synchronized Zombie zombieMuerto() {
        // ── GUARDIA DEFENSIVA ANTI-RACE CONDITIONS ──
        if (zombiesRestantes <= 0) {
            return null; 
        }

        zombiesRestantes--;
        
        if (zombiesRestantes == 0) {
            rondaEnCurso = false;
            System.out.printf("[GAME] Ronda %d completada.%n", rondaActual);
            return null;
        }
        
        if (zombiesSpawneados < totalZombiesRonda) {
            return crearZombie();
        }
        
        return null;
    }

    

    public synchronized boolean isRondaTerminada()    { return !rondaEnCurso; }
    public synchronized int     getRondaActual()      { return rondaActual; }
    public synchronized int     getZombiesRestantes() { return zombiesRestantes; }

    /**
     * Drain instantáneo de la ronda (NUKE).
     * Pone zombiesRestantes a 0 y marca la ronda como terminada
     * sin intentar spawnear nuevos zombies.
     * Devuelve los puntos base por zombie pendiente (para que el engine los sume).
     */
    public synchronized int nukearRonda() {
        int pendientes = zombiesRestantes;
        zombiesRestantes  = 0;
        zombiesSpawneados = totalZombiesRonda; // evitar spawns futuros
        rondaEnCurso      = false;
        System.out.printf("[NUKE] Ronda %d terminada. %d zombies drenados.%n", rondaActual, pendientes);
        return pendientes;
    }

    /** Cooldown de ataque de esta ronda (para uso externo si se necesita). */
    public synchronized long getCooldownAtaqueMs() { return calcularCooldownAtaque(); }

    /**
     * Resetea el estado de la partida para empezar “como un juego normal”.
     * (Se llama cuando mueren todos los jugadores).
     */
    public synchronized void reiniciarPartida() {
        rondaActual = 0;
        zombiesRestantes = 0;
        zombiesSpawneados = 0;
        totalZombiesRonda = 0;
        rondaEnCurso = false;
    }


    // ── Fábrica de zombies ────────────────────────────────────────────────────

    private List<Zombie> spawnLote(int cantidad) {
        List<Zombie> lote = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) lote.add(crearZombie());
        return lote;
    }

  private Zombie crearZombie() {
        zombiesSpawneados++;
        
        // 1. Decidimos la naturaleza del zombie primero
        boolean esRunner = rng.nextDouble() < calcularProbRunner();
        Zombie.TipoZombie tipo = esRunner ? Zombie.TipoZombie.CORREDOR : Zombie.TipoZombie.CAMINANTE;
        
        // 2. Calculamos la velocidad basada EXACTAMENTE en esa decisión
        double vel   = calcularVelocidad(esRunner);
        int    salud = calcularSalud();
        
        return new Zombie(spawnAleatorio(), vel, salud, tipo);
    }

    // ── Fórmulas de escalado exponencial ─────────────────────────────────────

    /** HP(r) = 100 × 1.20^(r-1), tope 50 000. */
    private int calcularSalud() {
        return Math.min(SALUD_MAX,
            (int)(SALUD_BASE * Math.pow(SALUD_EXP, rondaActual - 1)));
    }

    /** Velocidad con jitter ±10%; elige caminante o runner según prob. */
   
    private double calcularVelocidad(boolean esRunner) {
        double jitter = 0.90 + rng.nextDouble() * 0.20;
        
        if (esRunner) {
            return Math.min(VEL_RUNNER_MAX,
                VEL_RUNNER_BASE + VEL_RUNNER_INC * (rondaActual - 1)) * jitter;
        } else {
            return Math.min(VEL_CAMINANTE_MAX,
                VEL_CAMINANTE_BASE + VEL_CAMINANTE_INC * (rondaActual - 1)) * jitter;
        }
    }

    /** Prob runner: 0 hasta R3, luego +8% por ronda, tope 85%. */
    private double calcularProbRunner() {
        if (rondaActual < RUNNER_RONDA_INICIO) return 0.0;
        return Math.min(RUNNER_PROB_MAX,
            (rondaActual - RUNNER_RONDA_INICIO + 1) * RUNNER_PROB_INC);
    }

    /** Cooldown ataque: 800ms × 0.95^(r-1), mínimo 150ms. */
    private long calcularCooldownAtaque() {
        return Math.max(COOLDOWN_MIN_MS,
            (long)(COOLDOWN_BASE_MS * Math.pow(1.0 - COOLDOWN_DEC, rondaActual - 1)));
    }

    /** Spawn en borde; desde R8, 25% chance de aparecer en interior. */
  private Position spawnAleatorio() {
        Position[] pool = (rondaActual >= 8 && rng.nextDouble() < 0.25)
                          ? SPAWNS_INTERIOR : SPAWNS_BORDE;
        Position base = pool[rng.nextInt(pool.length)];
        
        // Un jitter un pelín más generoso (±40px) para que se dispersen mejor en las zonas abiertas
        return new Position(
            base.x + (rng.nextDouble() - 0.5) * 70,
            base.y + (rng.nextDouble() - 0.5) * 70
        );
    }
}
