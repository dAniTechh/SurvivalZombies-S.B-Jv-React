package com.example.demo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    // Escucha cuando un usuario se autentica con éxito (ej: al validar su token en el filtro)
    @EventListener
    public void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        log.info(" [AUDITORÍA] Acceso AUTORIZADO para el usuario: {}", username);
    }

    // Escucha cuando hay un intento fallido por malas credenciales
    @EventListener
    public void handleAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        log.warn(" [AUDITORÍA] Intento de acceso DENEGADO para el usuario: {}", username);
    }
}