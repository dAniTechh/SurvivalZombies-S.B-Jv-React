import { initInput } from './input.js';
// Sustituye tu línea de importación actual por esta:
import { gameState, enviarDisparo, getMySessionId, connect, enviarInteraccion, enviarCambioArma } from './network.js';
import { render, agregarTrazadorBala } from './renderer.js';
// ── Nombres amigables de armas ────────────────────────────────────────────────
const NOMBRES_ARMA = {
    PISTOLA:      'Pistola 9mm',
    ESCOPETA:     'Escopeta de Corredera',
    FUSIL_ASALTO: 'Fusil de Asalto M4',
    SNIPER:       'Rifle de Francotirador',
    THUNDER:      '⚡ Cañón Thunder',
};

// ── Caja Mágica: eventos del servidor ────────────────────────────────────────
window.addEventListener('caja:evento', (e) => {
    const p = e.detail;
    if (p.evento === 'ABRIENDO') {
        mostrarNotificacion(`🎰 ${p.jugador} está abriendo la caja...`, 3200, '#f0c040');
    } else if (p.evento === 'RESULTADO') {
        const nombre = NOMBRES_ARMA[p.arma] ?? p.arma;
        const esMalo  = p.arma === 'PISTOLA';
        mostrarNotificacion(
            esMalo ? `😤 Pistola... qué mala suerte` : `🎁 ¡${nombre}!`,
            3500,
            esMalo ? '#e24b4a' : '#1d9e75'
        );
        if (!p.activa) {
            setTimeout(() => mostrarNotificacion('📦 La caja se ha movido a otro lugar...', 2500, '#888'), 3600);
        }
    }
});

function mostrarNotificacion(texto, duracion = 3000, color = '#fff') {
    let el = document.getElementById('caja-notif');
    if (!el) {
        el = document.createElement('div');
        el.id = 'caja-notif';
        el.style.cssText = `position:fixed;top:20px;left:50%;transform:translateX(-50%);
            padding:10px 22px;border-radius:8px;font-size:15px;font-weight:500;
            background:rgba(0,0,0,0.82);border:1px solid rgba(255,255,255,0.15);
            pointer-events:none;z-index:9999;transition:opacity 0.3s;`;
        document.body.appendChild(el);
    }
    el.textContent = texto;
    el.style.color   = color;
    el.style.opacity = '1';
    clearTimeout(el._timeout);
    el._timeout = setTimeout(() => { el.style.opacity = '0'; }, duracion);
}

// ═══════════════════════════════════════════════════════
//  ESTADO DEL LOBBY
// ═══════════════════════════════════════════════════════
let selectedSkin = 'jugador.png';
let gameStarted  = false;

const canvas = document.getElementById('game');

// ── Selección de skin (llamada desde el HTML) ─────────────────────────────────
window.selectSkin = function(skinName, element) {
    selectedSkin = skinName;
    document.querySelectorAll('.skin-option').forEach(el => el.classList.remove('selected'));
    element.classList.add('selected');
};

function seleccionarSkinPorDefecto() {
    const primera = document.querySelector('.skin-option');
    if (primera) primera.classList.add('selected');
}

// En módulo ES, podemos ejecutar antes de que el DOM esté listo.
// Por eso usamos fallback: si el DOM aún no está preparado, reintentamos en DOMContentLoaded.
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', seleccionarSkinPorDefecto);
} else {
    seleccionarSkinPorDefecto();
}


