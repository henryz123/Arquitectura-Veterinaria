# P1B — Auditoría final del ecosistema

**Fecha de verificación:** 2026-10-21
**Alcance:** lectura, comprobación de comandos no mutantes y revisión de archivos locales. No se instalaron herramientas, no se ejecutaron hooks ni launchers de Impeccable, no se conectó GitHub ni se abrieron datos de PostgreSQL.

## Inventario

| Componente | Versión / ubicación | Procedencia y capacidad verificada | Estado y acción pendiente |
|---|---|---|---|
| Git | 2.51.0.windows.1 | `git` operativo; sin remoto | apto para documentación |
| Java | JRE 1.8.0_51, `C:\ProgramData\Oracle\Java\javapath\java.exe` | `java -version` funciona | **bloqueador:** no es JDK 21; localizar/instalar solo con autorización posterior |
| Maven | no disponible en `PATH` | no hay `mvn`, wrapper ni `pom.xml` | **bloqueador G4** |
| Node/npm | 22.20.0 / 10.9.3, `C:\Program Files\nodejs` | comandos operativos | compatible con Angular 21, pero no hay workspace |
| Angular CLI | no disponible | no existe `ng`, `angular.json` ni `package.json` de producto | **bloqueador G4** |
| Python | 3.12.10, instalación Python312 | comando operativo | apto para utilidades permitidas |
| PostgreSQL | 18.6, `C:\Program Files\PostgreSQL\18\bin\psql.exe` | servicio `postgresql-x64-18` activo; no se abrió conexión | apto en potencia; agregar ruta solo con permiso |
| Docker/Compose | no disponible | no hay binario ni daemon | no se puede validar Compose/local integration |
| Codex | aplicación/runtimes activos; CLI no invocable en sandbox | `codex --version` recibe acceso denegado | no se afirma versión CLI; capacidades de esta sesión sí son reales |
| ECC Affaan Mustafa | plugin nativo `ecc@ecc`, caché 2.2.3 | habilitado en `C:\Users\HNR05\.codex\config.toml`; skills detectadas por sesión | apto como fuente de skills; agentes no se presumen |
| Agent Browser | 0.27.0, npm global | ejecutable responde versión | paquete apto; Chromium/E2E pendiente con app autorizada |
| UI UX Pro Max | 2.15.0, npm global | `uipro.cmd` responde versión; skill local | apto para diseño futuro; PowerShell requiere wrapper `.cmd` |
| Impeccable | skill 4.7.0; launcher 0.1.16 | launcher local analizado sin invocarlo | **incompleto:** motor ausente; no ejecutar |
| Design Motion Principles | skill global | disponible en la sesión | apto como guía documental/diseño futuro |
| Emil Skills | sin copia independiente | cobertura parcial: Design Motion Principles y `animation-vocabulary` | opcional ausente |
| Taste Skill | ausente | no hay skill/comando detectable | opcional; no bloquea V1 |
| img2threejs | ausente | no hay paquete/skill/comando detectable | opcional; no bloquea V1 |

## ECC y orquestación

ECC de Affaan Mustafa es la única ruta ECC prevista. La sesión detecta skills de arquitectura, ADR, contrato, API, PostgreSQL, planificación, Angular, E2E y accesibilidad. La caché contiene plantillas de `explorer`, `reviewer` y `docs-researcher`; son plantillas, no agentes registrados/callable comprobados. El subagente `planner` usado para la revisión documental es una capacidad real del runtime, pero no se presenta como agente ECC.

Regla de uso: un responsable por archivos compartidos; revisores en solo lectura; ningún agente escribe migraciones, contratos compartidos o configuración de seguridad en paralelo. El contexto de la tarea debe declarar fuente V1, límites, archivos, pruebas, evidencia y rollback.

## Impeccable y hooks

- Ruta activa esperada: `.codex/hooks.json`; **no existe**, por lo que no hay hook cargable en el proyecto.
- Respaldo: `.codex/backup/hooks.json.p1a.bak`, 1084 bytes, SHA-256 `57EC2302C0EACFD0DE2E7940A9584E7F8221E9B555C5A23FD06DD5A6B156188F`.
- El launcher intentaría descargar el motor ausente si se invocara. No se ha permitido tráfico, descarga ni inicialización automática.
- La documentación upstream confirma que Codex usa `.codex/hooks.json`, requiere aprobación explícita en `/hooks` y que el motor puede descargarse al faltar el binario. Este dato ratifica el aislamiento; no autoriza activar el flujo.

### Procedimiento de restauración futura

1. Obtener aprobación expresa para diagnosticar/instalar el motor desde una procedencia y licencia verificadas.
2. Verificar versión, hash, permisos Windows, compatibilidad con el launcher y que una ejecución de diagnóstico no descargue componentes no revisados.
3. Ejecutar únicamente un smoke test reversible sobre una copia o árbol temporal, sin editar documentación/producto.
4. Comparar el hash del respaldo y restaurar `hooks.json` desde esa copia solo si el test es correcto y hay autorización específica.
5. Confirmar por ruta y contenido que Codex carga el hook esperado; conservar un procedimiento de aislamiento inverso.

Hasta cumplir todos los pasos, Impeccable permanece aislado. P1B no bloquea G2/G3 documental ni desarrollo futuro que no dependa de ese motor.

## Terceros, secretos y Git

`.agents/` contiene 234 archivos (6,508,827 bytes) y ocho skills de diseño. No hay manifiesto de procedencia en esa raíz; solo se encontró una licencia explícita, Apache-2.0, para `ui-styling`. Por eso se excluye del staging y ahora se ignora por defecto hasta revisión de origen/licencias. El uso local en modo lectura no transfiere su titularidad al proyecto.

La revisión de rutas rastreadas no detectó `.env`, claves privadas ni patrones sospechosos. El repositorio sigue siendo documental: no hay `pom.xml`, wrapper, Angular workspace, Dockerfile, Compose, Flyway ni migraciones SQL. `.codex/backup/` permanece ignorado.

## Resultado P1B

La documentación G2/G3 puede completarse. El inicio de G4 está bloqueado de forma legítima por JDK 21, Maven, Angular CLI y Docker ausentes, y por la autorización separada del primer incremento. No se recomienda instalar por presión de calendario.

## Referencias externas consultadas

- [ECC plugin nativo 2.2.3](https://github.com/affaan-m/ECC/blob/main/.codex-plugin/plugin.json)
- [Impeccable — instalación y hooks Codex](https://github.com/pbakaus/impeccable)
- [Agent Browser — instalación de Chrome for Testing](https://github.com/vercel-labs/agent-browser)
