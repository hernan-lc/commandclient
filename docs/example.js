const BASE_URL = 'http://localhost:8080';
const TOKEN = 'tu-token-secreto'; // Cambia esto por tu token real
async function ejecutarComando() {
  try {
    const response = await fetch(`${BASE_URL}/api/execute`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${TOKEN}` // Quitar si la auth está desactivada
      },
      body: JSON.stringify({
        command: "/seed"
      })
    });

    const data = await response.json();
    
    if (!response.ok) {
      throw new Error(data.error || 'Error al ejecutar el comando');
    }

    console.log('Resultado:', data);
  } catch (error) {
    console.error('Error:', error.message);
  }
}
ejecutarComando()