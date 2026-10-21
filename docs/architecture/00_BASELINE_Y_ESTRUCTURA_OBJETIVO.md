# G2 — Línea técnica y estructura objetivo

## Decisiones de base

| Área | Decisión | Estado |
|---|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Maven y Maven Wrapper | Decidido para implementar; entorno local aún no conforme |
| Frontend | Angular 21 y TypeScript | Decidido para implementar; CLI aún no disponible |
| Datos | PostgreSQL 18 | Decidido; cliente 18.6 localizado fuera de `PATH` |
| Estilo | Monolito modular, DDD estratégico y arquitectura hexagonal selectiva | Decidido para implementar |
| Integración | REST versionada y contrato OpenAPI | Decidido para implementar |
| Cambios de esquema | Flyway con módulo PostgreSQL, migraciones solo tras autorización de implementación | Decidido arquitectónicamente; no instalado ni ejecutado |
| Sesión | Sesiones opacas del lado servidor, cookies seguras y misma procedencia en producción | Decidido para implementar |
| Moneda y tiempo | PEN exacto; instantes UTC y presentación `America/Lima` | Obligación heredada de V1 |

Spring Boot 4.1.1 y PostgreSQL 18 son versiones publicadas y documentadas por sus proyectos. Angular 21 admite Node 22.12 o superior, por lo que el Node 22.20.0 detectado es compatible; esto no sustituye la ausencia real de Angular CLI.

## Estructura objetivo — no creada todavía

```text
Arquitectura-Veterinaria/
├── backend/                         # Spring Boot; un único desplegable modular
│   ├── pom.xml
│   ├── mvnw / .mvn/
│   └── src/{main,test}/java/...
├── frontend/                        # workspace Angular 21
│   ├── package.json
│   └── src/app/
├── database/                        # migraciones Flyway y datos sintéticos, nunca secretos
│   ├── migration/
│   └── fixtures/
├── infra/                           # Docker/Compose y descriptores de despliegue, después de G3
├── docs/                            # autoridad documental, ADR, contratos y evidencias
├── scripts/                         # automatización revisable y no secreta, después de G3
├── design-system/                   # tokens y especificaciones, no cachés de herramientas
├── arquitectura_veterinaria_v1_FINAL/ # línea funcional inmutable
├── AGENTS.md
└── PRODUCT.md
```

No se crea esta estructura en esta actividad para evitar presentar scaffolding como implementación. Los nombres establecen la ubicación exclusiva futura de cada clase de artefacto.

## Convenciones que protegen el producto

- Paquetes Java: `pe.arquitecturaveterinaria.<modulo>`, y dentro de cada módulo `domain`, `application`, `adapter.in.web`, `adapter.out.persistence` y `config` solo cuando el límite lo justifique.
- Angular se organiza por capacidad (`features/<capacidad>`), `core` para sesión/API/interceptores y `shared` para componentes sin reglas de negocio. No replica tablas ni capas Java.
- Las migraciones futuras se numeran en `database/migration/` y se aplican únicamente mediante Flyway en un entorno controlado. No se modifica una migración aplicada.
- OpenAPI vive en `docs/contracts/` como fuente de contrato; clientes Angular se generan o implementan contra esa versión, nunca contra tablas.
- Solo datos sintéticos y manifiestos verificables se versionan. `.env`, certificados, cachés, binarios y dependencias permanecen excluidos.

## Puertas para crear la estructura

Antes del primer archivo de aplicación: JDK 21 activo, Maven Wrapper generado desde una fuente aprobada, Angular CLI compatible, decisión de versiones de dependencias revisada, ADR presentes, contrato mínimo y tarea G3 autorizada. La falta de cualquiera bloquea el inicio, no se compensa con un prototipo desechable.
