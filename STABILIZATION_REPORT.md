# Reporte del Proyecto - La Pop-Pería

Actualizado: 12/Julio/26

Este documento funciona como bitacora humana del proyecto. La idea es poder entender como va evolucionando la app sin revisar commit por commit.

## Estado Actual

La app ya compila desde GitHub Actions y genera un APK debug instalable como artefacto.

El proyecto es una app Android local/offline para controlar inventario, ventas, pedidos recibidos, promociones, ajustes y reportes de La Pop-Pería. No usa backend, login, nube ni pagos.

Stack actual:

- Kotlin
- Jetpack Compose
- Room
- Gradle
- GitHub Actions

## Version Actual de Trabajo

La version actual se enfoca en estabilizar la app y mejorar el flujo basico antes de usar datos reales.

Cambios principales incluidos:

- La app compila en GitHub Actions.
- El APK debug se genera automaticamente desde el workflow `Generar APK Android`.
- El nombre visible dentro de la app ya es `La Pop-Pería`.
- El nombre Android de instalacion/launcher tambien se cambio a `La Pop-Pería`.
- Se corrigio el crash detectado al hacer scroll al final de Ventas.
- Se mejoro el flujo de captura de ventas.
- Se agrego seleccion de fechas con calendario.
- Se agrego capacidad de registrar ventas de varias lineas.
- Se agrego soporte para ventas por promocion.
- Se agrego edicion/correccion directa de ventas y movimientos.
- Se quitaron controles de configuracion que no hacian nada, como `Pequeno`, `Mediano` y `Grande`.
- Se corrigio el desfase de un dia al elegir fechas desde calendarios.
- Se agregaron graficas simples en Reportes para ver bolsas vendidas y dinero vendido por sabor.
- Se configuro una firma debug estable para que futuras versiones puedan actualizarse sobre la anterior despues de la reinstalacion inicial necesaria.
- Las promociones ahora guardan una cantidad exacta por sabor mediante una migracion Room compatible.
- El flujo `Nueva venta` separa venta por pieza y venta por promocion; la promocion usa su composicion guardada sin pedir sabores ni cantidades manuales.
- Se agrego eliminacion confirmada de ventas con reversa de inventario y exclusion de reportes.
- Se agrego reactivacion de sabores y promociones inactivos.

## Evolucion del Proyecto

### Etapa 1 - Base Android

Se preparo la estructura Android del proyecto:

- Modulo principal en `app`.
- Configuracion Gradle.
- Kotlin y Jetpack Compose.
- Base local con Room.
- Entidades para sabores, inventario, promociones, ventas, detalles y movimientos.
- Workflow de GitHub Actions para generar APK debug.

Resultado: el repositorio quedo preparado para construir la app desde GitHub.

### Etapa 2 - Compilacion y APK

Se corrigieron errores iniciales de compilacion, incluyendo un problema de tipos donde Kotlin esperaba `Long` y recibia `Int`.

Tambien se dejo documentado que, si el entorno local no tiene Android SDK/Gradle/Java, GitHub Actions es la ruta principal para construir el APK.

Resultado: GitHub Actions pudo compilar y publicar el APK debug.

### Etapa 3 - Estabilizacion de Ventas

Se investigo el cierre inesperado al llegar al final del scroll en Ventas.

La causa mas probable estaba en identificadores inestables o duplicados dentro de listas Compose. Se ajustaron las llaves de elementos para evitar conflictos al renderizar el final de la lista.

Resultado: la app dejo de fallar en GitHub Actions y quedo preparada para nueva prueba en celular.

### Etapa 4 - Flujo de Ventas, Fechas y Correcciones

Se mejoro el uso real de la seccion de ventas:

- Fecha por defecto en hoy.
- Calendario para registrar ventas de dias anteriores.
- Venta por pieza.
- Venta con varias lineas de sabores y cantidades.
- Venta por promocion.
- Edicion/correccion directa de ventas.
- Ajuste de inventario cuando se corrige una venta.
- Calendarios en reportes para no depender solo de flechas.

Resultado: la captura se acerca mas a escenarios reales, como varias ventas al final del dia, varios sabores en una misma venta o ventas hechas en fechas anteriores.

### Etapa 5 - Nombre y Limpieza de Configuracion

Se cambio el nombre visible de la app a `La Pop-Pería`.

Tambien se quito de Configuracion la seccion de tamano de interfaz (`Pequeno`, `Mediano`, `Grande`) porque no tenia efecto real sobre la app. Mantenerla visible confundia mas de lo que ayudaba.

Resultado: la pantalla de Configuracion queda mas honesta y clara.

### Etapa 6 - Fechas y Reportes Visuales

Se corrigio el problema donde seleccionar una fecha en el calendario regresaba el dia anterior. La causa era una conversion entre UTC y zona horaria local dentro del selector de fechas de Material.

Tambien se agregaron graficas simples en Reportes:

- Bolsas vendidas por sabor.
- Dinero vendido por sabor.

Las graficas usan el mismo periodo elegido en Reportes: dia, semana, mes o rango.

Resultado: los reportes ahora permiten revisar mas rapido que se vendio y cuanto se vendio, sin depender solo de una lista.

### Etapa 7 - Promociones, Totales y Correcciones

