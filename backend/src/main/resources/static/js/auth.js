const btnRegistro = document.getElementById('register-btn');
const btnLogin = document.getElementById('login-btn');
const btnStartGame = document.getElementById('start-game-btn');
const inputUsername = document.getElementById('username');
const inputPassword = document.getElementById('password');

// --- EVENTO: Clic en Registrarse (Optimizado con respuestas dinámicas) ---
btnRegistro.addEventListener('click', async () => {
    const datos = {
        nombre: inputUsername.value,
        password: inputPassword.value
    };

    try {
        const respuesta = await fetch('/api/usuarios/registro', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(datos)
        });

        if (respuesta.ok) {
            alert("¡Usuario registrado con éxito! Ahora puedes iniciar sesión.");
        } else {
            
            const mensajeError = await respuesta.text();
            alert(mensajeError);
        }
    } catch (error) {
        console.error("Error de red:", error);
        alert("No se pudo conectar con el servidor. Verifica tu conexión.");
    }
});

// --- EVENTO: Clic en Iniciar Sesión ---
btnLogin.addEventListener('click', async () => {
    const datos = {
        nombre: inputUsername.value,
        password: inputPassword.value
    };

    try {
        const respuesta = await fetch('/api/usuarios/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(datos)
        });

        if (respuesta.ok) {
            const data = await respuesta.json(); // Obtenemos el objeto { "token": "..." }
            
            // Guardamos el token para peticiones futuras y el nombre para el juego
            sessionStorage.setItem('jwtToken', data.token); 
            sessionStorage.setItem('jugadorActual', inputUsername.value);
            
            alert("¡Bienvenido, " + inputUsername.value + "!");
            
            btnRegistro.style.display = 'none';
            btnLogin.style.display = 'none';
            btnStartGame.classList.remove('hidden');
            
            // Recargamos el ranking ahora que tenemos permisos
            cargarRanking();
        } else {
            alert("Credenciales incorrectas.");
        }
    } catch (error) {
        console.error("Error de login:", error);
    }
});

// --- Cargar Ranking (Ahora requiere autenticación) ---
export async function cargarRanking() {
    const token = sessionStorage.getItem('jwtToken');
    
    if (!token) return;

    try {
   
        const respuesta = await fetch('/api/usuarios/ranking', {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            }
        });

       

        if (respuesta.ok) {
            const datosRanking = await respuesta.json();
            const cuerpoTabla = document.getElementById('ranking-cuerpo');
            cuerpoTabla.innerHTML = ''; 

            datosRanking.forEach((registro, index) => {
            
                const nombreJugador = registro.nombre;
                const rondaAlcanzada = registro.rondaMaxima;

               

                cuerpoTabla.innerHTML += `
                    <tr>
                        <td style="color: #ff0000; font-weight: bold;">${index + 1}</td>
                        <td>${nombreJugador}</td>
                        <td style="text-align: right; padding-right: 10px;">${rondaAlcanzada}</td>
                    </tr>
                `;
            });
        }
    } catch (error) {
        console.error("No se pudo obtener el ranking:", error);
    }
}

// Ejecutamos la carga automáticamente al entrar en la web
cargarRanking();