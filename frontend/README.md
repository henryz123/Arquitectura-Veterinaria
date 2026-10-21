# Frontend Angular

Aplicación Angular 21 del producto definitivo. G4-I00 contiene exclusivamente el acceso laboral por tenant, el contexto de sesión y el perfil propio; no simula ni declara completados módulos B02–B06.

## Comandos

Con dependencias instaladas mediante Corepack/pnpm:

```powershell
node_modules\.bin\ng.cmd build
node_modules\.bin\ng.cmd test --watch=false
node_modules\.bin\ng.cmd serve --proxy-config proxy.conf.json
```

El proxy de desarrollo dirige `/api` a `http://localhost:8080`. No hay mocks de autenticación ni credenciales versionadas. La prueba de integración real requiere el backend conectado a la base exclusiva descrita en `../docs/runbooks/G4-I00_ENTORNO_LOCAL.md`.

Los scripts nativos transitorios permanecen bloqueados mediante `pnpm-workspace.yaml`; no se habilitan de forma automática.