La estructura anterior de promociones solo conocia los sabores permitidos y la cantidad total. Eso obligaba a elegir manualmente los sabores al vender una promocion y podia producir registros incompletos.

Se agrego la cantidad por sabor en `promocion_sabores` y se incremento Room de version 1 a 2 con una migracion. Las promociones nuevas se crean definiendo, por ejemplo, `Caramelo x1` y `Queso x1`; al venderlas, la app descuenta esas lineas, registra el precio promocional completo y las reporta como una sola venta.

Las ventas por pieza siguen aceptando una o varias lineas. Las ventas pueden editarse en cualquier fecha y ahora tambien eliminarse con confirmacion: se devuelve cada bolsa al sabor correspondiente, se anula el movimiento de venta y la venta deja de contar en reportes.

Los sabores y promociones inactivos pueden reactivarse. Crear un sabor con el mismo nombre y categoria de uno existente reutiliza y reactiva el registro en vez de duplicarlo. Crear una promocion con el mismo nombre de una inactiva la reactiva.

## Pruebas de esta etapa

En el celular, con datos falsos, revisar:

1. Crear una promocion de dos sabores con una bolsa por sabor.
2. Registrar el mismo dia una promocion de `$60.00` y una venta de Caramelo de `$35.00`; el resumen y Reportes deben mostrar `$95.00`.
3. Confirmar que la venta promocional descuenta exactamente la composicion configurada.
4. Editar una venta de 1 a 2 bolsas y confirmar diferencia de inventario y total.
5. Eliminar una venta y confirmar que devuelve las bolsas y desaparece del reporte.
6. Desactivar y reactivar un sabor y una promocion.
7. Revisar Reportes por dia, semana, mes y rango; las bolsas por sabor deben coincidir con las graficas.

Riesgo conocido: las promociones antiguas con varios sabores y cantidad total ambigua no pueden reconstruirse automaticamente con certeza. La app conserva sus datos; si la suma guardada no coincide con el total, bloquea la venta y pide crear una promocion nueva con cantidades exactas.

## Decisiones Importantes

### App local/offline

La app se mantiene local en el celular. No hay cuentas, servidores, nube ni sincronizacion.

### Unidades

Todo el inventario se maneja en unidades enteras, es decir bolsas:

- 1 bolsa
- 2 bolsas
- 10 bolsas

No se manejan decimales en cantidades.

### Dinero

El dinero se muestra con simbolo `$`, comas para miles y 2 decimales.

Ejemplo:

```text
$1,234.56
```

### Fechas

El formato visual elegido es:

```text
09/Junio/26
```

### Inventario bajo

El umbral general de alerta es 10 bolsas o menos.

### Edicion de datos

Por practicidad, se priorizo permitir corregir directamente registros equivocados. Esto hace mas facil arreglar errores de captura en cualquier fecha.

Riesgo conocido: editar datos historicos cambia reportes anteriores, pero para esta etapa se considero mejor permitir correcciones simples que bloquear al usuario.

### Sabores usados en registros

Si un sabor ya tiene historial, lo mas seguro es inactivarlo en vez de borrarlo fisicamente para no romper reportes.

## Instalacion y Actualizaciones

El APK se descarga desde GitHub Actions:

1. Entrar al repositorio en GitHub.
2. Ir a `Actions`.
3. Abrir el ultimo workflow exitoso `Generar APK Android`.
4. Descargar el artefacto `inventario-palomitas-debug-apk`.
5. Extraer el ZIP.
6. Instalar el APK en el celular.

Nota importante:

Hubo una version anterior firmada con una firma debug distinta. Si Android muestra conflicto de paquete al actualizar, hay que desinstalar una vez la app anterior e instalar el nuevo APK. Despues de esa instalacion, las siguientes actualizaciones deberian poder instalarse encima porque ya se configuro una firma debug estable.

## Riesgos Conocidos

Antes de usar datos reales de forma definitiva, conviene probar:

- Registrar una venta de un solo sabor.
- Registrar una venta con varios sabores.
- Registrar una venta con promocion.
- Editar una venta del mismo dia.
- Editar una venta de dias anteriores.
- Registrar pedido recibido.
- Registrar merma/cortesia/correccion.
- Revisar que el inventario suba o baje correctamente.
- Revisar reportes por dia, semana, mes y rango.
- Confirmar que al elegir una fecha se queda exactamente el dia seleccionado.
- Confirmar que las graficas de reportes coinciden con la lista de sabores vendidos.
- Confirmar que el scroll de Ventas ya no cierre la app.

## Hacia Donde Va

La direccion del proyecto es mantener la app simple, estable y util para operacion diaria.

Prioridades siguientes:

1. Probar la version actual en celular con datos falsos.
2. Corregir cualquier crash o comportamiento raro antes de meter datos reales.
3. Afinar promociones segun promociones reales del negocio.
4. Mejorar reportes solo cuando ya exista uso real.
5. Evitar redisenos grandes mientras la app siga estabilizandose.

## Criterio de Listo

Una version se considera lista para probar cuando:

- GitHub Actions aparece en verde.
- Existe artefacto APK descargable.
- El APK instala correctamente.
- La app abre sin crash.
- Se puede registrar venta, pedido recibido y ajuste.
- El inventario cambia de forma esperada.
- Los reportes muestran informacion coherente.
