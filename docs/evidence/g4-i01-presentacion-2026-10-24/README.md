# Evidencia local - G4-I01 - presentacion 2026-10-24

Estas capturas se generaron el 2026-10-23 por
`frontend/e2e/g4-i01-authenticated.e2e.mjs` contra los servicios locales Spring
Boot y Angular. El recorrido obtuvo `G4_I01_AUTHENTICATED_BROWSER_E2E=PASS` y
cubrio login STAFF Aurora, ruta protegida, perfil propio, aislamiento frente a
Bosque, logout, CSRF y operaciones SaaS por API.

Las capturas se guardaron antes de ingresar el formulario o despues de navegar
a una pantalla protegida. No contienen contrasenas, tokens, cookies, hashes ni
datos reales.

| Archivo | SHA-256 | Contenido |
|---|---|---|
| `01-login-aurora.png` | `C9CBA5C4AD75D943D72584782AC8C45DC3D7D5DA9F49035AAD02386B71F2F80B` | Login Aurora sin campos rellenados. |
| `02-workspace-aurora.png` | `6CB7003784BB52ABC5B1723EC0F63FFF51BA893A14EC2E162506EE9825410E2B` | Espacio protegido de Aurora. |
| `03-workspace-bosque.png` | `6401B8B288CDB1F2D31942CB2AA9ECC2D65E3447739B4E970B0A8676C3D28E2D` | Espacio protegido de Bosque. |

Abra los PNG directamente desde esta carpeta durante la contingencia. No se
genero video: Agent Browser cerro la sesion de grabacion y reporto `ffmpeg`
ausente; no se instalo software durante esta tarea.
