// Overlay cinematográfico de rondas
import { enviarReinicio } from './network.js';

let overlayEl = null;
let timerEl = null;
let restartBtnEl = null;
let titleEl = null;
let hideTimeout = null;
let isVisible = false;
let pauseRestante = 0;
let modoGameOver = false;

function ensureEls() {
  if (!overlayEl)      overlayEl      = document.getElementById('round-overlay');
  if (!timerEl)        timerEl        = document.getElementById('round-overlay-timer');
  if (!restartBtnEl)   restartBtnEl   = document.getElementById('round-overlay-restart');
  if (!titleEl)        titleEl        = overlayEl?.querySelector('.round-overlay-title');
}

function setVisible(visible) {
  ensureEls();
  if (!overlayEl) return;

  if (visible) {
    overlayEl.classList.remove('oculto', 'hide');
    overlayEl.classList.add('show');
    overlayEl.setAttribute('aria-hidden', 'false');
    isVisible = true;
  } else {
    overlayEl.classList.remove('show');
    overlayEl.classList.add('hide');
    overlayEl.setAttribute('aria-hidden', 'true');
    isVisible = false;
    setTimeout(() => {
      if (!overlayEl) return;
      overlayEl.classList.remove('hide');
      overlayEl.classList.add('oculto');
    }, 420);
  }
}

function setModoGameOver(activo) {
  ensureEls();
  modoGameOver = activo;
  if (timerEl)      timerEl.style.display      = activo ? 'none'         : '';
  if (restartBtnEl) restartBtnEl.style.display  = activo ? 'inline-block' : 'none';
  if (titleEl)      titleEl.textContent          = activo ? 'GAME OVER'   : 'RONDA SUPERADA';
}

function updateTimerText() {
  ensureEls();
  if (!timerEl) return;
  timerEl.textContent = String(Math.max(0, Math.ceil(pauseRestante)));
}

function startCountdownFrom(restanteSeconds) {
  pauseRestante = Number(restanteSeconds) || 0;
  updateTimerText();

  if (hideTimeout) { clearInterval(hideTimeout); hideTimeout = null; }

  const start = performance.now();
  const durationMs = pauseRestante * 1000;

  hideTimeout = setInterval(() => {
    if (!isVisible || modoGameOver) return;
    const elapsed = performance.now() - start;
    pauseRestante = Math.max(0, (durationMs - elapsed) / 1000);
    updateTimerText();
    if (pauseRestante <= 0.01) {
      clearInterval(hideTimeout);
      hideTimeout = null;
      setVisible(false);
    }
  }, 100);
}

// Botón de reinicio: lo enganchamos al DOM una sola vez
window.addEventListener('DOMContentLoaded', () => {
  ensureEls();
  if (restartBtnEl) {
    restartBtnEl.addEventListener('click', () => {
      enviarReinicio();
      // El servidor responderá con REINICIO → ocultamos el overlay ahí
    });
  }
});

window.addEventListener('round:event', (ev) => {
  const { evento, restante } = ev.detail || {};

  if (evento === 'CUENTA_ATRAS') {
    setModoGameOver(false);
    setVisible(true);
    startCountdownFrom(restante);
    return;
  }

  if (evento === 'RONDA_COMPLETADA') {
    setModoGameOver(false);
    setVisible(true);
    startCountdownFrom(10);
    return;
  }

  if (evento === 'GAME_OVER') {
    // Mostramos la pantalla de game over con botón de reinicio
    setModoGameOver(true);
    setVisible(true);
    if (hideTimeout) { clearInterval(hideTimeout); hideTimeout = null; }
    return;
  }

  if (evento === 'REINICIO') {
    // El servidor ha confirmado el reinicio: ocultamos overlay y restauramos estado
    setModoGameOver(false);
    setVisible(false);
    return;
  }

  if (evento === 'RONDA_INICIO') {
    // Al empezar ronda ocultamos el overlay si sigue visible
    setModoGameOver(false);
    setVisible(false);
    return;
  }
});
