# Inventario de Palomitas

App Android local para registrar ventas físicas, inventario por sabor, promociones, pedidos recibidos, ajustes y reportes simples.

## Estado

Primera base del proyecto creada con:

- Kotlin nativo.
- Jetpack Compose para la interfaz.
- Room para base de datos local SQLite.
- Operación sin internet.
- Interfaz visible en español.

## Reglas principales

- Las unidades son bolsas enteras, sin decimales.
- El dinero se muestra como `$1,234.56`.
- Las unidades se muestran como `1,234`.
- Las fechas se muestran como `09/Junio/26`.
- Los sabores pueden ser `Dulces` o `Saladas`.
- Cada sabor tiene inventario ideal propio.
- La alerta de inventario bajo aparece con 10 bolsas o menos.
- El pedido sugerido se calcula con `inventario ideal - inventario actual`.
- `Pedido recibido` suma inventario.
- `Regalo de proveedor` suma inventario.
- `Cortesía / regalo al cliente` resta inventario.
- `Merma / dañado` resta inventario.
- `Corrección de conteo` puede sumar o restar.
- Las promociones se pueden crear, desactivar, reactivar y eliminar definitivamente con confirmación.

## Secciones

- Ventas: registro de venta normal o con promoción.
- Inventario: sabores, pedido sugerido, pedido recibido y ajustes.
- Configuración: precios base, promociones y cambios masivos de precio.
- Histórico: ventas de cualquier fecha, edición y eliminación con confirmación.
- Reportes: día, semana, mes y rango de fechas.

## Promociones y correcciones

- Las promociones pueden ser fijas, por categorías, por grupo de sabores permitidos o avanzadas.
- Una promoción fija define sabores y cantidades exactas; una promoción flexible guarda reglas y pide solo los sabores necesarios al vender.
- Al registrar una venta por promoción, la app calcula las bolsas y el precio desde la configuración guardada.
- Las ventas se pueden editar o eliminar en cualquier fecha.
- Eliminar una venta devuelve las bolsas al inventario y la excluye de reportes.
- Sabores y promociones inactivos pueden reactivarse desde sus respectivas secciones.
- Eliminar definitivamente un sabor elimina sus registros relacionados; requiere confirmación y no se puede deshacer.

El contexto completo, las decisiones y la evolución del proyecto se mantienen en [STABILIZATION_REPORT.md](STABILIZATION_REPORT.md).

## Actualización 1.4

- Se agregó el módulo Histórico para consultar ventas por fecha sin saturar la captura diaria.
- Las ventas históricas se pueden editar o eliminar; al corregirlas se ajusta el inventario y al eliminarlas se devuelven las bolsas.
- Las promociones ahora soportan reglas por categoría, grupos de sabores permitidos y combinaciones avanzadas.
- Room se actualizó a la versión 3 con tablas para reglas de promociones y una migración compatible con instalaciones existentes.
- Las eliminaciones definitivas de promociones y sabores limpian sus relaciones, movimientos y ventas asociadas con confirmación.

## Cómo abrir

1. Abre Android Studio.
2. Selecciona `Open`.
3. Elige esta carpeta: `C:\Users\rulo_\Documents\popcorn-inventory-app`.
4. Espera a que Android Studio sincronice Gradle.
5. Ejecuta la app en un celular Android o emulador.

## Cómo generar el APK sin programar

La forma más simple es usar GitHub Actions:

1. Sube este proyecto a un repositorio de GitHub.
2. En GitHub, entra al repositorio.
3. Abre la pestaña `Actions`.
4. Selecciona el workflow `Generar APK Android`.
5. Presiona `Run workflow`.
6. Espera a que termine el proceso.
7. En la ejecución terminada, baja hasta `Artifacts`.
8. Descarga `inventario-palomitas-debug-apk`.
9. Descomprime el archivo descargado.
10. Copia `app-debug.apk` a tu celular Samsung.
11. Abre el APK en el celular y acepta instalar desde esa fuente si Android lo pide.

Este APK es de prueba (`debug`), suficiente para instalarlo en tu celular y validar el flujo básico con datos falsos.

## Si Android dice que hay conflicto de paquete

Si aparece el mensaje `App not installed as package conflicts with an existing package`, significa que el APK nuevo y la app instalada tienen el mismo paquete Android, pero fueron firmados con llaves distintas.

Desde la versión `1.1`, los APK debug de GitHub Actions usan una llave estable incluida en `signing/popcorn-debug.p12`. Eso permite actualizar encima en futuras versiones.

Para pasar desde una versión anterior a la versión `1.1`, puede ser necesario desinstalar una vez la app anterior y luego instalar el APK nuevo. Esto borra los datos locales de prueba guardados en el celular.

## Datos falsos para probar el flujo básico

Después de instalar la app, puedes capturar estos sabores de prueba:

| Sabor | Categoría | Inventario ideal | Inventario actual | Precio |
|---|---|---:|---:|---:|
| Caramelo | Dulces | 32 | 32 | $50.00 |
| Esquite | Saladas | 24 | 24 | $55.00 |
| Chile | Saladas | 40 | 18 | $50.00 |

Prueba sugerida:

1. Registra una venta normal de 2 bolsas de Caramelo.
2. Crea una promoción de 3 bolsas por `$120.00`.
3. Registra una venta con promoción.
4. En Inventario, revisa el pedido sugerido.
5. Registra un pedido recibido.
6. Registra un regalo de proveedor.
7. Registra una merma o corrección de conteo.
8. Revisa Reportes por día, semana, mes y rango.

## Nota del entorno local

La consola donde se creó este proyecto no tiene disponibles `java`, `gradle` ni Android SDK en el PATH. Eso es un problema del entorno, no del diseño del proyecto. Para compilar localmente necesitas Android Studio o una terminal con Java 17, Gradle y Android SDK instalados.