// ═══════════════════════════════════════════════════════
//  BOTÓN EMPEZAR PARTIDA
// ═══════════════════════════════════════════════════════
function setupStartGameButton() {
    const btn = document.getElementById('start-game-btn');
    if (!btn) return false;

    btn.addEventListener('click', () => {
        const usernameEl = document.getElementById('username');
        const username = usernameEl ? usernameEl.value.trim() : '';

        if (!username) {
            alert('¡Escribe tu nombre antes de empezar!');
            usernameEl?.focus?.();
            return;
        }

        if (gameStarted) return;

        // Ocultamos el menú principal
        document.getElementById('login-screen').style.display = 'none';
        
        // --- NUEVO: OCULTAMOS LA CAJA DEL RANKING ---
        const cajaRanking = document.getElementById('ranking-box');
        if (cajaRanking) cajaRanking.style.display = 'none';
        // --------------------------------------------

        document.getElementById('game-container').classList.remove('hidden');

        gameStarted = true;
        initInput();
        connect(username, selectedSkin);
        render();

        console.log(`✅ Partida iniciada: ${username} | Skin: ${selectedSkin}`);
    });

    return true;
}

if (!setupStartGameButton()) {
    document.addEventListener('DOMContentLoaded', () => setupStartGameButton());
}

window.addEventListener('mousedown', (e) => {
    if (!gameStarted || !gameState.zombies) return;

    const rect   = canvas.getBoundingClientRect();
    const scaleX = canvas.width  / rect.width;
    const scaleY = canvas.height / rect.height;

    // Coordenadas en pantalla
    const screenX = (e.clientX - rect.left) * scaleX;
    const screenY = (e.clientY - rect.top)  * scaleY;

    // Localizar cámara
    const miJugadorId = getMySessionId();
    const miJugador   = gameState.jugadores?.find(j => j.id === miJugadorId)
                     || (gameState.jugadores?.length === 1 ? gameState.jugadores[0] : null);

    const MAP_W = 2400, MAP_H = 1800;
    const halfW = canvas.width / 2, halfH = canvas.height / 2;

    let camX = miJugador ? miJugador.x : halfW;
    let camY = miJugador ? miJugador.y : halfH;
    camX = Math.max(halfW, Math.min(camX, MAP_W - halfW));
    camY = Math.max(halfH, Math.min(camY, MAP_H - halfH));

    const worldX = screenX - halfW + camX;
    const worldY = screenY - halfH + camY;

    // Trazador visual
    const origenX = miJugador?.x ?? camX;
    const origenY = miJugador?.y ?? camY;
    if (typeof agregarTrazadorBala === 'function') {
        agregarTrazadorBala(origenX, origenY, worldX, worldY);
    }

    // Lógica de disparo
    const target = gameState.zombies.find(z =>
        Math.hypot(z.x - worldX, z.y - worldY) < 35
    );

    if (target) {
        const esHeadshot = Math.hypot(target.x - worldX, target.y - worldY) < 12;
        enviarDisparo(target.id, esHeadshot);
    }
});

// ═══════════════════════════════════════════════════════
// INTERACCIONES Y CAMBIO DE ARMA (Teclado)
// ═══════════════════════════════════════════════════════
// input.js ya maneja la tecla F (interactuar)
// Aquí solo se deja la Q para cambio de arma, si no quieres duplicación.
window.addEventListener('keydown', (e) => {
    if (!e.key) return; 
    const pantallaLogin = document.getElementById('login-screen');
    if (pantallaLogin && !pantallaLogin.classList.contains('hidden')) return;
    const key = e.key.toLowerCase();
    if (key === 'q') enviarCambioArma();
});

// ═══════════════════════════════════════════════════════════════════════════
// SISTEMA DE POWER-UPS / DROPS  ·  Black Ops 1 style
// ═══════════════════════════════════════════════════════════════════════════

