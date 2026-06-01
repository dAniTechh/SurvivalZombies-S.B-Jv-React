package com.example.demo.engine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.demo.model.Player;
import com.example.demo.model.Zombie;

/**
 * Mueve zombies y detecta colisiones con jugadores.
 * Pathfinding fase 1: vector directo al jugador más cercano con deslizamiento en obstáculos.
 */
@Component
public class ZombieEngine {

    private final Map<String, Zombie> zombies = new ConcurrentHashMap<>();
    private final DamageEngine damageEngine;

    // ── Anti-stuck (evita que el zombie se quede pillado pegado a obstáculos) ──
    private static final int STUCK_TICKS_THRESHOLD = 12; // ~12*16ms ≈ 192ms
    private static final double STUCK_ANGLE_STEP_RAD = Math.toRadians(20); // abanico cada ~20°
    private static final int STUCK_SEARCH_RADIUS_SWEEP = 1; // 1 paso = misma longitud que el movimiento normal

    private final Map<String, Integer> stuckTicksByZombieId = new ConcurrentHashMap<>();


    // Mapa de colisiones (X, Y, Ancho, Alto) - Coincide con el frontend

    // Zombies: DEBEN atravesar MUROS ENTRE ZONAS, así que estos NO bloquean.
    @SuppressWarnings("unused")
    private static final double[][] MUROS_ENTRE_ZONAS = {
        // ══ MUROS ENTRE ZONAS (igual que GameEngine.java) ═════════════════════
        { 796,   0, 8, 220 }, { 796, 380, 8, 220 },
        {   0, 596, 250, 8 }, { 450, 596, 350, 8 },
        { 1596,   0, 8, 150 }, { 1596, 350, 8, 250 },
        {  800, 596, 150, 8 }, { 1250, 596, 350, 8 },
        { 1600, 596, 800, 8 },
        { 796,  600, 8, 150 }, { 796,  950, 8, 250 },
        { 1596,  600, 8,  50 }, { 1596,  900, 8, 300 },
        {  800, 1196, 150, 8 }, { 1250, 1196, 350, 8 },
        {  796, 1196,   8, 608 }, { 1596, 1196,   8, 608 },
        {  800, 1796, 800,   8 },
    };

    // Zombies: NO atraviesan OBSTÁCULOS INTERNOS (mesas/pilares) pero sí los “muros” entre zonas.
    private static final double[][] OBSTACULOS_INTERNOS = {
        // ══ OBSTÁCULOS INTERNOS ═══════════════════════════════════════════════
        // Z0
        {150, 150,  90, 45}, {560, 150,  90, 45}, {330, 320, 140, 60},
        { 80, 420,  60, 120}, {650, 420,  60, 120},
        // Z1
        { 870,  80, 60, 180}, {1470,  80, 60, 180}, {1100, 200,200,  40},
        { 870, 400, 60, 180}, {1470, 400, 60, 180},
        // Z2
        {1670,  80,  80,  80}, {2230,  80,  80,  80}, {1850, 180, 320,  30},
        {1680, 350,  60, 200}, {2260, 350,  60, 200},
        // Z3
        {  80, 680, 200, 50}, {  80, 770, 200, 50}, {  80, 860, 200, 50},
        { 500, 700,  80, 200}, { 350,1000, 200,  60},
        // Z4
        { 950, 680, 100, 100}, {1340, 680, 100, 100},
        { 950,1000, 100, 100}, {1340,1000, 100, 100}, {1150, 820, 100, 100},
        // Z5
        {1680, 650, 300,  40}, {1680,1100, 300,  40},
        {2100, 750,  60, 300}, {1700, 800,  80,  80},
        // Z6
        { 870,1280,  80, 200}, {1060,1280,  80, 200}, {1250,1280,  80, 200},
        {1440,1280,  80, 200}, { 950,1550, 500,  60},
    };

    // Puertas (se pueblan desde GameEngine via snapshot de estado en gamestate/puertas event en el futuro).
    // Para este juego, referenciamos puertas vía inclusión en GameEngine al modificar colisión.


    public ZombieEngine(DamageEngine damageEngine) {
        this.damageEngine = damageEngine;
    }

    // ── Gestión de zombies ────────────────────────────────────────────────────

    public void agregarZombies(List<Zombie> nuevos) {
        nuevos.forEach(z -> zombies.put(z.getId(), z));
        System.out.printf("[ZOMBIE] +%d zombies en juego (total: %d)%n",
                nuevos.size(), zombies.size());
    }

