# Blueprint de ecosistema — Arquitectura Veterinaria

## Propósito

Preparar un entorno reproducible de ingeniería, automatización y diseño para definir y construir el producto veterinario, con calidad, accesibilidad y seguridad desde el inicio.

## Conciliación G1-A — 21 de octubre de 2026

Esta sección prevalece sobre las afirmaciones provisionales del bootstrap que aparecen más abajo, las cuales se conservan como historial.

- La especificación funcional V1 de B01 a B06 está cerrada e inmutable en "arquitectura_veterinaria_v1_FINAL/". Producto, usuarios, reglas y exclusiones funcionales ya no están pendientes de definición.
- Permanece pendiente: NFR, arquitectura física, ADR, modelo de datos, contratos, UX definitivo, infraestructura, despliegue y pruebas ejecutadas.
- La planificación técnica aprobada es Java 21, Spring Boot 4.1.1, Maven/Maven Wrapper, Angular 21 con TypeScript, PostgreSQL 18, monolito modular, DDD estratégico y arquitectura hexagonal selectiva. No existe implementación.
- ECC nativo es la ruta primaria. Agent Browser, UI UX Pro Max y Design Motion Principles se usarán cuando la tarea lo justifique. Taste, img2threejs y skills adicionales son opcionales.
- Impeccable conserva skill y launcher, pero su motor no está validado; los hooks quedan aislados hasta P1B. No se ejecutará "impeccable init" como parte de P1A.
- "MASTER.md" es una dirección visual provisional. No existe un diseño UX, identidad de marca ni "DESIGN.md" aprobados.
- El objetivo de demo pública del 26 de octubre de 2026 y la entrega académica del 18 de noviembre de 2026 no eliminan las puertas G2/G3 ni los controles de seguridad.
- El Plan Maestro Codex vigente es V0.3 en `docs/plan_maestro_codex/`. V0.2 se conserva sin modificar como historial en `docs/historial/plan_maestro_codex_v0_2/`.

## Estado confirmado

- Repositorio Git nuevo, sin commits ni código de aplicación.
- Windows y PowerShell.
- Node.js 22.20.0; Python 3.12.10 como intérprete canónico del proyecto.
- Producto, usuarios, reglas y requisitos funcionales: definidos e inmutables en V1. Arquitectura definitiva, NFR, ADR, modelo físico, contratos, UX, infraestructura y pruebas ejecutadas: pendientes de G2/G3.

## Decisiones de herramienta

- ECC primario: plugin nativo `affaan-m/ECC` para Codex; no mezclar con una sincronización legada de `~/.codex`.
- Automatización E2E y accesibilidad: Agent Browser global y Chrome for Testing.
- Diseño: Impeccable, UI UX Pro Max, Taste Skill, Design Motion Principles y skills de emilkowalski.
- Modelado 3D: img2threejs, solo cuando una interfaz requiera activos 3D.

## Calibración visual provisional

El Product Brief funcional ya está consolidado. `design-system/arquitectura-veterinaria/MASTER.md` conserva una dirección visual provisional con `DESIGN_VARIANCE=3`, `MOTION_INTENSITY=2` y `VISUAL_DENSITY=6`; no sustituye decisiones UX, marca o `DESIGN.md` aprobados.

## Secuencia de implantación

La secuencia histórica siguiente no autoriza instalaciones ni inicializadores. La secuencia vigente es: proteger la línea base y revisar el diff; resolver hooks y motor de Impeccable solo en P1B; completar G2; obtener G3; y recién entonces implementar con TDD, seguridad, E2E y accesibilidad.

1. Validar binarios, Git y árbol de trabajo.
2. Mantener un `.gitignore` limitado a artefactos efímeros de agentes.
3. Usar el ciclo nativo de ECC en Codex y revisar la confianza de hooks manualmente.
4. Instalar y verificar Agent Browser y Chrome for Testing.
5. Instalar habilidades de diseño y revisar los archivos de configuración creados.
6. Mantener `PRODUCT.md` como brief consolidado; no ejecutar inicializadores de Impeccable mientras P1B no valide su motor y exista autorización específica.
7. Registrar versiones efectivas, rutas y resultados de validación.
8. Ejecutar E2E, accesibilidad y escaneo de seguridad solo cuando exista una aplicación ejecutable y flujos definidos.

## Límites de control

- Versionar configuración útil revisada: `AGENTS.md`, `PRODUCT.md` y documentación de proyecto. Revisar procedencia y licencia antes de versionar `.agents/`; no incluir `.codex/backup/` ni estados efímeros.
- Ignorar solo cachés y capturas efímeras acordadas.
- No incluir secretos, sesiones ni perfiles de navegador.
- No declarar una validación E2E hasta disponer de aplicación, datos de prueba y criterios de aceptación.
