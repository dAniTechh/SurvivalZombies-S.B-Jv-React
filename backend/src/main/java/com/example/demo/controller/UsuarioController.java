package com.example.demo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher; 
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping; 
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.security.JwtService;

@RestController
@RequestMapping("/api/usuarios") 
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher; 

    // Constructor con todas las dependencias inyectadas por Spring
    public UsuarioController(UsuarioRepository usuarioRepository, 
                             JwtService jwtService, 
                             PasswordEncoder passwordEncoder,
                             ApplicationEventPublisher eventPublisher) {
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/registro")
    public ResponseEntity<String> registrarUsuario(@RequestBody Usuario nuevoUsuario) {
        // Que el nombre no llegue vacío o nulo
        if (nuevoUsuario.getNombre() == null || nuevoUsuario.getNombre().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El nombre de usuario no puede estar vacío.");
        }

        //comprobamos si el nombre ya existe en la Base de Datos
        Usuario usuarioExistente = usuarioRepository.findByNombre(nuevoUsuario.getNombre());
        if (usuarioExistente != null) {
            // Devolvemos un código 400 (Bad Request) con un mensaje claro para tu JS
            return ResponseEntity.badRequest().body("El nombre de usuario ya está registrado.");
        }

        //Si todo está bien, ciframos la contraseña usando BCrypt antes de persistir
        nuevoUsuario.setPassword(passwordEncoder.encode(nuevoUsuario.getPassword()));
        usuarioRepository.save(nuevoUsuario);
        return ResponseEntity.ok("Usuario registrado exitosamente");
    }

    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(@RequestBody Usuario datosLogin) {
        // Buscamos al usuario por nombre en la base de datos
        Usuario usuarioEncontrado = usuarioRepository.findByNombre(datosLogin.getNombre());
        
        // Comparamos el hash de la base de datos con la contraseña en texto plano recibida
        if (usuarioEncontrado != null && passwordEncoder.matches(datosLogin.getPassword(), usuarioEncontrado.getPassword())) {
            
            // Generamos el token de sesión stateless
            String token = jwtService.generateToken(usuarioEncontrado.getNombre());
            
            // ── AUDITORÍA: Disparamos evento de éxito (Sección 4.4 del PDF) ──
            UsernamePasswordAuthenticationToken authToken = 
                    new UsernamePasswordAuthenticationToken(usuarioEncontrado.getNombre(), null, null);
            eventPublisher.publishEvent(new AuthenticationSuccessEvent(authToken));

            // Devolvemos el token al cliente en formato JSON
            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            return ResponseEntity.ok(response);
            
        } else {
            // ── AUDITORÍA: Disparamos evento de fallo (Sección 4.4 del PDF) ──
            UsernamePasswordAuthenticationToken authToken = 
                    new UsernamePasswordAuthenticationToken(datosLogin.getNombre(), null, null);
            eventPublisher.publishEvent(new AuthenticationFailureBadCredentialsEvent(
                    authToken, 
                    new AuthenticationException("Credenciales inválidas") {}
            ));
            
            return ResponseEntity.status(401).body("Credenciales incorrectas");
        }
    }

    @PostMapping("/actualizar-ronda")
    public ResponseEntity<String> actualizarRonda(@RequestParam String nombre, @RequestParam int rondaAlcanzada) {
        Usuario jugador = usuarioRepository.findByNombre(nombre);
        if (jugador != null) {
            // Comprobamos si la nueva ronda supera el récord guardado
            if (rondaAlcanzada > jugador.getRondaMaxima()) {
                jugador.setRondaMaxima(rondaAlcanzada);
                usuarioRepository.save(jugador);
                return ResponseEntity.ok("¡Nuevo récord guardado!");
            }
            return ResponseEntity.ok("No has superado tu récord anterior.");
        }
        return ResponseEntity.badRequest().body("Usuario no encontrado.");
    }

    @GetMapping("/ranking")
    public List<Usuario> obtenerTop3() {
        return usuarioRepository.findTop3ByOrderByRondaMaximaDesc();
    }
}