    public void limpiarMuertos() {
        zombies.values().removeIf(z -> !z.isVivo());
    }

    public List<Zombie> getZombiesVivos() {
        return zombies.values().stream()
                .filter(Zombie::isVivo)
                .collect(Collectors.toList());
    }

    public int contarVivos() {
        return (int) zombies.values().stream().filter(Zombie::isVivo).count();
    }

    public void limpiarTodo() { zombies.clear(); }

    // ── Tick de IA (llamado desde GameEngine cada 16ms) ───────────────────────

    /**
     * Para cada zombie vivo:
     * 1. Encuentra el jugador más cercano (o el más "olible")
     * 2. Se mueve hacia él evitando obstáculos mediante deslizamiento (Sliding)
     * 3. Si está en rango de mordisco → delegar a DamageEngine
     */
public void tick(Map<String, Player> jugadores, double deltaTime,
                     GameManager gameManager, MensajeBroker broker,
                     List<com.example.demo.model.Puerta> puertas) {

        // Filtro: Solo persiguen a los vivos que NO estén derribados

        List<Player> jugadoresVivos = jugadores.values().stream()
            .filter(p -> p.isVivo() && !p.isDerribado())
            .collect(Collectors.toList());

        // Si no hay nadie de pie, los zombies se quedan quietos
        if (jugadoresVivos.isEmpty()) return;

        for (Zombie z : getZombiesVivos()) {

            // 1. Elegir objetivo
            Player objetivo = elegirObjetivo(z, jugadoresVivos);
            if (objetivo == null) continue;
            z.setTargetPlayerId(objetivo.getId());

            // 2. Mover hacia el objetivo calculando el vector y aplicando deslizamiento
            double dx = objetivo.getPos().x - z.getPos().x;
            double dy = objetivo.getPos().y - z.getPos().y;
            double len = Math.sqrt(dx * dx + dy * dy);

            if (len > 0) {
                dx /= len;
                dy /= len;

                double vel = z.getVelocidad() * deltaTime;
                double nuevaX = z.getPos().x + (dx * vel);
                double nuevaY = z.getPos().y + (dy * vel);

                // Deslizamiento robusto:
                // - Primero intentamos mover completo.
                // - Si choca, probamos eje X/Y por separado.
                // - Si también falla, giramos un poco la dirección hacia un lado ("buscar salida")
                //   para evitar que se queden "pillados" pegados al muro cuando el vector directo falla.

                boolean movido = false;
                if (!colisionaZombie(nuevaX, nuevaY, puertas)) {
                    z.getPos().x = nuevaX;
                    z.getPos().y = nuevaY;
                    movido = true;
                }

                if (!movido) {
                    // Eje X
                    if (!colisionaZombie(nuevaX, z.getPos().y, puertas)) {
                        z.getPos().x = nuevaX;
                        movido = true;
                    }
                    // Eje Y (si X falló o dejó de mover)
                    if (!movido && !colisionaZombie(z.getPos().x, nuevaY, puertas)) {
                        z.getPos().y = nuevaY;
                        movido = true;
                    }
                }

                if (!movido) {
                    // Si no se pudo mover con los ejes (X/Y), intentamos un giro pequeño...
                    double ang = Math.atan2(dy, dx);
                    double vel2 = vel;

                    boolean movidoConGiro = false;

                    double ang1 = ang + Math.PI / 6.0;
                    double gx1 = z.getPos().x + Math.cos(ang1) * vel2;
                    double gy1 = z.getPos().y + Math.sin(ang1) * vel2;
                    if (!colisionaZombie(gx1, gy1, puertas)) {
                        z.getPos().x = gx1;
                        z.getPos().y = gy1;
                        movidoConGiro = true;
                    } else {
                        double ang2 = ang - Math.PI / 6.0;
                        double gx2 = z.getPos().x + Math.cos(ang2) * vel2;
                        double gy2 = z.getPos().y + Math.sin(ang2) * vel2;
                        if (!colisionaZombie(gx2, gy2, puertas)) {
                            z.getPos().x = gx2;
                            z.getPos().y = gy2;
                            movidoConGiro = true;
                        }
                    }

                    // ── Anti-stuck ──
                    if (!movidoConGiro) {
                        int prev = stuckTicksByZombieId.getOrDefault(z.getId(), 0);
                        stuckTicksByZombieId.put(z.getId(), prev + 1);

                        if (prev + 1 >= STUCK_TICKS_THRESHOLD) {
                            // Probamos un abanico alrededor de la dirección original buscando salida.
                            // Importante: respetamos colisión (puertas/obstáculos internos).
                            boolean found = false;
                            double baseX = z.getPos().x;
                            double baseY = z.getPos().y;

                            for (int sweep = 0; sweep <= STUCK_SEARCH_RADIUS_SWEEP && !found; sweep++) {
                                for (int k = 1; k <= 18 && !found; k++) {
                                    double step = STUCK_ANGLE_STEP_RAD * k;

                                    double a1 = ang + step;
                                    double sx1 = baseX + Math.cos(a1) * vel2;
                                    double sy1 = baseY + Math.sin(a1) * vel2;
                                    if (!colisionaZombie(sx1, sy1, puertas)) {
                                        z.getPos().x = sx1;
                                        z.getPos().y = sy1;
                                        found = true;
                                        break;
                                    }

                                    double a2 = ang - step;
                                    double sx2 = baseX + Math.cos(a2) * vel2;
                                    double sy2 = baseY + Math.sin(a2) * vel2;
                                    if (!colisionaZombie(sx2, sy2, puertas)) {
                                        z.getPos().x = sx2;
                                        z.getPos().y = sy2;
                                        found = true;
                                        break;
                                    }
                                }
                            }

                            if (found) {
                                stuckTicksByZombieId.put(z.getId(), 0);
                            }
                        }
                    }
                } else {
                    // Se movió: reset anti-stuck
                    stuckTicksByZombieId.put(z.getId(), 0);
                }

            }


            // 3. Comprobar colisión (radio de 20px = zombie toca al jugador)
            double dist = distancia(z.getPos().x, z.getPos().y,
                                    objetivo.getPos().x, objetivo.getPos().y);
            if (dist <= 20.0) {
                damageEngine.procesarMordisco(z, objetivo, jugadores, broker);
            }

            // Clamp de zombies al mundo completo (evita quedar fuera de mapa)
            z.getPos().x = Math.max(0, Math.min(2390, z.getPos().x));
            z.getPos().y = Math.max(0, Math.min(1790, z.getPos().y));
        }
    }

