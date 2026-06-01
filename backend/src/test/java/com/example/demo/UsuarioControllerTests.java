package com.example.demo;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.demo.controller.UsuarioController;
import com.example.demo.model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.security.JwtService;

public class UsuarioControllerTests {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtService jwtService; 

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UsuarioController usuarioController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testRegistrarUsuarioExitoso() {
        Usuario nuevoUsuario = new Usuario("Dani", "1234");
        
        when(usuarioRepository.findByNombre("Dani")).thenReturn(null);
        when(passwordEncoder.encode("1234")).thenReturn("$2a$10$cifrado_seguro");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(nuevoUsuario);

        ResponseEntity<String> respuesta = usuarioController.registrarUsuario(nuevoUsuario);

        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals("Usuario registrado exitosamente", respuesta.getBody());
        
        verify(usuarioRepository).save(nuevoUsuario);
    }

    @Test
    public void testRegistrarUsuarioDuplicadoFalla() {
        Usuario nuevoUsuario = new Usuario("Dani", "1234");
        Usuario usuarioExistenteEnBD = new Usuario("Dani", "$2a$10$hash_viejo");

        when(usuarioRepository.findByNombre("Dani")).thenReturn(usuarioExistenteEnBD);

        ResponseEntity<String> respuesta = usuarioController.registrarUsuario(nuevoUsuario);

        assertEquals(400, respuesta.getStatusCode().value());
        assertEquals("El nombre de usuario ya está registrado.", respuesta.getBody());
    }

    // ── NUEVO TEST: LOGIN EXITOSO (Usa las variables) ──
    @Test
    public void testIniciarSesionExitoso() {
        // GIVEN
        Usuario datosLogin = new Usuario("Dani", "1234");
        Usuario usuarioEnBD = new Usuario("Dani", "$2a$10$hash_cifrado");

        // Simulamos la búsqueda en BD, el matches de contraseña y la generación del token
        when(usuarioRepository.findByNombre("Dani")).thenReturn(usuarioEnBD);
        when(passwordEncoder.matches("1234", "$2a$10$hash_cifrado")).thenReturn(true);
        when(jwtService.generateToken("Dani")).thenReturn("token_mock_de_prueba_123");

        // WHEN (Ejecutamos el login)
        ResponseEntity<?> respuesta = usuarioController.iniciarSesion(datosLogin);

        // THEN (Verificaciones)
        assertEquals(200, respuesta.getStatusCode().value());
        
        // Comprobamos que la respuesta contenga el token generado
        Map<?, ?> cuerpoResponse = (Map<?, ?>) respuesta.getBody();
        assertEquals("token_mock_de_prueba_123", cuerpoResponse.get("token"));

        // Verificamos que se llamó al generador de tokens de JwtService y al publicador de eventos
        verify(jwtService).generateToken("Dani");
        verify(eventPublisher).publishEvent(any()); // Verifica que se disparó el evento de auditoría
    }
}