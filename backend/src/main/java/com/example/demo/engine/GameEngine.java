package com.example.demo.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.demo.model.CajaMagica;
import com.example.demo.model.Drop;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.model.Puerta;
import com.example.demo.model.Zombie;
import com.example.demo.service.AnalyticService;

@Service
public class GameEngine {

    private final ConcurrentHashMap<String, Player> jugadores = new ConcurrentHashMap<>();

    // ── Sistema de Drops (Black Ops 1 style) ──────────────────────────────────
    private final ConcurrentHashMap<String, Drop> drops = new ConcurrentHashMap<>();
    private int killsParaDrop    = 0;
    private int killsEnRonda     = 0;
    private static final int DROP_CADA_N_KILLS = 10;   // cada 10 kills → drop garantizado

    // ─── Estados de efectos globales temporales ───────────────────────────────
    private volatile boolean instakillActivo     = false;
    private volatile long    instakillFin        = 0;
    private volatile boolean doblesPuntosActivo  = false;
    private volatile long    doblesPuntosFin     = 0;
    private volatile boolean deathMachineActivo  = false;
    private volatile long    deathMachineFin     = 0;
    private volatile boolean fireSaleActivo      = false;
    private volatile long    fireSaleFin         = 0;

    // Caja mágica (estado mínimo para render en el canvas)
    // Se serializa en /topic/gamestate como gameState.caja = { x, y, activa }
    
    private final ZombieEngine  zombieEngine;
    private final DamageEngine  damageEngine;
    private final GameManager   gameManager;
    private final MensajeBroker broker;

    // ── Revive por proximidad (Downed → revivir) ───────────────────────────
    private static final double RADIO_REVIVE = 90.0;
    private static final long TIEMPO_REVIVE_MS = 4_000L;

    // reviveInProgress: derribadoId -> (socorristaId, inicioMs)
    private static class ReviveState {
        final String socorristaId;
        final long inicioMs;
        ReviveState(String socorristaId, long inicioMs) {
            this.socorristaId = socorristaId;
            this.inicioMs = inicioMs;
        }
    }
    private final Map<String, ReviveState> reviveEnCurso = new ConcurrentHashMap<>();

 private final AnalyticService analyticsService; 
    private long inicioPartidaMs;

    // ── Tamaño del mundo (fuente única de verdad) ──────────────────────────────
    public static final double MUNDO_W = 2400;
    public static final double MUNDO_H = 1800;

    // ══════════════════════════════════════════════════════════════════════════
    //  MAPA: 7 ZONAS INTERCONECTADAS
    //
    //  Columnas: A(0-800)   B(800-1600)   C(1600-2400)
    //  Filas:    1(0-600)   2(600-1200)   3(1200-1800)
    //
    //  [Z0:Lab A1] ──► [Z1:Pasillos B1] ──► [Z2:Armería C1]
    //       │                  │
    //       ▼                  ▼
    //  [Z3:Enferm A2] ──► [Z4:Patio B2] ──► [Z5:Búnker C2]
    //                          │
    //                          ▼
    //                   [Z6:Generadores B3]
    //
    //  Spawn: Z0 (400, 300)
    // ══════════════════════════════════════════════════════════════════════════

    // Lista MUTABLE para poder abrir puertas en runtime
    private final CopyOnWriteArrayList<Puerta> puertas = new CopyOnWriteArrayList<>(List.of(


        // Z0 → Z1  |  Pared Este de Z0  |  x=798, y=220-380 (puerta ancha 160px)
        new Puerta("Z0_Z1", 798, 220, 20, 160,  750, "Pasillos",        false),

        // Z0 → Z3  |  Pared Sur de Z0   |  x=250-450, y=598 (puerta ancha 200px)
        new Puerta("Z0_Z3", 250, 598, 200, 20,  750, "Enfermería",      false),

        // Z1 → Z2  |  Pared Este de Z1  |  x=1598, y=150-350
        new Puerta("Z1_Z2", 1598, 150, 20, 200, 1500, "Armería",        false),

        // Z1 → Z4  |  Pared Sur de Z1   |  x=950-1250, y=598
        new Puerta("Z1_Z4",  950, 598, 300, 20, 1000, "Patio Central",  false),

        // Z3 → Z4  |  Pared Este de Z3  |  x=798, y=750-950
        new Puerta("Z3_Z4",  798, 750,  20, 200, 1250, "Patio Central", false),

        // Z4 → Z5  |  Pared Este de Z4  |  x=1598, y=650-900
        new Puerta("Z4_Z5", 1598, 650,  20, 250, 1750, "Búnker",        false),

        // Z4 → Z6  |  Pared Sur de Z4   |  x=950-1250, y=1198
        new Puerta("Z4_Z6",  950, 1198, 300, 20, 2000, "Generadores",   false)
    ));
    private CajaMagica caja = new CajaMagica("CAJA_1", 1200, 900);

