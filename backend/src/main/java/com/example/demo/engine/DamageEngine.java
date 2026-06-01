package com.example.demo.engine;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.demo.model.Player;
import com.example.demo.model.Zombie;

/**
 * Toda la lógica de daño, estados y muerte está aquí.
 * El servidor es el único árbitro (arquitectura autoritativa).
 * Lógica protegida contra concurrencia (Thread-Safe).
 */
@Component
public class DamageEngine {

    // Daño base por mordisco (sin perks)
    private static final int DAÑO_MORDISCO = 50; // 2 golpes = muerte normal

    // ── Mordisco de zombie a jugador ──────────────────────────────────────────

    public void procesarMordisco(Zombie zombie, Player jugador,
                                  Map<String, Player> todosLosJugadores,
                                  MensajeBroker broker) {

        // Cooldown del zombie
        if (!zombie.puedeAtacar()) return;

        // 🔥 BLOQUEO: Evita que dos zombies muerdan al jugador en el mismo milisegundo
        // y causen un error de cálculo en la salud restante.
        synchronized (jugador) {
            // El jugador tiene su propio cooldown de recepción de daño
            if (!jugador.puedeSerGolpeado()) return;

            // ── Calcular daño real ─────────────────────────────────────────────────
            int daño = DAÑO_MORDISCO;

            // Perk TITAN (Jugger-Nog)
            if (jugador.hasPerk("TITAN")) daño = 20;

            // Registrar cooldowns en ambas partes
            zombie.registrarAtaque();
            jugador.registrarGolpe();

            // Aplicar daño
            int saludAnterior = jugador.getSalud();
            jugador.setSalud(saludAnterior - daño);

            System.out.printf("[DAÑO] %s recibe %d HP de zombie %s → salud: %d%n",
                    jugador.getNombre(), daño, zombie.getId(), jugador.getSalud());

            // Avisar a TODOS los clientes: efecto sangre en pantalla
            broker.enviarATodos("/topic/hits", Map.of(
                "playerId", jugador.getId(),
                "nombre",   jugador.getNombre(),
                "daño",     daño,
                "saludRestante", jugador.getSalud()
            ));

            // ── Comprobar si debe ser derribado ───────────────────────────────────
            if (jugador.getSalud() <= 0 && jugador.isVivo() && !jugador.isDerribado()) {
                derribarJugador(jugador, broker);
            }
        }
    }

    // ── Disparo de jugador a zombie ───────────────────────────────────────────

    public boolean procesarDisparo(Player tirador, Zombie objetivo,
                                    boolean esHeadshot, boolean esInstantKill,
                                    MensajeBroker broker) {
                                        
        // 🔥 BLOQUEO: Evita que dos jugadores reclamen la muerte del mismo zombie a la vez.
        // El primero que procesa el tiro letal se lleva los puntos.
        synchronized (objetivo) {
            if (!objetivo.isVivo()) return false;

            int daño = calcularDañoDisparo(tirador, esHeadshot, esInstantKill);
            objetivo.recibirDaño(daño);

            // Puntos por impacto (uso seguro gracias al AtomicInteger que pusimos antes)
            tirador.sumarPuntos(10);

            if (!objetivo.isVivo()) {
                // Puntos por eliminación (SOLO entra el que dio la bala que lo mató)
                int puntosBono = esHeadshot ? 100 : 60;
                tirador.sumarPuntos(puntosBono);

                broker.enviarATodos("/topic/kill", Map.of(
                    "zombieId",  objetivo.getId(),
                    "matadoPor", tirador.getNombre(),
                    "headshot",  esHeadshot,
                    "puntos",    puntosBono
                ));
                System.out.printf("[KILL] %s eliminó zombie %s (%s) +%d pts%n",
                        tirador.getNombre(), objetivo.getId(),
                        esHeadshot ? "HEADSHOT" : "normal", puntosBono);
                return true; // zombie muerto
            }
            return false;
        }
    }

    // ── Daño real del disparo (según arma y headshot) ────────────────

    private int calcularDañoDisparo(Player tirador, boolean headshot, boolean instantKill) {
        if (instantKill) return Integer.MAX_VALUE; // Drop "Baja Instantánea"

        // Obtener el arma que tiene el jugador (ej. SNIPER)
        Player.TipoArma arma = tirador.getArmaEquipada();

        // Daño dinámico de esa arma
        return headshot ? arma.danoHeadshot : arma.danoBase;
    }

    // ── Estado Derribado ──────────────────────────────────────────────────────

    private void derribarJugador(Player jugador, MensajeBroker broker) {
        jugador.derribar();
        System.out.printf("[DOWNED] %s ha caído. Desangrado en 15s...%n", jugador.getNombre());

        broker.enviarATodos("/topic/estado", Map.of(
            "evento",   "PLAYER_DOWNED",
            "playerId", jugador.getId(),
            "nombre",   jugador.getNombre()
        ));
    }

    public void procesarMuerteTotal(Player jugador, MensajeBroker broker) {
        jugador.morir();
        System.out.printf("[MUERTE] %s eliminado. Inventario y puntos borrados.%n",
                jugador.getNombre());

        broker.enviarATodos("/topic/estado", Map.of(
            "evento",   "PLAYER_ELIMINATED",
            "playerId", jugador.getId(),
            "nombre",   jugador.getNombre()
        ));
    }

    // ── Revivir ───────────────────────────────────────────────────────────────

    public void procesarRevive(Player derribado, Player socorrista,
                                MensajeBroker broker) {
        derribado.revivir();
        System.out.printf("[REVIVE] %s revivió a %s%n",
                socorrista.getNombre(), derribado.getNombre());

        broker.enviarATodos("/topic/estado", Map.of(
            "evento",    "PLAYER_REVIVED",
            "playerId",  derribado.getId(),
            "nombre",    derribado.getNombre(),
            "por",       socorrista.getNombre()
        ));
    }
}