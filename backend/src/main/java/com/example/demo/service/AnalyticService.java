package com.example.demo.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.demo.model.PartidaCompletada;
import com.example.demo.repository.PartidaRepository;

@Service
public class AnalyticService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticService.class);
    private final PartidaRepository partidaRepository;

    public AnalyticService(PartidaRepository partidaRepository) {
        this.partidaRepository = partidaRepository;
    }

    public void guardarPartida(String jugador, int ronda, int zombiesEliminados, List<String> armasUsadas, long duracionSegundos) {
        try {
            PartidaCompletada partida = new PartidaCompletada(jugador, ronda, zombiesEliminados, armasUsadas, duracionSegundos);
            partidaRepository.save(partida);
            log.info(" [ANALÍTICAS] Guardada partida del jugador {} (Ronda {}, {} Kills)", jugador, ronda, zombiesEliminados);
        } catch (Exception e) {
            // ── ESCUDO DE TOLERANCIA A FALLOS ──
            // Si MongoDB se cae, logueamos el error pero el juego NO se detiene
            log.error(" [ANALÍTICAS] No se pudo conectar con MongoDB para guardar la partida. Detalles: {}", e.getMessage());
        }
    }
}