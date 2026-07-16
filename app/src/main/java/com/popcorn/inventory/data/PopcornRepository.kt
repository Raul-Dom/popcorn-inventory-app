package com.popcorn.inventory.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

class PopcornRepository(
    private val database: PopcornDatabase
) {
    private val dao = database.dao()

    val configuracion: Flow<ConfiguracionEntity> =
        dao.observeConfiguracion().map { it ?: ConfiguracionEntity() }

    val sabores: Flow<List<SaborEntity>> = dao.observeSabores()
    val saboresActivos: Flow<List<SaborEntity>> = dao.observeSaboresActivos()
    val promociones: Flow<List<PromocionConSabores>> = dao.observePromocionesConSabores()
    val movimientosRecientes: Flow<List<MovimientoInventarioEntity>> = dao.observeMovimientosRecientes()

    val saboresResumen: Flow<List<SaborResumen>> =
        combine(sabores, configuracion) { sabores, config ->
            sabores.map { sabor ->
                val pedidoSugerido = (sabor.inventarioIdeal - sabor.inventarioActual).coerceAtLeast(0)
                SaborResumen(
                    id = sabor.id,
                    nombre = sabor.nombre,
                    categoria = sabor.categoria,
                    inventarioIdeal = sabor.inventarioIdeal,
                    inventarioActual = sabor.inventarioActual,
                    precioVenta = sabor.precioVenta,
                    activo = sabor.activo,
                    bajoInventario = sabor.inventarioActual <= config.umbralInventarioBajo,
                    pedidoSugerido = pedidoSugerido
                )
            }
        }

    suspend fun asegurarConfiguracionInicial() {
        if (dao.getConfiguracion() == null) {
            dao.upsertConfiguracion(ConfiguracionEntity())
        }
    }

    suspend fun guardarConfiguracion(configuracion: ConfiguracionEntity) {
        dao.upsertConfiguracion(configuracion)
    }

    suspend fun crearSabor(
        nombre: String,
        categoria: String,
        inventarioIdeal: Int,
        inventarioActual: Int,
        precioPersonalizado: Double?
    ) {
        val config = dao.getConfiguracion() ?: ConfiguracionEntity()
        val precioBase = if (categoria == CategoriaSabor.DULCE) {
            config.precioBaseDulces
        } else {
            config.precioBaseSaladas
        }
        val nombreLimpio = nombre.trim()
        val existente = dao.findSaborPorNombre(nombreLimpio, categoria)
        if (existente != null) {
            dao.updateSabor(
                existente.copy(
                    nombre = nombreLimpio,
                    inventarioIdeal = inventarioIdeal,
                    inventarioActual = inventarioActual,
                    precioVenta = precioPersonalizado ?: precioBase,
                    precioPersonalizado = precioPersonalizado != null,
                    activo = true
                )
            )
        } else {
            dao.insertSabor(
                SaborEntity(
                    nombre = nombreLimpio,
                    categoria = categoria,
                    inventarioIdeal = inventarioIdeal,
                    inventarioActual = inventarioActual,
                    precioVenta = precioPersonalizado ?: precioBase,
                    precioPersonalizado = precioPersonalizado != null
                )
            )
        }
    }

    suspend fun actualizarSabor(sabor: SaborEntity) {
        dao.updateSabor(sabor.copy(nombre = sabor.nombre.trim()))
    }

    suspend fun desactivarSabor(saborId: Long) {
        dao.desactivarSabor(saborId)
    }

    suspend fun reactivarSabor(saborId: Long) {
        dao.reactivarSabor(saborId)
    }

    suspend fun borrarODesactivarSabor(saborId: Long): Boolean {
        val tieneUso = dao.contarDetallesPorSabor(saborId) > 0 || dao.contarMovimientosPorSabor(saborId) > 0
        return if (tieneUso) {
            dao.desactivarSabor(saborId)
            false
        } else {
            dao.borrarSabor(saborId)
            true
        }
    }

    suspend fun aplicarPrecioASeleccion(saborIds: List<Long>, precio: Double) {
        if (saborIds.isNotEmpty()) dao.actualizarPreciosSeleccion(saborIds, precio)
    }

    suspend fun aplicarPrecioACategorias(categorias: List<String>, precio: Double) {
        if (categorias.isNotEmpty()) dao.actualizarPreciosCategorias(categorias, precio)
    }

    suspend fun crearPromocion(
        nombre: String,
        precioPromocional: Double,
        fechaInicio: Long,
        fechaFin: Long?,
        tipo: String,
        sabores: List<PromocionSaborInput>,
        reglas: List<PromocionReglaInput>
    ) {
        val cantidadUnidades = when (tipo) {
            TipoPromocion.FIJA -> {
                require(sabores.isNotEmpty()) { "La promoción fija debe definir sabores." }
                require(reglas.isEmpty()) { "La promoción fija no necesita reglas." }
                sabores.sumOf { it.cantidad }
            }
            else -> {
                require(sabores.isEmpty()) { "Las promociones flexibles usan reglas, no sabores incluidos." }
                require(reglas.isNotEmpty()) { "La promoción debe definir al menos una regla." }
                reglas.sumOf { it.cantidad }
            }
        }
        require(cantidadUnidades > 0) { "La promoción debe incluir bolsas." }
        require(sabores.all { it.cantidad > 0 }) { "Todas las cantidades de la promoción deben ser mayores a cero." }
        require(reglas.all { it.cantidad > 0 }) { "Todas las reglas deben tener una cantidad mayor a cero." }
        val nombreLimpio = nombre.trim()
        database.withTransaction {
            val existente = dao.findPromocionPorNombre(nombreLimpio)
            if (existente != null && !existente.activa) {
                dao.reactivarPromocion(existente.id)
                return@withTransaction
            }
            val promocionId = dao.insertPromocion(
                PromocionEntity(
                    nombre = nombreLimpio,
                    cantidadUnidades = cantidadUnidades,
                    precioPromocional = precioPromocional,
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin,
                    tipo = tipo
                )
            )
            dao.insertPromocionSabores(
                sabores.map { PromocionSaborEntity(promocionId, it.saborId, it.cantidad) }
            )
            if (reglas.isNotEmpty()) {
                val ids = dao.insertPromocionReglas(
                    reglas.mapIndexed { index, regla ->
                        PromocionReglaEntity(
                            promocionId = promocionId,
                            alcance = regla.alcance,
                            categoria = regla.categoria,
                            cantidad = regla.cantidad,
                            permiteRepetir = regla.permiteRepetir,
                            orden = index
                        )
                    }
                )
                val enlaces = reglas.flatMapIndexed { index, regla ->
                    regla.saborIds.map { saborId ->
                        PromocionReglaSaborEntity(ids[index], promocionId, saborId)
                    }
                }
                if (enlaces.isNotEmpty()) dao.insertPromocionReglaSabores(enlaces)
            }
        }
    }

    suspend fun desactivarPromocion(promocionId: Long) {
        dao.desactivarPromocion(promocionId)
    }

    suspend fun reactivarPromocion(promocionId: Long) {
        dao.reactivarPromocion(promocionId)
    }

    suspend fun registrarVentaNormal(sabor: SaborEntity, cantidad: Int, fechaVenta: Long) {
        require(cantidad > 0) { "La cantidad debe ser mayor a cero." }
        registrarVentaLineas(
            lineas = listOf(VentaLineaInput(sabor, cantidad)),
            fechaVenta = fechaVenta,
            promocion = null
        )
    }

    suspend fun registrarVentaLineas(
        lineas: List<VentaLineaInput>,
        fechaVenta: Long,
        promocion: PromocionEntity? = null
    ) {
        require(lineas.isNotEmpty()) { "La venta debe tener al menos un sabor." }
        require(lineas.all { it.cantidad > 0 }) { "Todas las cantidades deben ser mayores a cero." }
        database.withTransaction {
            val totalUnidades = lineas.sumOf { it.cantidad }
            val total = promocion?.precioPromocional ?: lineas.sumOf { it.precioUnitario * it.cantidad }
            val precioPromocionalUnitario = if (promocion != null) total / totalUnidades else null
            val ventaId = dao.insertVenta(
                VentaEntity(
                    fechaVenta = fechaVenta,
                    totalUnidades = totalUnidades,
                    totalDinero = total,
                    promocionId = promocion?.id
                )
            )
            dao.insertDetallesVenta(
                lineas.map { linea ->
                    DetalleVentaEntity(
                        ventaId = ventaId,
                        saborId = linea.sabor.id,
                        cantidad = linea.cantidad,
                        precioAplicado = precioPromocionalUnitario ?: linea.precioUnitario,
                        subtotal = (precioPromocionalUnitario ?: linea.precioUnitario) * linea.cantidad
                    )
                }
            )
            lineas.forEach { linea ->
                registrarSalidaPorVenta(linea.sabor.id, linea.cantidad, fechaVenta, ventaId)
            }
        }
    }

    suspend fun registrarVentaPromocion(
        promocion: PromocionConSabores,
        selecciones: List<PromocionSeleccionInput>,
        sabores: List<SaborEntity>,
        fechaVenta: Long
    ) {
        registrarVentaLineas(
            lineas = lineasDePromocion(promocion, selecciones, sabores),
            fechaVenta = fechaVenta,
            promocion = promocion.promocion
        )
    }

    suspend fun actualizarVenta(
        ventaOriginal: VentaEntity,
        nuevasLineas: List<VentaLineaInput>,
        nuevaFechaVenta: Long,
        promocion: PromocionConSabores?,
        selecciones: List<PromocionSeleccionInput>,
        sabores: List<SaborEntity>
    ) {
        val lineasFinales = if (promocion != null) {
            lineasDePromocion(promocion, selecciones, sabores)
        } else nuevasLineas
        require(lineasFinales.isNotEmpty()) { "La venta debe tener al menos un sabor." }
        require(lineasFinales.all { it.cantidad > 0 }) { "Todas las cantidades deben ser mayores a cero." }
        database.withTransaction {
            val detallesActuales = dao.getDetallesVenta(ventaOriginal.id)
            detallesActuales.forEach { detalle ->
                dao.sumarInventario(detalle.saborId, detalle.cantidad)
            }
            dao.borrarDetallesVenta(ventaOriginal.id)
            dao.borrarMovimientosPorReferencia(TipoMovimiento.VENTA, ventaOriginal.id)

            val totalUnidades = lineasFinales.sumOf { it.cantidad }
            val total = promocion?.promocion?.precioPromocional ?: lineasFinales.sumOf { it.precioUnitario * it.cantidad }
            val precioPromocionalUnitario = if (promocion != null) total / totalUnidades else null
            dao.updateVenta(
                ventaOriginal.copy(
                    fechaVenta = nuevaFechaVenta,
                    totalUnidades = totalUnidades,
                    totalDinero = total,
                    promocionId = promocion?.promocion?.id
                )
            )
            dao.insertDetallesVenta(
                lineasFinales.map { linea ->
                    DetalleVentaEntity(
                        ventaId = ventaOriginal.id,
                        saborId = linea.sabor.id,
                        cantidad = linea.cantidad,
                        precioAplicado = precioPromocionalUnitario ?: linea.precioUnitario,
                        subtotal = (precioPromocionalUnitario ?: linea.precioUnitario) * linea.cantidad
                    )
                }
            )
            lineasFinales.forEach { linea ->
                registrarSalidaPorVenta(linea.sabor.id, linea.cantidad, nuevaFechaVenta, ventaOriginal.id)
            }
        }
    }

    suspend fun eliminarVenta(ventaId: Long) {
        database.withTransaction {
            val venta = dao.getVentaConDetalles(ventaId) ?: return@withTransaction
            borrarVentaFisicamente(venta)
        }
    }

    suspend fun eliminarPromocion(promocionId: Long) {
        database.withTransaction {
            dao.getVentasPorPromocion(promocionId).forEach { borrarVentaFisicamente(it) }
            dao.borrarPromocion(promocionId)
        }
    }

    suspend fun eliminarSabor(saborId: Long) {
        database.withTransaction {
            val promocionIds = (dao.getPromocionesPorSabor(saborId) + dao.getPromocionesPorSaborEnReglas(saborId)).distinct()
            promocionIds.forEach { promocionId ->
                dao.getVentasPorPromocion(promocionId).forEach { borrarVentaFisicamente(it) }
                dao.borrarPromocion(promocionId)
            }
            dao.getVentasPorSabor(saborId).forEach { borrarVentaFisicamente(it) }
            dao.borrarMovimientosPorSabor(saborId)
            dao.borrarSabor(saborId)
        }
    }

    suspend fun registrarMovimientoInventario(
        saborId: Long,
        tipo: String,
        cantidad: Int,
        fechaMovimiento: Long,
        motivo: String?
    ) {
        require(cantidad != 0) { "La cantidad no puede ser cero." }
        database.withTransaction {
            dao.sumarInventario(saborId, cantidad)
            dao.insertMovimiento(
                MovimientoInventarioEntity(
                    saborId = saborId,
                    tipo = tipo,
                    cantidad = cantidad,
                    fechaMovimiento = fechaMovimiento,
                    referenciaId = null,
                    motivo = motivo
                )
            )
        }
    }

    suspend fun actualizarMovimientoInventario(
        movimientoId: Long,
        nuevoSaborId: Long,
        nuevoTipo: String,
        nuevaCantidad: Int,
        nuevaFechaMovimiento: Long,
        nuevoMotivo: String?
    ) {
        require(nuevaCantidad != 0) { "La cantidad no puede ser cero." }
        database.withTransaction {
            val actual = dao.getMovimiento(movimientoId) ?: return@withTransaction
            dao.sumarInventario(actual.saborId, -actual.cantidad)
            dao.sumarInventario(nuevoSaborId, nuevaCantidad)
            dao.updateMovimiento(
                actual.copy(
                    saborId = nuevoSaborId,
                    tipo = nuevoTipo,
                    cantidad = nuevaCantidad,
                    fechaMovimiento = nuevaFechaMovimiento,
                    motivo = nuevoMotivo
                )
            )
        }
    }

    fun ventasEntre(inicio: Long, fin: Long): Flow<List<VentaConDetalles>> =
        dao.observeVentasConDetallesEntre(inicio, fin)

    fun movimientosEntre(inicio: Long, fin: Long): Flow<List<MovimientoInventarioEntity>> =
        dao.observeMovimientosEntre(inicio, fin)

    fun reporteEntre(inicio: Long, fin: Long): Flow<ReporteResumen> {
        return combine(ventasEntre(inicio, fin), saboresResumen) { ventas, sabores ->
            val saborPorId = sabores.associateBy { it.id }
            val acumulado = mutableMapOf<Long, SaborVendido>()
            ventas.flatMap { it.detalles }.forEach { detalle ->
                val anterior = acumulado[detalle.saborId]
                val nombre = saborPorId[detalle.saborId]?.nombre ?: "Sabor inactivo"
                acumulado[detalle.saborId] = SaborVendido(
                    saborId = detalle.saborId,
                    nombre = nombre,
                    unidades = (anterior?.unidades ?: 0) + detalle.cantidad,
                    dinero = (anterior?.dinero ?: 0.0) + detalle.subtotal
                )
            }
            ReporteResumen(
                totalUnidades = ventas.sumOf { it.venta.totalUnidades },
                totalDinero = ventas.sumOf { it.venta.totalDinero },
                saboresMasVendidos = acumulado.values.sortedByDescending { it.unidades },
                pedidoSugeridoTotal = sabores.filter { it.activo }.sumOf { it.pedidoSugerido }
            )
        }
    }

    private suspend fun registrarSalidaPorVenta(
        saborId: Long,
        cantidad: Int,
        fechaVenta: Long,
        ventaId: Long
    ) {
        dao.sumarInventario(saborId, -cantidad)
        dao.insertMovimiento(
            MovimientoInventarioEntity(
                saborId = saborId,
                tipo = TipoMovimiento.VENTA,
                cantidad = -cantidad,
                fechaMovimiento = fechaVenta,
                referenciaId = ventaId,
                motivo = "Venta física"
            )
        )
    }

    private suspend fun borrarVentaFisicamente(venta: VentaConDetalles) {
        if (!venta.venta.anulada) {
            venta.detalles.forEach { detalle ->
                dao.sumarInventario(detalle.saborId, detalle.cantidad)
            }
        }
        dao.borrarMovimientosPorReferenciaFisicamente(TipoMovimiento.VENTA, venta.venta.id)
        dao.borrarDetallesVenta(venta.venta.id)
        dao.borrarVenta(venta.venta.id)
    }

    private fun lineasDePromocion(
        promocion: PromocionConSabores,
        selecciones: List<PromocionSeleccionInput>,
        sabores: List<SaborEntity>
    ): List<VentaLineaInput> {
        if (promocion.promocion.tipo != TipoPromocion.FIJA) {
            val reglasPorId = promocion.reglas.associateBy { it.id }
            val saboresPorId = sabores.associateBy { it.id }
            val idsPorRegla = promocion.saboresDeReglas.groupBy { it.reglaId }.mapValues { (_, links) -> links.map { it.saborId }.toSet() }
            val cantidades = mutableMapOf<Long, Int>()
            promocion.reglas.forEach { regla ->
                val seleccion = selecciones.firstOrNull { it.reglaId == regla.id }
                    ?: error("Completa todos los requisitos de la promoción.")
                require(seleccion.saborIds.size == regla.cantidad) { "Completa todas las bolsas de la regla." }
                if (!regla.permiteRepetir) require(seleccion.saborIds.distinct().size == seleccion.saborIds.size) {
                    "Esta regla no permite repetir sabores."
                }
                seleccion.saborIds.forEach { saborId ->
                    val sabor = saboresPorId[saborId] ?: error("El sabor seleccionado ya no existe.")
                    if (regla.alcance == AlcanceReglaPromocion.CATEGORIA) {
                        require(sabor.categoria == regla.categoria) { "El sabor no pertenece a la categoría requerida." }
                    } else {
                        require(saborId in (idsPorRegla[regla.id].orEmpty())) { "El sabor no pertenece al grupo permitido." }
                    }
                    cantidades[saborId] = (cantidades[saborId] ?: 0) + 1
                }
            }
            require(selecciones.map { it.reglaId }.distinct().size == reglasPorId.size) {
                "La selección de la promoción está incompleta."
            }
            val total = promocion.promocion.cantidadUnidades
            require(cantidades.values.sum() == total) { "La promoción no coincide con sus reglas." }
            return cantidades.map { (saborId, cantidad) ->
                VentaLineaInput(
                    sabor = saboresPorId.getValue(saborId),
                    cantidad = cantidad,
                    precioUnitario = promocion.promocion.precioPromocional / total
                )
            }
        }
        val saborPorId = promocion.sabores.associateBy { it.id }
        val ingredientes = promocion.ingredientes
        val totalDefinido = ingredientes.sumOf { it.cantidad }
        val cantidades = if (ingredientes.size == 1 && totalDefinido != promocion.promocion.cantidadUnidades) {
            ingredientes.map { it.copy(cantidad = promocion.promocion.cantidadUnidades) }
        } else {
            ingredientes
        }
        require(cantidades.isNotEmpty()) { "La promoción no tiene sabores configurados." }
        require(cantidades.sumOf { it.cantidad } == promocion.promocion.cantidadUnidades) {
            "La promoción tiene cantidades de sabores incompletas. Edítala o crea una nueva."
        }
        return cantidades.map { ingrediente ->
            val sabor = saborPorId[ingrediente.saborId]
                ?: error("La promoción contiene un sabor que ya no existe.")
            VentaLineaInput(
                sabor = sabor,
                cantidad = ingrediente.cantidad,
                precioUnitario = promocion.promocion.precioPromocional / promocion.promocion.cantidadUnidades
            )
        }
    }
}

fun LocalDate.alInicioDelDiaMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun LocalDate.alFinalDelDiaMillis(): Long =
    plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