    // ══════════════════════════════════════════════════════════════════════════
    //  OBSTÁCULOS (X, Y, Ancho, Alto) — deben coincidir con renderer.js
    //
    //  INCLUYE los muros entre zonas (con huecos donde están las puertas).
    //  Grosor de muro entre zonas: 8px (tolerancia de colisión generosa).
    // ══════════════════════════════════════════════════════════════════════════
    private static final double[][] OBSTACULOS = {

        // ══ MUROS ENTRE ZONAS ══════════════════════════════════════════════════
        // Las puertas (Puerta) ya bloquean su propio hueco cuando están cerradas.
        // Aquí añadimos los segmentos SÓLIDOS del muro que rodean cada hueco.

        // ── Pared Z0 | Z1  (x=796, vertical, y=0..600)
        //    Puerta Z0_Z1: y=220..380  →  segmentos: y=0-220 y y=380-600
        { 796,   0, 8, 220 },   // tramo norte
        { 796, 380, 8, 220 },   // tramo sur

        // ── Pared Z0 | Z3  (y=596, horizontal, x=0..800)
        //    Puerta Z0_Z3: x=250..450  →  segmentos: x=0-250 y x=450-800
        {   0, 596, 250, 8 },   // tramo oeste
        { 450, 596, 350, 8 },   // tramo este

        // ── Pared Z1 | Z2  (x=1596, vertical, y=0..600)
        //    Puerta Z1_Z2: y=150..350  →  segmentos: y=0-150 y y=350-600
        { 1596,   0, 8, 150 },
        { 1596, 350, 8, 250 },

        // ── Pared Z1 | Z4  (y=596, horizontal, x=800..1600)
        //    Puerta Z1_Z4: x=950..1250  →  segmentos: x=800-950 y x=1250-1600
        {  800, 596, 150, 8 },
        { 1250, 596, 350, 8 },

        // ── Pared Z2 | Z5  (y=596, horizontal, x=1600..2400) — SIN PUERTA → completa
        { 1600, 596, 800, 8 },

        // ── Pared Z3 | Z4  (x=796, vertical, y=600..1200)
        //    Puerta Z3_Z4: y=750..950  →  segmentos: y=600-750 y y=950-1200
        { 796,  600, 8, 150 },
        { 796,  950, 8, 250 },

        // ── Pared Z4 | Z5  (x=1596, vertical, y=600..1200)
        //    Puerta Z4_Z5: y=650..900  →  segmentos: y=600-650 y y=900-1200
        { 1596,  600, 8,  50 },
        { 1596,  900, 8, 300 },

        // ── Pared Z4 | Z6  (y=1196, horizontal, x=800..1600)
        //    Puerta Z4_Z6: x=950..1250  →  segmentos: x=800-950 y x=1250-1600
        {  800, 1196, 150, 8 },
        { 1250, 1196, 350, 8 },

        // ── Bordes de Z6 (zona aislada, solo acceso por Z4|Z6)
        {  796, 1196,   8, 608 },   // pared izquierda Z6
        { 1596, 1196,   8, 608 },   // pared derecha Z6
        {  800, 1796, 800,   8 },   // pared sur Z6

        // ══ OBSTÁCULOS INTERNOS DE CADA ZONA ══════════════════════════════════

        // ── Z0: Laboratorio (0-800, 0-600) ──────────────────────────────────
        {150, 150,  90, 45},
        {560, 150,  90, 45},
        {330, 320, 140, 60},
        { 80, 420,  60, 120},
        {650, 420,  60, 120},

        // ── Z1: Pasillos (800-1600, 0-600) ──────────────────────────────────
        { 870,  80, 60, 180},
        {1470,  80, 60, 180},
        {1100, 200,200,  40},
        { 870, 400, 60, 180},
        {1470, 400, 60, 180},

        // ── Z2: Armería (1600-2400, 0-600) ──────────────────────────────────
        {1670,  80,  80,  80},
        {2230,  80,  80,  80},
        {1850, 180, 320,  30},
        {1680, 350,  60, 200},
        {2260, 350,  60, 200},

        // ── Z3: Enfermería (0-800, 600-1200) ────────────────────────────────
        {  80, 680, 200, 50},
        {  80, 770, 200, 50},
        {  80, 860, 200, 50},
        { 500, 700,  80, 200},
        { 350,1000, 200,  60},

        // ── Z4: Patio Central (800-1600, 600-1200) ───────────────────────────
        { 950, 680, 100, 100},
        {1340, 680, 100, 100},
        { 950,1000, 100, 100},
        {1340,1000, 100, 100},
        {1150, 820, 100, 100},

        // ── Z5: Búnker (1600-2400, 600-1200) ────────────────────────────────
        {1680, 650, 300,  40},
        {1680,1100, 300,  40},
        {2100, 750,  60, 300},
        {1700, 800,  80,  80},

        // ── Z6: Generadores (800-1600, 1200-1800) ───────────────────────────
        { 870,1280,  80, 200},
        {1060,1280,  80, 200},
        {1250,1280,  80, 200},
        {1440,1280,  80, 200},
        { 950,1550, 500,  60},
    };

    // ── Colisión AABB ─────────────────────────────────────────────────────────
    private boolean colisiona(double x, double y) {
        final double R = 12.0;
        for (Puerta p : puertas) {
            if (!p.isAbierta() &&
                x + R > p.getX() && x - R < p.getX() + p.getW() &&
                y + R > p.getY() && y - R < p.getY() + p.getH()) {
                return true;
            }
        }
        for (double[] o : OBSTACULOS) {
            if (x + R > o[0] && x - R < o[0] + o[2] &&
                y + R > o[1] && y - R < o[1] + o[3]) {
                return true;
            }
        }
        return false;
    }

