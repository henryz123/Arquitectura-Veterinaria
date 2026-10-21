# 09 — Resolución de decisiones y registro de riesgos para arquitectura

**Estado:** cuatro decisiones críticas expresamente ratificadas; análisis funcional cerrado. Los riesgos técnicos y legales quedan como **gates** de etapas posteriores, no nuevas preguntas de alcance V1.

## Decisiones resueltas

| Código antiguo | Decisión | ID vigente |
|---|---|---|
| V1-AP-01 | Cliente no accede a historia clínica completa por portal | DEC-064 / RN-073 |
| V1-AP-02 | Devoluciones parciales inspeccionadas; no despachos parciales | DEC-065 / RN-074 |
| V1-AP-03 | Pagos manuales y constancias internas no tributarias | DEC-066 / RN-075 |
| V1-AP-04 | Grooming con encargado disponible, agenda multirol sin solapamientos, horarios alternativos aceptados | DEC-067 / RN-076 |

## Riesgos residuales y puertas específicas

| Código | Riesgo | Severidad | Puerta que lo controla |
|---|---|---|---|
| SEG-01 | Acceso clínico indebido tras transferencia de mascota | Alta | Diseño de recursos/autorización B02; pruebas negativas E2E antes de demo |
| SEG-02 | Suplantación en recuperación excepcional admin / disputa RUC | Alta | Definir evidencia mínima y revocación sesiones anteriores antes de implementar flujos |
| INT-01 | Sobreventa o doble restitución | Alta | Transacciones, claves de idempotencia y pruebas de concurrencia reales |
| INT-02 | Solapamiento de agenda entre roles | Alta | Persistencia de intervalos y pruebas concurrentes |
| FISC-01 | Constancia interna malinterpretada como comprobante SUNAT | Alta para operación real | Uso académico exclusivamente; proceso fiscal legal antes de ventas reales |
| PRIV-01 | Datos reales sin salvaguardas peruanas | Alta para operación real | Política de privacidad, fundamento de uso, derechos, seguridad y validación legal |
| OPS-01 | Correos fallidos, repetición de notificaciones | Media | Mecanismo confiable y pruebas de reintentos |
| DOC-01 | Documentos aprobados en conversación no materializados originalmente | Media | Crear y revisar repositorio documental controlado con CCR y referencias |
| DEV-01 | Stack, infraestructura y repositorio no inspeccionados | Alta para Codex | Fase arquitectura y diagnóstico real antes de orquestación |

## Políticas seguras V1 para asuntos no controversiales

- Solo el **cajero** registra ventas POS; administrador necesita también el rol cajero.
- Recepción/administración aprueba cancelaciones de citas y servicios confirmados.
- Cliente sin lectura de expediente clínico completo en portal; derechos legales externos permanecen.
- Agenda original confirmada persiste mientras sea viable; si deja de serlo, notificar y pasar explícitamente a reprogramación pendiente o cancelación.
- Pago, stock y logística son estados separados. No se valida cobro por declaración del cliente.

**Dictamen:** decisiones funcionales cerradas para V1 académica. No existen nuevos interrogantes de negocio de alto impacto que impidan iniciar la **etapa de arquitectura y plan de implementación**. Las puertas anteriores siguen bloqueando desarrollo de áreas sensibles o operación real hasta cumplirse.
