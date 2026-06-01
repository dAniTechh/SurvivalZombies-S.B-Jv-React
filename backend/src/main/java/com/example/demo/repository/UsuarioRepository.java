package com.example.demo.repository;
 
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.model.Usuario;
 
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
 
    //  SELECT * FROM usuarios WHERE nombre = ?
    Usuario findByNombre(String nombre);
    List<Usuario> findTop3ByOrderByRondaMaximaDesc();
}

