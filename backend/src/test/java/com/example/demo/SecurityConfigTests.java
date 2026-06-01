package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
public class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testAccesoRutaPublicaRegistro() throws Exception {
        // La ruta de registro debe ser pública (retorna 200 OK si el cuerpo es correcto)
        String nuevoUsuarioJson = "{\"nombre\":\"jugadortest\",\"password\":\"passwordtest\"}";

        mockMvc.perform(post("/api/usuarios/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(nuevoUsuarioJson))
                .andExpect(status().isOk());
    }

    @Test
    public void testAccesoBloqueadoRutaPrivadaSinToken() throws Exception {
        // La ruta de ranking es privada. Sin token debe devolver 403 Forbidden
        mockMvc.perform(get("/api/usuarios/ranking"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testRegistroDuplicadoDevuelveBadRequest() throws Exception {
        String usuarioJson = "{\"nombre\":\"danitest\",\"password\":\"1234\"}";

        
        mockMvc.perform(post("/api/usuarios/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuarioJson))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/usuarios/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuarioJson))
                .andExpect(status().isBadRequest());
    }


    @Test
    public void testLoginIncorrectoDevuelve401() throws Exception {
        String loginInvalidoJson = "{\"nombre\":\"no_existo\",\"password\":\"clave_falsa\"}";

        mockMvc.perform(post("/api/usuarios/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginInvalidoJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testLoginExitosoDevuelveToken() throws Exception {
        // 1. Nos aseguramos de registrar al usuario "dani_seguro" primero
        String registroJson = "{\"nombre\":\"dani_seguro\",\"password\":\"1234\"}";
        mockMvc.perform(post("/api/usuarios/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registroJson));

        // 2. Intentamos iniciar sesión con ese mismo usuario
        String loginJson = "{\"nombre\":\"dani_seguro\",\"password\":\"1234\"}";
        mockMvc.perform(post("/api/usuarios/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andExpect(status().isOk())
                // Verificamos que el JSON de respuesta contenga una clave llamada "token"
                .andExpect(jsonPath("$.token").exists()); 
    }
}