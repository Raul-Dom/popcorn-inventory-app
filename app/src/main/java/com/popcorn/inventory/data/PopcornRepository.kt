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
        dao.insertSabor(
            SaborEntity(
                nombre = nombre.trim(),
                categoria = categoria,
                inventarioIdeal = inventarioIdeal,
                inventarioActual = inventarioActual,
                precioVenta = precioPersonalizado ?: precioBase,
                precioPersonalizado = precioPersonalizado != null
            )
        )
    }

    suspend fun actualizarSabor(sabor: SaborEntity) {
        dao.updateSabor(sabor.copy(nombre = sabor.nombre.trim()))
    }

    suspend fun desactivarSabor(saborId: Long) {
        dao.desactivarSabor(saborId)
    }

    suspend fun aplicarPrecioASeleccion(saborIds: List<Long>, precio: Double) {
        if (saborIds.isNotEmpty()) dao.actualizarPreciosSeleccion(saborIds, precio)
    }

    suspend fun aplicarPrecioACategorias(categorias: List<String>, precio: Double) {
        if (categorias.isNotEmpty()) dao.actualizarPreciosCategorias(categorias, precio)
    }

    suspend fun crearPromocion(
        nombre: String,
        cantidadUnidades: Int,
        precioPromocional: Double,
        fechaInicio: Long,
        fechaFin: Long?,
        saborIds: List<Long>
    ) {
        database.withTransaction {
            val promocionId = dao.insertPromocion(
                PromocionEntity(
                    nombre = nombre.trim(),
                    cantidadUnidades = cantidadUnidades,
                    precioPromocional = precioPromocional,
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin
                )
            )
            if (saborIds.isNotEmpty()) {
                dao.insertPromocionSabores(saborIds.map { PromocionSaborEntity(promocionId, it) })
            }
        }
    }

    suspend fun desactivarPromocion(promocionId: Long) {
        dao.desactivarPromocion(promocionId)
    }

    suspend fun registrarVentaNormal(sabor: SaborEntity, cantidad: Int, fechaVenta: Long) {
        require(cantidad > 0) { "La cantidad debe ser mayor a cero." }
        database.withTransaction {
            val total = sabor.precioVenta * cantidad
            val ventaId = dao.insertVenta(
                VentaEntity(
                    fechaVenta = fechaVenta,
                    totalUnidades = cantidad,
                    totalDinero = total,
                    promocionId = null
                )
            )
            dao.insertDetallesVenta(
                listOf(
                    DetalleVentaEntity(
                        ventaId = ventaId,
                        saborId = sabor.id,
                        cantidad = cantidad,
                        precioAplicado = sabor.precioVenta,
                        subtotal = total
                    )
                )
            )
            registrarSalidaPorVenta(sabor.id, cantidad, fechaVenta, ventaId)
        }
    }

    suspend fun registrarVentaPromocion(
        promocion: PromocionEntity,
        sabor: SaborEntity,
        fechaVenta: Long
    ) {
        require(promocion.cantidadUnidades > 0) { "La promoción debe tener unidades." }
        database.withTransaction {
            val ventaId = dao.insertVenta(
                VentaEntity(
                    fechaVenta = fechaVenta,
                    totalUnidades = promocion.cantidadUnidades,
                    totalDinero = promocion.precioPromocional,
                    promocionId = promocion.id
                )
            )
            dao.insertDetallesVenta(
                listOf(
                    DetalleVentaEntity(
                        ventaId = ventaId,
                        saborId = sabor.id,
                        cantidad = promocion.cantidadUnidades,
                        precioAplicado = promocion.precioPromocional / promocion.cantidadUnidades,
                        subtotal = promocion.precioPromocional
                    )
                )
            )
            registrarSalidaPorVenta(sabor.id, promocion.cantidadUnidades, fechaVenta, ventaId)
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
}

fun LocalDate.alInicioDelDiaMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun LocalDate.alFinalDelDiaMillis(): Long =
    plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
