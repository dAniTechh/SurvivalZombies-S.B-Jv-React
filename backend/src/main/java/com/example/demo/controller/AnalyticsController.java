package com.example.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.PartidaCompletada;
import com.example.demo.repository.PartidaRepository;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final PartidaRepository partidaRepository;

    public AnalyticsController(PartidaRepository partidaRepository) {
        this.partidaRepository = partidaRepository;
    }

    @GetMapping("/ranking")
    public ResponseEntity<List<PartidaCompletada>> obtenerTop10() {
        return ResponseEntity.ok(partidaRepository.findTop10ByOrderByRondaAlcanzadaDesc());
    }

    @GetMapping("/historial/{jugador}")
    public ResponseEntity<List<PartidaCompletada>> obtenerHistorial(@PathVariable String jugador) {
        return ResponseEntity.ok(partidaRepository.findByJugador(jugador));
    }
}