    // ── Pathfinding: elegir objetivo ──────────────────────────────────────────

    /**
     * Heurística combinada:
     * - Base: distancia euclidiana
     * - Bonus de "olor": si un jugador lleva quieto N segundos, los zombies lo perciben más cerca
     */
    private Player elegirObjetivo(Zombie zombie, List<Player> candidatos) {
        Player mejor      = null;
        double mejorScore = Double.MAX_VALUE;

        for (Player p : candidatos) {
            double dist = distancia(
                zombie.getPos().x, zombie.getPos().y,
                p.getPos().x,      p.getPos().y
            );

            // Penalización por quietud: -15px de distancia efectiva por segundo quieto
            double penalizacionOlor = p.segundosQuieto() * 15.0;
            double score = dist - penalizacionOlor;

            if (score < mejorScore) {
                mejorScore = score;
                mejor      = p;
            }
        }
        return mejor;
    }

    private double distancia(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // Método para comprobar si el zombie choca con un obstáculo
    // - Los zombies DEBEN atravesar MUROS ENTRE ZONAS.
    // - Los zombies NO deben atravesar OBSTÁCULOS INTERNOS (mesas/pilares).
    // - Además, se comporta como el jugador: UNA puerta cerrada es sólido.
    private boolean colisionaZombie(double x, double y, java.util.List<com.example.demo.model.Puerta> puertas) {
        final double R = 12.0;

        // 1) Puertas cerradas: sólido
        for (com.example.demo.model.Puerta p : puertas) {
            if (!p.isAbierta()) {
                if (x + R > p.getX() && x - R < p.getX() + p.getW() &&
                    y + R > p.getY() && y - R < p.getY() + p.getH()) {
                    return true;
                }
            }
        }

        // 2) Obstáculos internos: sólido
        for (double[] o : OBSTACULOS_INTERNOS) {
            if (x + R > o[0] && x - R < o[0] + o[2] &&
                y + R > o[1] && y - R < o[1] + o[3]) {
                return true;
            }
        }

        // 3) NO colisionar con MUROS_ENTRE_ZONAS
        return false;
    }
}