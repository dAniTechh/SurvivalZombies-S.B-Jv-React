export const keys = { w: false, a: false, s: false, d: false, shift: false };

import { enviarInteraccion } from './network.js';


export function initInput() {
    document.addEventListener('keydown', e => { 
        const key = e.key.toLowerCase();

        if (key === 'f') {
            // Interactuar / abrir puerta
            enviarInteraccion();
            return;

        }

        if (key === 'shift') keys.shift = true;
        else if (key in keys) keys[key] = true;
    });
    
    document.addEventListener('keyup', e => { 
        const key = e.key.toLowerCase();
        if (key === 'shift') keys.shift = false;
        else if (key in keys) keys[key] = false;
    });
}
