import { keys } from './input.js';
import { cargarRanking } from './auth.js';

export let gameState = {};
let stompClient = null;
let mySessionId = null;
let ultimoEstadoTeclas = null;
let ultimoSprintCache = null;

// Variable de control para no bombardear al servidor al morir
let puntuacionGuardada = false;
let rondaActualGlobal = 0;

export function getMySessionId() { return mySessionId; }

// ═══════════════════════════════════════════════════════════════
//  PUNTO DE ENTRADA ÚNICO
// ═══════════════════════════════════════════════════════════════
export function connect(nombre, skin) {
    const socket = new SockJS('/nexus-zombies');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    // Recuperamos el token para enviarlo en la conexión WS y en los futuros fetches
    const token = sessionStorage.getItem('jwtToken');
    
    // Cabecera de autenticación para el protocolo STOMP
    const headers = {
        'Authorization': 'Bearer ' + token
    };

    stompClient.connect(headers, () => {
        console.log('[WS] ✅ Conectado al servidor con seguridad');

        puntuacionGuardada = false;
        rondaActualGlobal = 0;

        // Subscripciones
        stompClient.subscribe('/user/queue/init', (msg) => {
            const payload = JSON.parse(msg.body);
            mySessionId = payload.sessionId || null;
            console.log('[WS] 🔑 sessionId asignado:', mySessionId);
        });

        stompClient.subscribe('/user/queue/aviso', (msg) => {
            const p = JSON.parse(msg.body);
            console.warn(`[ECONOMÍA] ${p.tipo}: Cuesta $${p.coste}, tienes $${p.tienes}`);
        });

        // Enviamos el join inicial
        stompClient.send('/app/join', {}, JSON.stringify({ nombre, skin }));

        stompClient.subscribe('/topic/kill', (msg) => {
            window.dispatchEvent(new CustomEvent('hud:kill', { detail: JSON.parse(msg.body) }));
        });
        
        stompClient.subscribe('/topic/hits', (msg) => {
            window.dispatchEvent(new CustomEvent('hud:hits', { detail: JSON.parse(msg.body) }));
        });
        
        stompClient.subscribe('/topic/gamestate', (msg) => {
            gameState = JSON.parse(msg.body);
        });
        
        stompClient.subscribe('/topic/ronda', (msg) => {
            const detalleRonda = JSON.parse(msg.body);
            rondaActualGlobal = detalleRonda.ronda ?? rondaActualGlobal;

            if (detalleRonda.evento === 'GAME_OVER') {
                if (!puntuacionGuardada) {
                    puntuacionGuardada = true;
                    const rondaFinal = detalleRonda.ronda ?? rondaActualGlobal;
                    console.log(`💀 GAME OVER en ronda ${rondaFinal}. Guardando récord...`);

                    // Llamada REST autenticada
                    fetch(`/api/usuarios/actualizar-ronda?nombre=${encodeURIComponent(nombre)}&rondaAlcanzada=${rondaFinal}`, {
                        method: 'POST',
                        headers: {
                            'Authorization': 'Bearer ' + token, // IMPORTANTE: Autorización aquí
                            'Content-Type': 'application/json'
                        }
                    })
                    .then(r => {
                        if (r.ok) {
                            console.log('✅ Récord guardado en el servidor');
                            cargarRanking(); // Refrescamos ranking tras guardar
                        } else {
                            console.error('❌ Error al guardar:', r.statusText);
                        }
                    })
                    .catch(err => console.error('❌ Error de red:', err));
                }
                puntuacionGuardada = false;
                rondaActualGlobal = 0;
            }

            window.dispatchEvent(new CustomEvent('round:event', { detail: detalleRonda }));
        });
        
        stompClient.subscribe('/topic/puertas', (msg) => {
            const p = JSON.parse(msg.body);
            console.log('[PUERTA]', p.evento, p.puertaId, '→', p.nombreZonaDestino);
        });
        
        stompClient.subscribe('/topic/powerup', (msg) => {
            const ev = JSON.parse(msg.body);
            window.dispatchEvent(new CustomEvent('powerup:event', { detail: ev }));
        });

    }, (error) => {
        console.error('[WS] ❌ Error de conexión:', error);
    });

    // Loop de envío de input
    setInterval(() => {
        if (!stompClient?.connected) return;
        const payload = JSON.stringify(keys);
        if (payload !== ultimoEstadoTeclas) {
            ultimoEstadoTeclas = payload;
            stompClient.send('/app/input', {}, payload);
        }
        const sprintPayload = JSON.stringify({ activo: !!keys.shift });
        if (sprintPayload !== ultimoSprintCache) {
            ultimoSprintCache = sprintPayload;
            stompClient.send('/app/sprint', {}, sprintPayload);
        }
    }, 32);
}

// ── Acciones del jugador ──────────
export function enviarDisparo(zombieId, headshot) {
    if (!stompClient?.connected) return;
    stompClient.send('/app/shoot', {}, JSON.stringify({ zombieId, headshot }));
}

export function enviarInteraccion() {
    if (!stompClient?.connected) return;
    stompClient.send('/app/interact', {}, JSON.stringify({}));
}

export function enviarCambioArma() {
    if (!stompClient?.connected) return;
    stompClient.send('/app/switchWeapon', {}, JSON.stringify({}));
}

export function enviarReinicio() {
    if (!stompClient?.connected) return;
    stompClient.send('/app/restart', {}, JSON.stringify({}));
}