    // ── Interacción con puertas (F) ───────────────────────────────────────────
    public void procesarInteraccion(String playerId) {
        Player jugador = jugadores.get(playerId);
        if (jugador == null || !jugador.isVivo() || jugador.isDerribado()) return;

        double px = jugador.getPos().getX();
        double py = jugador.getPos().getY();

        for (Puerta puerta : puertas) {
            if (puerta.isAbierta()) continue;

            double cx   = puerta.getX() + puerta.getW() / 2.0;
            double cy   = puerta.getY() + puerta.getH() / 2.0;
            double dist = Math.sqrt(Math.pow(px - cx, 2) + Math.pow(py - cy, 2));

            if (dist <= 90.0) { // radio de interacción generoso
                
                // 1. BLOQUEO SINCRONIZADO: Evita que dos jugadores compren la misma puerta a la vez
                synchronized (puerta) {
                    // Doble comprobación por si otro hilo la abrió un milisegundo antes
                    if (puerta.isAbierta()) return; 

                    if (jugador.gastarPuntos(puerta.getCoste())) {
                        puerta.setAbierta(true);
                        
                        // 2. EMPUJE (PUSH): Mueve al jugador a través de la puerta a la zona nueva
                        boolean esVertical = puerta.getW() < puerta.getH();
                        if (esVertical) {
                            double dirX = (px < cx) ? 1.0 : -1.0;
                            double destinoX = cx + dirX * (puerta.getW() / 2.0 + 50.0);
                            jugador.getPos().setX(destinoX);
                        } else {
                            double dirY = (py < cy) ? 1.0 : -1.0;
                            double destinoY = cy + dirY * (puerta.getH() / 2.0 + 50.0);
                            jugador.getPos().setY(destinoY);
                        }

                        // Aseguramos que el empuje no lo saque de los límites del mapa
                        jugador.getPos().setX(Math.max(10, Math.min(MUNDO_W - 10, jugador.getPos().getX())));
                        jugador.getPos().setY(Math.max(10, Math.min(MUNDO_H - 10, jugador.getPos().getY())));

                        // 3. EMITIR EVENTO
                        broker.enviarATodos("/topic/puertas", Map.of(
                            "evento",            "PUERTA_ABIERTA",
                            "puertaId",          puerta.getId(),
                            "nombreZonaDestino", puerta.getNombreZonaDestino(),
                            "abierta",           true
                        ));
                        System.out.printf("[PUERTA] %s abrió %s → %s%n",
                            jugador.getNombre(), puerta.getId(), puerta.getNombreZonaDestino());
                    } else {
                        broker.enviarAJugador(playerId, "/queue/aviso", Map.of(
                            "tipo",   "SIN_PUNTOS",
                            "coste",  puerta.getCoste(),
                            "tienes", jugador.getPuntos()
                        ));
                    }
                }
                return; // Solo interacciona con una puerta a la vez
            }
        }

        // ── Interacción con la Caja Mágica ──
        if (this.caja.isActiva() && !this.caja.isAnimando()) {
            double distCaja = Math.hypot(px - this.caja.getX(), py - this.caja.getY());
            
            if (distCaja <= 90.0) {
                if (jugador.gastarPuntos(this.caja.getCoste())) {
                    this.caja.setAnimando(true);
                    
                    System.out.println("[CAJA] " + jugador.getNombre() + " ha comprado la caja.");

                    // Creamos un hilo para simular el tiempo que tarda la caja en pensar el arma (3 segundos)
                    new Thread(() -> {
                        try { Thread.sleep(3000); } catch (InterruptedException e) {}
                        
                        // Elegimos un arma aleatoria
                        Player.TipoArma[] armas = Player.TipoArma.values();
                        Player.TipoArma armaPremiada = armas[new java.util.Random().nextInt(armas.length)];
                        
                       jugador.recibirNuevaArma(armaPremiada);
                        this.caja.setAnimando(false); // La caja vuelve a estar disponible
                        
                        System.out.println("[CAJA] A " + jugador.getNombre() + " le tocó: " + armaPremiada.name());
                    }).start();
                    
                } else {
                    broker.enviarAJugador(playerId, "/queue/aviso", Map.of(
                        "tipo",   "SIN_PUNTOS",
                        "coste",  this.caja.getCoste(),
                        "tienes", jugador.getPuntos()
                    ));
                }
            }
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────────
    public GameEngine(ZombieEngine zombieEngine, DamageEngine damageEngine,
                      GameManager gameManager, MensajeBroker broker, AnalyticService analyticsService) {
        this.zombieEngine = zombieEngine;
        this.damageEngine = damageEngine;
        this.gameManager  = gameManager;
        this.broker       = broker;
        this.analyticsService = analyticsService;
    }

    private long ultimoTickNanos  = System.nanoTime();
    private int  emitCounter      = 0;
    private boolean esperandoNuevaRonda     = true;
    private boolean esperandoReinicioManual = false;   // pausa hasta que el jugador pulse "Reiniciar"
    private long    tiempoInicioEspera      = System.nanoTime();
    private int     ultimoSegundoEnviado    = -1;
    private static final long PAUSA_RONDAS_NANOS = 10_000_000_000L;

    // ── Gestión de Drops ─────────────────────────────────────────────────────

    private void actualizarDrops(long ahoraMs) {
        // 1. Expirar drops que llevan >30s en el suelo
        drops.values().removeIf(d -> d.expirado(ahoraMs));

        // 2. Expirar efectos globales y notificar al frontend
        if (instakillActivo && ahoraMs > instakillFin) {
            instakillActivo = false;
            notificarFinEfecto("INSTA_KILL");
        }
        if (doblesPuntosActivo && ahoraMs > doblesPuntosFin) {
            doblesPuntosActivo = false;
            notificarFinEfecto("DOUBLE_POINTS");
        }
        if (deathMachineActivo && ahoraMs > deathMachineFin) {
            deathMachineActivo = false;
            notificarFinEfecto("DEATH_MACHINE");
        }
        if (fireSaleActivo && ahoraMs > fireSaleFin) {
            fireSaleActivo = false;
            caja.setCoste(950); // restaurar precio normal
            notificarFinEfecto("FIRE_SALE");
        }

        // 3. Recoger drops: cualquier jugador que pise uno (radio 38px)
        for (Player p : jugadores.values()) {
            if (!p.isVivo() || p.isDerribado()) continue;
            for (Drop d : drops.values()) {


                double dist = Math.hypot(p.getPos().x - d.getPos().x,
                                         p.getPos().y - d.getPos().y);
                if (dist <= 38.0) {
                    if (drops.remove(d.getId()) != null) {
                        aplicarEfectoDrop(d, p, ahoraMs);
                    }
                    break;
                }
            }
        }
    }

    private void notificarFinEfecto(String tipo) {
        System.out.printf("[DROP] Efecto %s terminado%n", tipo);
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("evento", "FIN_EFECTO");
        msg.put("tipo", tipo);
        broker.enviarATodos("/topic/powerup", msg);
    }

    private void aplicarEfectoDrop(Drop drop, Player recogedor, long ahoraMs) {
        Drop.TipoDrop tipo = drop.getTipo();
        System.out.printf("[DROP] %s recogió %s%n", recogedor.getNombre(), tipo.name());

        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("por", recogedor.getNombre());
        msg.put("evento", tipo.name());

        switch (tipo) {

            // ── NUKE: mata todos los zombies ─────────────────────────────────
          // ── NUKE: mata todos los zombies ─────────────────────────────────
            case NUKE: {
                // 1. Matar todos los zombies vivos en el engine
                int killed = 0;
                for (com.example.demo.model.Zombie z : zombieEngine.getZombiesVivos()) {
                    synchronized (z) {
                        if (z.isVivo()) {
                            z.recibirDaño(Integer.MAX_VALUE);
                            killed++;
                        }
                    }
                }
                zombieEngine.limpiarMuertos();

                // 2. Drenamos la ronda
                int extras = gameManager.nukearRonda();
                int totalBajas = killed + extras;

         
                int bonusNuke = doblesPuntosActivo ? 800 : 400;
                
                // Repartimos el dinero a todo el equipo
                jugadores.values().forEach(p -> {
                    // Opcional: podrías poner "if (p.isVivo())" si no quieres que los muertos cobren
                    p.sumarPuntos(bonusNuke); 
                });

                killsParaDrop = 0;
                msg.put("kills", totalBajas);
                msg.put("duracion", 0);
                break;
            }

            // ── MAX AMMO: recarga + bonus pts ────────────────────────────────
            case MAX_AMMO: {
                int bonus = doblesPuntosActivo ? 600 : 300;
                jugadores.values().forEach(p -> p.sumarPuntos(bonus));
                msg.put("bonusPts", bonus);
                msg.put("duracion", 0);
                break;
            }

            // ── CARPENTER: cura a todos los jugadores ────────────────────────
            case CARPENTER: {
                jugadores.values().forEach(p -> {
                    if (p.isVivo() && !p.isDerribado()) {
                        p.setSalud(p.getClase().saludBase);
                        p.sumarPuntos(doblesPuntosActivo ? 500 : 200);
                    }
                });
                msg.put("duracion", 0);
                break;
            }

            // ── INSTA-KILL: 20 s ─────────────────────────────────────────────
            case INSTA_KILL: {
                instakillActivo = true;
                instakillFin    = ahoraMs + tipo.duracionEfectoMs();
                msg.put("duracion", tipo.duracionEfectoMs() / 1000);
                msg.put("fin", instakillFin);
                break;
            }

            // ── DOUBLE POINTS: 30 s ──────────────────────────────────────────
            case DOUBLE_POINTS: {
                doblesPuntosActivo = true;
                doblesPuntosFin    = ahoraMs + tipo.duracionEfectoMs();
                msg.put("duracion", tipo.duracionEfectoMs() / 1000);
                msg.put("fin", doblesPuntosFin);
                break;
            }

            // ── DEATH MACHINE: daño x4 durante 30 s ─────────────────────────
            case DEATH_MACHINE: {
                deathMachineActivo = true;
                deathMachineFin    = ahoraMs + tipo.duracionEfectoMs();
                msg.put("duracion", tipo.duracionEfectoMs() / 1000);
                msg.put("fin", deathMachineFin);
                break;
            }

            // ── FIRE SALE: caja gratis 30 s ──────────────────────────────────
            case FIRE_SALE: {
                fireSaleActivo = true;
                fireSaleFin    = ahoraMs + tipo.duracionEfectoMs();
                caja.setCoste(10);
                msg.put("duracion", tipo.duracionEfectoMs() / 1000);
                msg.put("fin", fireSaleFin);
                break;
            }
        }

        broker.enviarATodos("/topic/powerup", msg);
    }

    // Calcula saludMax escalando con ronda a partir de saludBase.
    // Para rondas > 10: saludMax = saludBase × 1.2^(ronda-10)
    // Para rondas <= 10: devuelve saludBase.
   private int calcularSaludMaxJugador(int saludBase, int ronda) {
        if (ronda <= 7) return saludBase;
        
        double factor = Math.pow(1.17, ronda - 7); 
        double val = saludBase * factor;
        
        return (int) Math.max(1, Math.round(val));
    }

    // ── Spawn de drop según probabilidad y kill count ─────────────────────────

    private void spawnDropSiToca(double x, double y) {
        killsParaDrop++;
        killsEnRonda++;

        // Drop garantizado cada 10 kills; además, tirada aleatoria (8%) entre medias
        boolean dropGarantizado = (killsParaDrop % DROP_CADA_N_KILLS == 0);
        boolean dropAleatorio   = !dropGarantizado && (Math.random() < 0.08);

        if (!dropGarantizado && !dropAleatorio) return;

        Drop drop = Drop.aleatorio(new Position(x, y));
        drops.put(drop.getId(), drop);

        System.out.printf("[DROP] ¡%s spawneado! tipo=%-14s en (%.0f,%.0f)  kill#%d%n",
                dropGarantizado ? "DROP GARANTIZADO" : "drop aleatorio",
                drop.getTipo().name(), x, y, killsEnRonda);

        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("evento",     "DROP_SPAWNED");
        msg.put("tipo",       drop.getTipo().name());
        msg.put("x",          x);
        msg.put("y",          y);
        msg.put("id",         drop.getId());
        msg.put("garantizado", dropGarantizado);
        broker.enviarATodos("/topic/powerup", msg);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GAME LOOP  ~60 TPS
    // ══════════════════════════════════════════════════════════════════════════
@Scheduled(fixedRate = 16)
    public void gameLoop() {
        // Congelamos el tiempo exacto de este fotograma (tick)
        long ahoraNanos  = System.nanoTime();
        long ahoraMs     = System.currentTimeMillis(); // 🔥 Calculado una sola vez
        
        double deltaTime = (ahoraNanos - ultimoTickNanos) / 1_000_000_000.0;
        ultimoTickNanos  = ahoraNanos;

        jugadores.values().forEach(p -> actualizarJugador(p, deltaTime));
        gestionarRondas(ahoraNanos); // Usa nanos para la precisión milimétrica del descanso

        // Usamos la variable cacheada
        procesarRevivePorProximidad(ahoraMs);

        if (!esperandoNuevaRonda && !esperandoReinicioManual) {
            zombieEngine.tick(jugadores, deltaTime, gameManager, broker, puertas);
            zombieEngine.limpiarMuertos();
        }
        
        // Reutilizamos la misma variable
        actualizarDrops(ahoraMs);
        
        comprobarDesangrados();

        emitCounter++;
        if (emitCounter >= 3) { 
            emitCounter = 0; 
            // Pasamos el tiempo cacheado también aquí
            emitirEstadoMundo(ahoraMs); 
        }
    }
    // ── Rondas ────────────────────────────────────────────────────────────────


    private void gestionarRondas(long ahora) {
        if (jugadores.isEmpty()) return;

        // Bloqueado hasta que alguien pulse "Reiniciar Partida"
        if (esperandoReinicioManual) return;

        // ── 1. GAME OVER: Lo primero que comprobamos siempre ──
        boolean todosEliminados = jugadores.values().stream()
            .allMatch(p -> !p.isVivo() && !p.isDerribado());

        // Si todos han muerto, da igual si estamos en descanso o jugando. Se acaba.
        if (todosEliminados && !jugadores.isEmpty()) {
            // Congelamos el juego y esperamos acción manual
            esperandoReinicioManual = true;
            esperandoNuevaRonda = false; // Cortamos la cuenta atrás si estaba activa
            zombieEngine.limpiarTodo();

            // ── NUEVO: Extraemos y guardamos analíticas en MongoDB  ──
            Player mvp = jugadores.values().stream()
                .max(java.util.Comparator.comparingInt(p -> p.getPuntos()))
                .orElse(null);

            if (mvp != null) {
                String jugador = mvp.getNombre();
                int ronda = gameManager.getRondaActual();
                int totalKills = killsEnRonda;

                // Extraemos las armas únicas de los jugadores al morir
                List<String> armasUsadas = jugadores.values().stream()
                    .flatMap(p -> java.util.stream.Stream.of(p.getArmaEquipada().name(), p.getArmaSecundaria().name()))
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());

                long duracionSegundos = (System.currentTimeMillis() - this.inicioPartidaMs) / 1000;

                // Persistimos en MongoDB de forma asíncrona/segura
                analyticsService.guardarPartida(jugador, ronda, totalKills, armasUsadas, duracionSegundos);
            }
            // ─────────────────────────────────────────────────────────────────

            broker.enviarATodos("/topic/ronda", Map.of(
                "evento", "GAME_OVER",
                "ronda",  gameManager.getRondaActual()
            ));
            return;
        }

        // ── 2. CUENTA ATRÁS DEL DESCANSO ──
        if (esperandoNuevaRonda) {
            long elapsed  = ahora - tiempoInicioEspera;
            int  restante = (int) Math.max(0,
                (PAUSA_RONDAS_NANOS - elapsed) / 1_000_000_000L);

            if (restante != ultimoSegundoEnviado) {
                ultimoSegundoEnviado = restante;
                broker.enviarATodos("/topic/ronda", Map.of(
                    "evento",   "CUENTA_ATRAS",
                    "restante", restante,
                    "ronda",    gameManager.getRondaActual() + 1
                ));
            }
            if (elapsed >= PAUSA_RONDAS_NANOS) iniciarRonda();
            return;
        }

        // ── 3. FIN DE RONDA NORMAL ──
        if (gameManager.isRondaTerminada() && zombieEngine.contarVivos() == 0) {
            esperandoNuevaRonda  = true;
            tiempoInicioEspera   = ahora;
            ultimoSegundoEnviado = -1;
            
            jugadores.values().stream()
                .filter(p -> !p.isVivo())
                .forEach(p -> {
                    int saludBase = p.getClase().saludBase;
                    int saludMaxEscalada = calcularSaludMaxJugador(saludBase, gameManager.getRondaActual());
                    p.setSaludMax(saludMaxEscalada);
                    p.respawn(new Position(400, 300));
                });
                
            broker.enviarATodos("/topic/ronda", Map.of(
                "evento", "RONDA_COMPLETADA",
                "ronda",  gameManager.getRondaActual()
            ));
        }
    }

    private void iniciarRonda() {
        esperandoNuevaRonda = false;
        zombieEngine.limpiarTodo();
        killsParaDrop      = 0;
        killsEnRonda       = 0;



          if (gameManager.getRondaActual() == 1) {
            this.inicioPartidaMs = System.currentTimeMillis();
        }

        if (!fireSaleActivo) {
            caja.setCoste(950);
        }
        
        if (!fireSaleActivo) {
            caja.setCoste(950);
        }
        
        caja.setActiva(true);

        List<Zombie> nuevos = gameManager.iniciarSiguienteRonda(jugadores.size());
        zombieEngine.agregarZombies(nuevos);
        
        for (Player p : jugadores.values()) {
            if (!p.isVivo() || p.isDerribado()) p.respawn(new Position(400, 300));
            else {
                int saludBase = p.getClase().saludBase;
                int saludMaxEscalada = calcularSaludMaxJugador(saludBase, gameManager.getRondaActual());
                p.setSaludMax(saludMaxEscalada);
                p.setSalud(saludMaxEscalada);
            }
        }
        
        broker.enviarATodos("/topic/ronda", Map.of(
            "evento",  "RONDA_INICIO",
            "ronda",   gameManager.getRondaActual(),
            "zombies", gameManager.getZombiesRestantes()
        ));
    }

    // ── Movimiento jugador ────────────────────────────────────────────────────
    private void actualizarJugador(Player p, double deltaTime) {
        if (!p.isVivo() || p.isDerribado()) return;
        boolean[] in = p.getInput();
        double dx = 0, dy = 0;
        if (in[0]) dy -= 1; if (in[1]) dx -= 1;
        if (in[2]) dy += 1; if (in[3]) dx += 1;
        if (dx == 0 && dy == 0) return;

        double len = Math.sqrt(dx * dx + dy * dy);
        dx /= len; dy /= len;
        double vel = p.getVelocidadActual() * deltaTime;
        double nx  = p.getPos().getX() + dx * vel;
        double ny  = p.getPos().getY() + dy * vel;

        if (!colisiona(nx, p.getPos().getY())) p.getPos().setX(nx);
        if (!colisiona(p.getPos().getX(), ny)) p.getPos().setY(ny);

        p.getPos().setX(Math.max(10, Math.min(MUNDO_W - 10, p.getPos().getX())));
        p.getPos().setY(Math.max(10, Math.min(MUNDO_H - 10, p.getPos().getY())));
        p.registrarMovimiento();
    }

    private void comprobarDesangrados() {
        jugadores.values().stream()
            .filter(p -> p.isDerribado() && p.seHaDesangrado())
            .forEach(p -> damageEngine.procesarMuerteTotal(p, broker));
    }
private void procesarRevivePorProximidad(long ahoraMs) {
        // Para cada jugador derribado, buscamos un vivo cerca.
        for (Player derribado : jugadores.values()) {
            if (derribado == null) continue;
            
            // ── CORRECCIÓN AQUÍ ──
            // Si NO está derribado, no necesita ayuda. Pasamos al siguiente.
            // Eliminamos la comprobación de isVivo() para evitar el muro lógico.
            if (!derribado.isDerribado()) continue;

            Player mejorSocorrista = null;
            double mejorDist = Double.MAX_VALUE;
            
            for (Player socorrista : jugadores.values()) {
                if (socorrista == null) continue;
                if (socorrista == derribado) continue; // No te puedes revivir a ti mismo
                
                // El socorrista SÍ tiene que estar de pie y vivo
                if (!socorrista.isVivo() || socorrista.isDerribado()) continue;

                double dist = Math.hypot(
                    socorrista.getPos().x - derribado.getPos().x,
                    socorrista.getPos().y - derribado.getPos().y);

                if (dist <= RADIO_REVIVE && dist < mejorDist) {
                    mejorDist = dist;
                    mejorSocorrista = socorrista;
                }
            }

            ReviveState estado = reviveEnCurso.get(derribado.getId());

            // Si no hay nadie cerca: cancelamos (limpiamos estado)
            if (mejorSocorrista == null) {
                if (estado != null) reviveEnCurso.remove(derribado.getId());
                continue;
            }

            // Si ya había revive en curso con el mismo socorrista: continuamos
            if (estado != null && estado.socorristaId.equals(mejorSocorrista.getId())) {
                long elapsed = ahoraMs - estado.inicioMs;
                if (elapsed >= TIEMPO_REVIVE_MS) {
                    reviveEnCurso.remove(derribado.getId());
                    damageEngine.procesarRevive(derribado, mejorSocorrista, broker);
                }
                continue;
            }

            // Si está cerca pero el socorrista cambió: reiniciamos el contador.
            reviveEnCurso.put(derribado.getId(), new ReviveState(mejorSocorrista.getId(), ahoraMs));
        }

        // Limpieza extra: si el derribado ya no está derribado, limpiar estado.
        reviveEnCurso.entrySet().removeIf(e -> {
            Player p = jugadores.get(e.getKey());
            return (p == null || !p.isDerribado());
        });
    }


// ── Snapshot → clientes ───────────────────────────────────────────────────
    private void emitirEstadoMundo(long ahoraMs) {
        List<Map<String, Object>> snapJ = new ArrayList<>(jugadores.size());
        for (Player p : jugadores.values()) {
            Map<String, Object> d = new HashMap<>();
            d.put("id",        p.getId());
            d.put("nombre",    p.getNombre());
            d.put("x",         p.getPos().x);
            d.put("y",         p.getPos().y);
            d.put("salud",     p.getSalud());
            d.put("saludMax",  p.getSaludMax());
            d.put("puntos",    p.getPuntos());
            d.put("vivo",      p.isVivo());
            d.put("derribado", p.isDerribado());
            d.put("espectador",!p.isVivo() && !p.isDerribado());
            d.put("clase",     p.getClase().name());
            d.put("arma",      p.getArmaEquipada().name());
            d.put("armaSecundaria", p.getArmaSecundaria().name());
            d.put("perks",     p.getPerks());
            d.put("skin",      p.getSkin());
            snapJ.add(d);
        }

        List<Map<String, Object>> snapZ = new ArrayList<>();
        for (Zombie z : zombieEngine.getZombiesVivos()) {
            Map<String, Object> d = new HashMap<>();
            d.put("id",    z.getId());
            d.put("x",     z.getPos().x);
            d.put("y",     z.getPos().y);
            d.put("salud", z.getSaludActual());
            d.put("max",   z.getSaludMax());
            d.put("tipo",  z.getTipo().name());
            snapZ.add(d);
        }

        List<Map<String, Object>> snapP = new ArrayList<>(puertas.size());
        for (Puerta p : puertas) {
            Map<String, Object> d = new HashMap<>();
            d.put("id",     p.getId());
            d.put("x",      p.getX());
            d.put("y",      p.getY());
            d.put("w",      p.getW());
            d.put("h",      p.getH());
            d.put("coste",  p.getCoste());
            d.put("zona",   p.getNombreZonaDestino());
            d.put("abierta",p.isAbierta());
            snapP.add(d);
        }

        Map<String, Object> gameStateMap = new HashMap<>();
        gameStateMap.put("jugadores", snapJ);
        gameStateMap.put("zombies",   snapZ);
        gameStateMap.put("puertas",   snapP);
        gameStateMap.put("ronda",     gameManager.getRondaActual());
        gameStateMap.put("restantes", gameManager.getZombiesRestantes());

        Map<String, Object> snapCaja = new HashMap<>();
        snapCaja.put("id",      caja.getId());
        snapCaja.put("x",       caja.getX());
        snapCaja.put("y",       caja.getY());
        snapCaja.put("activa",  caja.isActiva());
        snapCaja.put("animando",caja.isAnimando());
        snapCaja.put("coste",   caja.getCoste());
        gameStateMap.put("caja", snapCaja);

        // --- DROPS ---
        List<Map<String, Object>> snapDrops = new ArrayList<>();
       
        for (Drop d : drops.values()) {
            if (!d.expirado(ahoraMs)) {
                Map<String, Object> dd = new HashMap<>();
                dd.put("id",        d.getId());
                dd.put("tipo",      d.getTipo().name());
                dd.put("x",         d.getPos().x);
                dd.put("y",         d.getPos().y);
                dd.put("msRestantes", d.msRestantesEnSuelo(ahoraMs));
                snapDrops.add(dd);
            }
        }
        gameStateMap.put("drops", snapDrops);

        // --- EFECTOS ACTIVOS (con ms restantes para countdown) ---
        Map<String, Object> efectos = new HashMap<>();
        efectos.put("instakill",     instakillActivo);
        efectos.put("instakillMs",   instakillActivo    ? Math.max(0, instakillFin    - ahoraMs) : 0);
        efectos.put("doblesPuntos",  doblesPuntosActivo);
        efectos.put("doblesPuntosMs",doblesPuntosActivo ? Math.max(0, doblesPuntosFin - ahoraMs) : 0);
        efectos.put("deathMachine",  deathMachineActivo);
        efectos.put("deathMachineMs",deathMachineActivo ? Math.max(0, deathMachineFin - ahoraMs) : 0);
        efectos.put("fireSale",      fireSaleActivo);
        efectos.put("fireSaleMs",    fireSaleActivo     ? Math.max(0, fireSaleFin     - ahoraMs) : 0);
        gameStateMap.put("efectos", efectos);

        broker.enviarATodos("/topic/gamestate", gameStateMap);
    }

    // ── API para GameController ───────────────────────────────────────────────

    /**
     * Llamado cuando el jugador pulsa "Reiniciar Partida" en la pantalla de GAME_OVER.
     * Resetea absolutamente todo a cero: ronda 0, puertas cerradas, puntos borrados,
     * efectos cancelados — y arranca la cuenta atrás para la primera ronda.
     */
public synchronized void reiniciarPartidaManual() {
        // 1. Resetear estado de rondas / zombies
        gameManager.reiniciarPartida();
        zombieEngine.limpiarTodo();
        drops.clear();
        killsParaDrop      = 0;
        killsEnRonda       = 0;
        
        // Restaurar estado del mapa y utilidades
        puertas.forEach(p -> p.setAbierta(false));
        caja.setActiva(true);
        caja.setAnimando(false);
        reviveEnCurso.clear();
        
        // 2. Cancelar todos los efectos de poder
        instakillActivo    = false;
        doblesPuntosActivo = false;
        deathMachineActivo = false;
        fireSaleActivo     = false;
        caja.setCoste(950);

        // 3. Respawnear jugadores con stats iniciales y economía limpia
        jugadores.values().forEach(p -> {
            int saludBase = p.getClase().saludBase;
            p.setSaludMax(saludBase);
            p.respawn(new Position(400, 300));
            
            // ── NUEVO: Reinicia el dinero a 500, quita perks y da solo la pistola ──
            p.reiniciarEstadisticasIniciales();
        });

        // 4. Salir del estado de espera manual y arrancar cuenta atrás normal
        esperandoReinicioManual  = false;
        esperandoNuevaRonda      = true;
        tiempoInicioEspera       = System.nanoTime();
        ultimoSegundoEnviado     = -1;

        System.out.println("[GAME] ── Partida reiniciada manualmente. Ronda 0. ──");
        broker.enviarATodos("/topic/ronda", Map.of("evento", "REINICIO", "ronda", 0));
    }

    public void registrarJugador(String sid, String nom, String skin) {
        Player p = new Player(sid, nom, new Position(400, 300), Player.ClaseJugador.jugador, skin);
        jugadores.put(sid, p);
        System.out.printf("[ENGINE] + %s conectado con skin %s%n", nom, skin);
    }

    public void desconectarJugador(String sid) {
        Player p = jugadores.remove(sid);
        if (p != null) System.out.printf("[ENGINE] - %s desconectado%n", p.getNombre());

        // Si el jugador se desconecta estando en un revive, limpiamos el estado huérfano.
        reviveEnCurso.remove(sid);
        reviveEnCurso.entrySet().removeIf(e -> e.getValue() != null && sid.equals(e.getValue().socorristaId));
    }

    public void procesarInput(String sid, boolean[] in) {
        Player p = jugadores.get(sid);
        if (p != null) p.setInput(in);
    }

    public void procesarSprint(String sid, boolean s) {
        Player p = jugadores.get(sid);
        if (p != null) p.setSprinting(s);
    }

    public void procesarDisparo(String sid, String zid, boolean h) {
        Player p = jugadores.get(sid);
        if (p == null || !p.isVivo()) return;
        // INSTA_KILL o DEATH_MACHINE → matar de un golpe
        boolean superKill = instakillActivo || deathMachineActivo;
        zombieEngine.getZombiesVivos().stream()
            .filter(z -> z.getId().equals(zid))
            .findFirst()
            .ifPresent(z -> {
                if (damageEngine.procesarDisparo(p, z, h, superKill, broker)) {
                    // Bonus por Doble Puntos (igual al kill base)
                    if (doblesPuntosActivo) p.sumarPuntos(h ? 100 : 60);
                    Zombie nuevo = gameManager.zombieMuerto();
                    if (nuevo != null) zombieEngine.agregarZombies(List.of(nuevo));
                    spawnDropSiToca(z.getPos().x, z.getPos().y);
                }
            });
    }


    public void procesarCambioArma(String sid) {
        Player p = jugadores.get(sid);
        if (p != null && p.isVivo()) {
            p.alternarArma();
        }
    }
}
