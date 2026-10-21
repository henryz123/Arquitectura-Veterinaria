# Infraestructura

Docker y Docker Compose no están instalados en el equipo auditado. No se crearon imágenes ni archivos Compose sin una ejecución verificable; la ruta local Java + Angular + PostgreSQL 18 está documentada en `docs/runbooks/G4-I00_ENTORNO_LOCAL.md`.

Si una fase posterior requiere Docker, primero se verificará que no exige habilitar WSL2, virtualización, reinicio o cambios de seguridad. Esas acciones requieren autorización específica.