const POWERUP_CFG = {
    // Drops temporales
    INSTA_KILL:    { icon: '☠',  color: '#CC00FF', bg: '#220033', durLabel: 'INSTA-KILL',    sound: 'kill' },
    DOUBLE_POINTS: { icon: '2×', color: '#FFD700', bg: '#1a1200', durLabel: 'DOBLE PUNTOS',  sound: 'pts'  },
    DEATH_MACHINE: { icon: '⚡', color: '#FF0044', bg: '#1a0005', durLabel: 'DEATH MACHINE', sound: 'death'},
    FIRE_SALE:     { icon: '🔥', color: '#FF6600', bg: '#1a0800', durLabel: 'FIRE SALE',     sound: 'fire' },
    // Drops instantáneos
    NUKE:          { icon: '☢',  color: '#FF5500', bg: '#1a0500', durLabel: '¡NUKE!',        sound: 'nuke' },
    MAX_AMMO:      { icon: '📦', color: '#00FF88', bg: '#001a0d', durLabel: 'MAX AMMO',      sound: 'ammo' },
    CARPENTER:     { icon: '♥',  color: '#FF9900', bg: '#1a0d00', durLabel: 'CARPENTER',     sound: 'carp' },
    FIN_EFECTO:    { icon: '⏱',  color: '#888888', bg: '#111',    durLabel: 'Efecto terminado', sound: null },
};

// ── Notificación visual al recoger un drop ────────────────────────────────
function mostrarNotifPowerup(ev) {
    const cfg  = POWERUP_CFG[ev.evento] || { icon: '?', color: '#fff', bg: '#111', durLabel: ev.evento };
    const por  = ev.por  ? ` · ${ev.por}` : '';
    const dur  = ev.duracion > 0 ? ` (${ev.duracion}s)` : '';
    const kills = ev.kills  > 0  ? ` · ×${ev.kills} zombies` : '';

    // Eliminar notif anterior del mismo tipo
    document.querySelectorAll(`.pup-notif[data-tipo="${ev.evento}"]`).forEach(e => e.remove());

    const el = document.createElement('div');
    el.className = 'pup-notif';
    el.dataset.tipo = ev.evento;
    el.style.cssText = `
        position:fixed; bottom:130px; left:50%; transform:translateX(-50%) scale(0.7);
        background:${cfg.bg}; border:2px solid ${cfg.color}; border-radius:12px;
        color:${cfg.color}; font-family:monospace; font-size:20px; font-weight:bold;
        padding:12px 28px; z-index:9999; text-align:center; white-space:nowrap;
        box-shadow:0 0 30px ${cfg.color}99, 0 0 60px ${cfg.color}44;
        transition: transform 0.2s cubic-bezier(.34,1.56,.64,1), opacity 0.4s;
        pointer-events:none; letter-spacing:1px;
    `;
    el.innerHTML = `<span style="font-size:26px">${cfg.icon}</span>  ${cfg.durLabel}${dur}${kills}${por}`;
    document.body.appendChild(el);

    // Animación entrada
    requestAnimationFrame(() => { el.style.transform = 'translateX(-50%) scale(1)'; });
    setTimeout(() => { el.style.opacity = '0'; el.style.transform = 'translateX(-50%) scale(0.8)'; }, 3200);
    setTimeout(() => el.remove(), 3700);

    // ── Efectos de pantalla por tipo ────────────────────────────────────────
    if (ev.evento === 'NUKE') flashPantalla('#FF5500', 0.6, 1000);
    if (ev.evento === 'INSTA_KILL') flashPantalla('#CC00FF', 0.35, 600);
    if (ev.evento === 'DEATH_MACHINE') flashPantalla('#FF0044', 0.35, 600);
    if (ev.evento === 'CARPENTER') flashPantalla('#FF9900', 0.25, 500);
    if (ev.evento === 'FIRE_SALE') flashPantalla('#FF6600', 0.25, 500);
    if (ev.evento === 'FIN_EFECTO') flashPantalla('#444', 0.15, 300);
}

function flashPantalla(color, opMax, durMs) {
    const el = document.createElement('div');
    el.style.cssText = `
        position:fixed;inset:0;background:${color};opacity:${opMax};z-index:9990;
        pointer-events:none;transition:opacity ${durMs}ms ease-out;
    `;
    document.body.appendChild(el);
    requestAnimationFrame(() => requestAnimationFrame(() => { el.style.opacity = '0'; }));
    setTimeout(() => el.remove(), durMs + 100);
}

