# PRODUCT.md — Arquitectura - Veterinaria

> BORRADOR DERIVADO DE V1 FUNCIONAL. Revisar con el responsable y comparar con archivos existentes antes de crear en la raíz.

## Qué es el producto
SaaS académico B2B para varias veterinarias independientes del Perú, cada una con una sede única. Integración de propietarios/mascotas, historia clínica, citas clínicas, grooming, catálogo, inventario, ventas presenciales y tienda online. Demostración con datos sintéticos; no está habilitado para pacientes reales, cobros reales ni emisión tributaria.

## Actores
Admin SaaS (metadatos y suspensión/reactivación, sin acceso clínico ordinario); admin veterinaria (empleados, roles, catálogo, ajustes inventario, sin permisos clínicos por defecto); veterinario (edita clínica); recepcionista (citas/grooming y verificación administrativa); cajero (POS y pedidos online); encargado grooming (ejecuta tratamientos no clínicos); cliente autenticado por veterinaria (mascotas básicas, pedidos y reservas; NO expediente clínico íntegro); visitante (catálogo público).

## Flujos V1
- Autoalta vet con correo administrador verificado, contraseña inicial y activación. RUC opcional con reservas temporales/disputas documentadas.
- Registro de propietario/mascota presencial y online, vinculación verificada y transferencias históricas.
- Veterinario registra consultas, vacunas, recetas, historia auditable. Solo veterinario edita clínica.
- Recepción/admin confirma cita clínica y grooming; asigna profesional/encargado; cambios de horario requieren aceptación y comprobación de agenda sin solape.
- Admin gestiona productos/ajustes; cajero opera POS. Un solo stock por tenant. POS descuenta al completar venta; online al confirmar; carrito/pendientes no reservan.
- Pedidos online recojo o delivery con tarifa fija por vet, sin despachos parciales. Devoluciones parciales por unidad físicamente inspeccionada. Pago registrado manualmente y constancia INTERNA NO TRIBUTARIA.
- Separación de tenant, permisos, idempotencia, auditoría y notificaciones resistentes a fallos.

## UX prioritario
Español, PEN, horarios America/Lima. Interfaz móvil/responsiva; personal utiliza desktop y clientes móvil. Claro, clínico, profesional y accesible. Dar prioridad a legibilidad, estados inequívocos, evitar errores de caja/citas, mensajes de permisos y diseño de formularios.

## Exclusiones
Sin multisucursal, SUNAT integrado, pasarela de pagos, apps móviles nativas, expediente clínico completo cliente, despacho parcial, GPS, hospitalización avanzada, contabilidad integral ni 3D ornamental.

## Obligaciones aún no cerradas
NFR medibles, diseño físico de BD, privacidad y recuperación ante desastres, hosting, stack/ORM/auth, acceso a datos reales, validación de disputas y recuperación excepcional de administradores, identidades/datos antiguos, interfaz visual definitiva y PR/CI.

## Autoridad documental
El directorio `arquitectura_veterinaria_v1_FINAL/` es fuente funcional. No inventar requisitos ni reinterpretar reglas por simplificar código. `PRODUCT.md` es product brief; `AGENTS.md` establece CÓMO trabajar; `DESIGN.md` se aprobará luego.
