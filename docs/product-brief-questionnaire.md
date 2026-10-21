# Registro histórico de insumos del Product Brief

## Conciliación G1-A — 21 de octubre de 2026

La información funcional confirmada se consolidó manualmente en "PRODUCT.md" a partir de "arquitectura_veterinaria_v1_FINAL/". Este cuestionario se conserva como registro de descubrimiento; no reemplaza V1 ni autoriza diseño o implementación.

### Hechos ya confirmados

- SaaS B2B académico para veterinarias independientes del Perú, una sede por tenant y datos sintéticos.
- Aplicación web responsive; sin aplicación móvil nativa, pagos online, SUNAT, multisucursal ni expediente clínico completo en portal cliente.
- Ámbitos separados para administración SaaS, personal de veterinaria, clientes y visitantes; reglas de tenant, roles, clínica, agenda, inventario, POS y pedidos ya definidas en B01 a B06.
- Español, PEN y agenda en America/Lima.
- Planificación técnica aprobada: Java 21, Spring Boot 4.1.1, Maven/Maven Wrapper, Angular 21 con TypeScript, PostgreSQL 18, monolito modular, DDD estratégico y arquitectura hexagonal selectiva.

### Pendientes de G2

- NFR cuantitativos, privacidad, retención, seguridad de sesiones, ADR, modelo físico, contratos, despliegue, backup/restauración y métricas de la demo.
- Diseño UX, accesibilidad verificable, voz, marca, activos visuales y "DESIGN.md".
- Infraestructura para URL pública y procedimiento de publicación del repositorio después de la revisión de secretos.

No ejecutar "impeccable init" mientras el motor no esté validado y P1B no sea autorizado. "PRODUCT.md" ya es el brief consolidado y no debe ser reemplazado por un inicializador.

Completa solo hechos confirmados. Las decisiones aún abiertas deben declararse como tales; no deben reemplazarse con supuestos estéticos.

```md
- Producto (nombre y una frase):
- Plataforma (web, iOS, Android o adaptativa):
- Usuario principal, situación y trabajo que debe completar:
- Usuarios secundarios y sus límites:
- Problema que resuelve y resultado esperado:
- Flujo crítico del MVP, desde el disparador hasta el resultado:
- Diferenciador verificable o mecanismo propio:
- Incluye en la primera versión:
- No incluye en la primera versión:
- Contexto operativo (sedes, horario, conectividad, idiomas, moneda, dispositivos):
- Datos sensibles, consentimiento, retención y reglas aplicables:
- Integraciones, activos y terminología que deben preservarse:
- Éxito medible:
- Stack y destino de despliegue (o "delegado al equipo"):
- Compromisos de marca, voz o activos existentes:
- Evidencia disponible (documentos, entrevistas, datos, rutas de archivos):
- Necesidades de accesibilidad e inclusión:
- Decisiones pendientes y responsable de aprobarlas:
```

La instrucción anterior a este párrafo pertenecía al bootstrap y queda reemplazada: no ejecutar `$impeccable init` para crear o sustituir `PRODUCT.md`. Cualquier uso futuro de Impeccable requiere P1B aprobado, motor validado, hook controlado y un objetivo UX concreto.
