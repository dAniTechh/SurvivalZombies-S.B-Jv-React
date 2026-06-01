package com.example.demo.model;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Player {
    
    private final String skin; 
    // ── 1. DEFINICIÓN DE CLASES (Estadísticas base) ────────────────────────────
    public enum ClaseJugador {
        jugador(100, 200.0),
        jugador2(150, 160.0),
        jugador3(80, 250.0),
        jugador4(100, 215.0);

        public final int saludBase;
        public final double velocidadBase;

        ClaseJugador(int saludBase, double velocidadBase) {
            this.saludBase = saludBase;
            this.velocidadBase = velocidadBase;
        }
    }

    // ── 2. DEFINICIÓN DE ARMAS (Daño) ──────────────────────────────────────────
    public enum TipoArma {
        PISTOLA(35, 100, "Pistola 9mm"),
        ESCOPETA(80, 200, "Escopeta de Corredera"),
        FUSIL_ASALTO(45, 120, "Fusil de Asalto (M4)"),
        SNIPER(150, 500, "Rifle de Francotirador"),
        THUNDER(9999, 9999, "Cañón Thunder"), // 🔥 NUEVA ARMA LETAL (Instakill)
        DESARMADO(0, 0, "Manos Vacías");      // 🔥 AÑADIDO: Para el hueco vacío del inventario

        public final int danoBase;
        public final int danoHeadshot;
        public final String nombreReal;

        TipoArma(int danoBase, int danoHeadshot, String nombreReal) {
            this.danoBase = danoBase;
            this.danoHeadshot = danoHeadshot;
            this.nombreReal = nombreReal;
        }
    }

    // ── Identidad ──────────────────────────────────────────────────────────────
    private final String id;
    private final String nombre;
    private final ClaseJugador clase;

    // ── Estado vital ───────────────────────────────────────────────────────────
    private int salud;
    private int saludMax;
    private boolean vivo = true;
    private boolean derribado = false;   // "Downed": gateando, solo pistola
    private long tiempoDesangrado = 0;   // ms cuando empezó a desangrarse
    public static final long TIEMPO_DESANGRADO_MS = 15_000;

    // ── Economía y equipamiento (🔥 AHORA ES THREAD-SAFE Y CON 2 ARMAS) ────────
    private final AtomicInteger puntos = new AtomicInteger(0);
    private final Set<String> perks = new HashSet<>();
    
    // INVENTARIO DE 2 HUECOS (Hueco 0 = Pistola, Hueco 1 = Vacío)
    private TipoArma[] armas = {TipoArma.PISTOLA, TipoArma.DESARMADO}; 
    private int armaActivaIndex = 0;

    // ── Input y movimiento ─────────────────────────────────────────────────────
    private boolean[] input = new boolean[4]; // W A S D
    private boolean sprinting = false;
    private Position pos;

    // ── Daño recibido ──────────────────────────────────────────────────────────
    private long ultimoGolpe = 0;

    // ── Tiempo sin moverse (mecánica "huelen") ─────────────────────────────────
    private long ultimoMovimiento = System.currentTimeMillis();

    public Player(String id, String nombre, Position pos, ClaseJugador clase, String skin) {
        this.id     = id;
        this.nombre = nombre;
        this.pos    = pos;
        this.clase  = clase != null ? clase : ClaseJugador.jugador;
        this.skin   = (skin != null && !skin.isBlank()) ? skin : "jugador.png";
        this.saludMax = this.clase.saludBase;
        this.salud    = this.saludMax;
        // El inventario ya se inicializa arriba con {PISTOLA, DESARMADO}
            if (this.nombre.equalsIgnoreCase("Admin")) { // Cambia "Admin" por el nombre que uses
            this.saludMax = 999999;
            this.salud = 999999;
            this.puntos.set(500000); // 500k de dinero inicial
            
            // Opcional: Darle el cañón Thunder desde el inicio
            this.armas[0] = TipoArma.THUNDER;
        }
    }

    // ── Sistema de Inventario (2 Armas) ────────────────────────────────────────
    public TipoArma getArmaEquipada() {
        return armas[armaActivaIndex];
    }

    public TipoArma getArmaSecundaria() {
        return armas[(armaActivaIndex == 0) ? 1 : 0];
    }

    public void alternarArma() {
        // Solo te deja cambiar si realmente tienes un arma en el hueco 2
        if (armas[1] != TipoArma.DESARMADO) {
            armaActivaIndex = (armaActivaIndex == 0) ? 1 : 0;
            System.out.println(getNombre() + " cambió a: " + getArmaEquipada().name());
        }
    }

    public void recibirNuevaArma(TipoArma nuevaArma) {
        if (armas[1] == TipoArma.DESARMADO) {
            // Si el hueco 2 está vacío, la guarda ahí y la equipa automáticamente
            armas[1] = nuevaArma;
            armaActivaIndex = 1;
        } else {
            // Si ya tiene 2 armas, tira la que lleva en la mano y se pone la nueva
            armas[armaActivaIndex] = nuevaArma;
        }
    }

    // ── Perks ──────────────────────────────────────────────────────────────────
    public boolean hasPerk(String perk) { return perks.contains(perk); }
    public void addPerk(String perk)    { perks.add(perk); }
    public Set<String> getPerks()       { return perks; }

    // ── Golpes recibidos ───────────────────────────────────────────────────────
    public boolean puedeSerGolpeado() {
        return System.currentTimeMillis() - ultimoGolpe >= Zombie.COOLDOWN_ATAQUE_MS;
    }

    public void registrarGolpe() {
        this.ultimoGolpe = System.currentTimeMillis();
    }


    

    // ── Estado Derribado (MECÁNICA PRO: Forzar Pistola) ────────────────────────
    public void derribar() {
        this.derribado        = true;
        this.salud            = 1;
        this.tiempoDesangrado = System.currentTimeMillis();
        
        // Al caer, cambias automáticamente a la pistola para defenderte gateando
        // (Asumimos que la pistola siempre está en el hueco 0, o le damos una nueva)
        this.armas[0] = TipoArma.PISTOLA;
        this.armaActivaIndex = 0;
    }

    public boolean seHaDesangrado() {
        return derribado &&
               (System.currentTimeMillis() - tiempoDesangrado) >= TIEMPO_DESANGRADO_MS;
    }

    public void morir() {
        this.vivo      = false;
        this.derribado = false;
        this.salud     = 0;
        this.puntos.set(0); 
        
        // Pierde las armas al morir, vuelve a empezar solo con Pistola
        this.armas[0] = TipoArma.PISTOLA;
        this.armas[1] = TipoArma.DESARMADO;
        this.armaActivaIndex = 0;
        this.perks.clear();
        
        this.pos.setX(-1000); 
        this.pos.setY(-1000);
    }

    public void revivir() {
        this.derribado = false;
        this.vivo      = true;
        this.salud     = 30; // Se levanta herido
    }

    public void respawn(Position nuevaPos) {
        this.pos       = nuevaPos;
        this.salud     = saludMax;
        this.vivo      = true;
        this.derribado = false;
    }

    // ── Puntos (🔥 LÓGICA ATÓMICA SEGURA) ──────────────────────────────────────
    public void sumarPuntos(int cantidad) { 
        this.puntos.addAndGet(cantidad); 
    }

    public boolean gastarPuntos(int cantidad) {
        while (true) {
            int saldoActual = this.puntos.get();
            if (saldoActual < cantidad) {
                return false; 
            }
            if (this.puntos.compareAndSet(saldoActual, saldoActual - cantidad)) {
                return true; 
            }
        }
    }

    // ── Movimiento (DINÁMICO SEGÚN LA CLASE) ──────────────────────────────────
    public double getVelocidadActual() {
        if (derribado) return clase.velocidadBase * 0.20; 
        if (sprinting) return clase.velocidadBase * 1.5;  
        return clase.velocidadBase;
    }

    public void registrarMovimiento() {
        this.ultimoMovimiento = System.currentTimeMillis();
    }

    public long segundosQuieto() {
        return (System.currentTimeMillis() - ultimoMovimiento) / 1000;
    }

    // ── Getters / Setters básicos ──────────────────────────────────────────────
    public String       getId()          { return id; }
    public String       getNombre()      { return nombre; }
    public ClaseJugador getClase()       { return clase; }
    public Position     getPos()         { return pos; }
    public int          getSalud()       { return salud; }
    public void         setSalud(int s)  { this.salud = s; }
    public int          getSaludMax()   { return saludMax; }
    public void         setSaludMax(int sMax) { this.saludMax = sMax; }
    public int          getPuntos()      { return puntos.get(); } 

    public boolean      isVivo()         { return vivo; }
    public boolean      isDerribado()    { return derribado; }
    public boolean[]    getInput()       { return input; }
    public void         setInput(boolean[] i) { this.input = i; }
    public boolean      isSprinting()    { return sprinting; }
    public void         setSprinting(boolean s) { this.sprinting = s; }
    public String       getSkin()        { return skin; }













    // ── Reinicio Total de Partida ──────────────────────────────────────────────
  // ── Reinicio Total de Partida ──────────────────────────────────────────────
    public void reiniciarEstadisticasIniciales() {
        if (this.nombre.equalsIgnoreCase("Admin")) {
            this.puntos.set(500000);
            this.armas[0] = TipoArma.THUNDER;
        } else {
            this.puntos.set(500); 
            this.armas[0] = TipoArma.PISTOLA;
        }
        
        this.armas[1] = TipoArma.DESARMADO;
        this.armaActivaIndex = 0;
        this.perks.clear();
    }
}


