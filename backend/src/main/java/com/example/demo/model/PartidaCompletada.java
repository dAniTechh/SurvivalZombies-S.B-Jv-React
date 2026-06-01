package com.example.demo.model;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "partidas_completadas")
public class PartidaCompletada {

    @Id
    private String id;
    private String jugador;
    private int rondaAlcanzada;
    private int zombiesEliminados;
    private List<String> armasUtilizadas;
    private long duracionSegundos;
    private LocalDateTime fecha;

    public PartidaCompletada() {
        this.fecha = LocalDateTime.now(); // Autorelleno en constructor
    }

    public PartidaCompletada(String jugador, int rondaAlcanzada, int zombiesEliminados, List<String> armasUtilizadas, long duracionSegundos) {
        this.jugador = jugador;
        this.rondaAlcanzada = rondaAlcanzada;
        this.zombiesEliminados = zombiesEliminados;
        this.armasUtilizadas = armasUtilizadas;
        this.duracionSegundos = duracionSegundos;
        this.fecha = LocalDateTime.now(); // Autorelleno en constructor
    }

    // --- Getters y Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getJugador() { return jugador; }
    public void setJugador(String jugador) { this.jugador = jugador; }
    public int getRondaAlcanzada() { return rondaAlcanzada; }
    public void setRondaAlcanzada(int rondaAlcanzada) { this.rondaAlcanzada = rondaAlcanzada; }
    public int getZombiesEliminados() { return zombiesEliminados; }
    public void setZombiesEliminados(int zombiesEliminados) { this.zombiesEliminados = zombiesEliminados; }
    public List<String> getArmasUtilizadas() { return armasUtilizadas; }
    public void setArmasUtilizadas(List<String> armasUtilizadas) { this.armasUtilizadas = armasUtilizadas; }
    public long getDuracionSegundos() { return duracionSegundos; }
    public void setDuracionSegundos(long duracionSegundos) { this.duracionSegundos = duracionSegundos; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}