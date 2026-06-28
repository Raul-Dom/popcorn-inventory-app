package com.popcorn.inventory.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class VentaConDetalles(
    @Embedded val venta: VentaEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "ventaId"
    )
    val detalles: List<DetalleVentaEntity>
)

data class PromocionConSabores(
    @Embedded val promocion: PromocionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PromocionSaborEntity::class,
            parentColumn = "promocionId",
            entityColumn = "saborId"
        )
    )
    val sabores: List<SaborEntity>
)

data class SaborResumen(
    val id: Long,
    val nombre: String,
    val categoria: String,
    val inventarioIdeal: Int,
    val inventarioActual: Int,
    val precioVenta: Double,
    val activo: Boolean,
    val bajoInventario: Boolean,
    val pedidoSugerido: Int
)

data class ReporteResumen(
    val totalUnidades: Int,
    val totalDinero: Double,
    val saboresMasVendidos: List<SaborVendido>,
    val pedidoSugeridoTotal: Int
)

data class SaborVendido(
    val saborId: Long,
    val nombre: String,
    val unidades: Int,
    val dinero: Double
)

data class VentaLineaInput(
    val sabor: SaborEntity,
    val cantidad: Int,
    val precioUnitario: Double = sabor.precioVenta
)
