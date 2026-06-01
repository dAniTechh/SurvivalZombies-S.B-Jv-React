package com.example.demo.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.demo.model.PartidaCompletada;

public interface PartidaRepository extends MongoRepository<PartidaCompletada, String> {
    
    // Top 10 de mejores partidas para el ranking global de analíticas
    List<PartidaCompletada> findTop10ByOrderByRondaAlcanzadaDesc();
    
    // Historial de un jugador específico
    List<PartidaCompletada> findByJugador(String jugador);
}