// ── HUD de efectos activos con barra de countdown ─────────────────────────
function actualizarHUDPowerups(efectos) {
    if (!efectos) return;

    let bar = document.getElementById('powerup-bar');
    if (!bar) {
        bar = document.createElement('div');
        bar.id = 'powerup-bar';
        bar.style.cssText = `
            position:fixed; top:55px; left:50%; transform:translateX(-50%);
            display:flex; gap:8px; z-index:999; pointer-events:none; flex-wrap:wrap;
            justify-content:center; max-width:600px;
        `;
        document.body.appendChild(bar);
    }

    const activos = [
        { key: 'instakill',    msKey: 'instakillMs',    tipo: 'INSTA_KILL'    },
        { key: 'doblesPuntos', msKey: 'doblesPuntosMs', tipo: 'DOUBLE_POINTS' },
        { key: 'deathMachine', msKey: 'deathMachineMs', tipo: 'DEATH_MACHINE' },
        { key: 'fireSale',     msKey: 'fireSaleMs',     tipo: 'FIRE_SALE'     },
    ].filter(e => efectos[e.key]);

    // Actualizar o crear cada píldora de efecto activo
    const vistos = new Set();
    activos.forEach(({ key, msKey, tipo }) => {
        vistos.add(tipo);
        const cfg    = POWERUP_CFG[tipo];
        const msLeft = efectos[msKey] || 0;
        const segs   = Math.ceil(msLeft / 1000);
        const frac   = Math.min(1, msLeft / (tipo === 'INSTA_KILL' ? 20000 : 30000));
        const urgente = segs <= 5;

        let pill = bar.querySelector(`[data-efecto="${tipo}"]`);
        if (!pill) {
            pill = document.createElement('div');
            pill.dataset.efecto = tipo;
            pill.style.cssText = `
                display:flex; align-items:center; gap:6px;
                background:${cfg.bg}; border:2px solid ${cfg.color};
                border-radius:8px; padding:4px 12px; font-family:monospace;
                font-weight:bold; font-size:13px; color:${cfg.color};
                box-shadow:0 0 10px ${cfg.color}88; min-width:130px;
                flex-direction:column; overflow:hidden;
                transition: box-shadow 0.2s;
            `;
            pill.innerHTML = `
                <div style="display:flex;align-items:center;gap:6px;width:100%;justify-content:space-between">
                    <span class="pup-icon" style="font-size:16px">${cfg.icon}</span>
                    <span class="pup-label">${cfg.durLabel}</span>
                    <span class="pup-segs" style="opacity:0.85;font-size:12px"></span>
                </div>
                <div class="pup-bar-bg" style="width:100%;height:4px;background:rgba(255,255,255,0.15);border-radius:2px;margin-top:2px">
                    <div class="pup-bar-fill" style="height:100%;border-radius:2px;background:${cfg.color};transition:width 0.3s linear;box-shadow:0 0 6px ${cfg.color}"></div>
                </div>
            `;
            bar.appendChild(pill);
        }

        pill.querySelector('.pup-segs').textContent  = `${segs}s`;
        pill.querySelector('.pup-bar-fill').style.width = `${frac * 100}%`;
        pill.style.boxShadow = urgente
            ? `0 0 20px ${cfg.color}cc`
            : `0 0 10px ${cfg.color}88`;
        if (urgente) pill.style.borderColor = '#FF4444';
        else         pill.style.borderColor = cfg.color;
    });

    // Eliminar píldoras de efectos que ya terminaron
    bar.querySelectorAll('[data-efecto]').forEach(el => {
        if (!vistos.has(el.dataset.efecto)) el.remove();
    });
}

// ── Eventos ───────────────────────────────────────────────────────────────
window.addEventListener('powerup:event', (e) => {
    const ev = e.detail;
    console.log('[POWERUP]', ev.evento, ev);
    mostrarNotifPowerup(ev);
});

// Actualizar HUD cada 200ms desde gameState
setInterval(() => {
    if (typeof gameState !== 'undefined' && gameState.efectos) {
        actualizarHUDPowerups(gameState.efectos);
    }
}, 200);