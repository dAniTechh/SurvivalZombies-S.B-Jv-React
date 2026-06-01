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
        return;
    }

    if (p.evento === 'RESULTADO') {
        const nombre = NOMBRES_ARMA[p.arma] ?? p.arma;
        const esMalo  = p.arma === 'PISTOLA';

        mostrarNotificacion(
            esMalo ? `😤 Pistola... qué mala suerte` : `🎁 ¡${nombre}!`,
            3500,
            esMalo ? '#e24b4a' : '#1d9e75'
        );

        if (!p.activa) {
            setTimeout(
                () => mostrarNotificacion('📦 La caja se ha movido a otro lugar...', 2500, '#888'),
                3600
            );
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
    el.style.color = color;
    el.style.opacity = '1';

    clearTimeout(el._timeout);
    el._timeout = setTimeout(() => { el.style.opacity = '0'; }, duracion);
}

