package com.popcorn.inventory.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object CategoriaSabor {
    const val DULCE = "DULCE"
    const val SALADA = "SALADA"
}

object TamanoInterfaz {
    const val PEQUENO = "PEQUENO"
    const val MEDIANO = "MEDIANO"
    const val GRANDE = "GRANDE"
}

object TipoMovimiento {
    const val VENTA = "VENTA"
    const val PEDIDO_RECIBIDO = "PEDIDO_RECIBIDO"
    const val REGALO_PROVEEDOR = "REGALO_PROVEEDOR"
    const val MERMA_DANADO = "MERMA_DANADO"
    const val CORTESIA_CLIENTE = "CORTESIA_CLIENTE"
    const val CORRECCION_CONTEO = "CORRECCION_CONTEO"
    const val OTRO_AJUSTE = "OTRO_AJUSTE"
}

object TipoPromocion {
    const val FIJA = "FIJA"
    const val CATEGORIAS = "CATEGORIAS"
    const val GRUPO_SABORES = "GRUPO_SABORES"
    const val AVANZADA = "AVANZADA"
}

object AlcanceReglaPromocion {
    const val CATEGORIA = "CATEGORIA"
    const val SABORES = "SABORES"
}

@Entity(tableName = "sabores")
data class SaborEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val categoria: String,
    val inventarioIdeal: Int,
    val inventarioActual: Int,
    val precioVenta: Double,
    val precioPersonalizado: Boolean,
    val activo: Boolean = true,
    val creadoEn: Long = System.currentTimeMillis()
)

@Entity(tableName = "configuracion")
data class ConfiguracionEntity(
    @PrimaryKey val id: Int = 1,
    val precioBaseDulces: Double = 50.0,
    val precioBaseSaladas: Double = 50.0,
    val tamanoInterfaz: String = TamanoInterfaz.MEDIANO,
    val umbralInventarioBajo: Int = 10,
    val confirmarVentas: Boolean = true
)

@Entity(tableName = "promociones")
data class PromocionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val cantidadUnidades: Int,
    val precioPromocional: Double,
    val fechaInicio: Long,
    val fechaFin: Long?,
    val tipo: String = TipoPromocion.FIJA,
    val activa: Boolean = true,
    val creadaEn: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "promocion_sabores",
    primaryKeys = ["promocionId", "saborId"],
    foreignKeys = [
        ForeignKey(
            entity = PromocionEntity::class,
            parentColumns = ["id"],
            childColumns = ["promocionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SaborEntity::class,
            parentColumns = ["id"],
            childColumns = ["saborId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("promocionId"), Index("saborId")]
)
data class PromocionSaborEntity(
    val promocionId: Long,
    val saborId: Long,
    val cantidad: Int = 1
)

@Entity(
    tableName = "promocion_reglas",
    foreignKeys = [
        ForeignKey(
            entity = PromocionEntity::class,
            parentColumns = ["id"],
            childColumns = ["promocionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("promocionId")]
)
data class PromocionReglaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val promocionId: Long,
    val alcance: String,
    val categoria: String?,
    val cantidad: Int,
    val permiteRepetir: Boolean,
    val orden: Int
)

@Entity(
    tableName = "promocion_regla_sabores",
    primaryKeys = ["reglaId", "saborId"],
    foreignKeys = [
        ForeignKey(
            entity = PromocionReglaEntity::class,
            parentColumns = ["id"],
            childColumns = ["reglaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SaborEntity::class,
            parentColumns = ["id"],
            childColumns = ["saborId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("promocionId"), Index("saborId")]
)
data class PromocionReglaSaborEntity(
    val reglaId: Long,
    val promocionId: Long,
    val saborId: Long
)

@Entity(tableName = "ventas")
data class VentaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fechaVenta: Long,
    val fechaRegistro: Long = System.currentTimeMillis(),
    val totalUnidades: Int,
    val totalDinero: Double,
    val promocionId: Long?,
    val anulada: Boolean = false
)

@Entity(
    tableName = "detalle_ventas",
    foreignKeys = [
        ForeignKey(
            entity = VentaEntity::class,
            parentColumns = ["id"],
            childColumns = ["ventaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SaborEntity::class,
            parentColumns = ["id"],
            childColumns = ["saborId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("ventaId"), Index("saborId")]
)
data class DetalleVentaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: Long,
    val saborId: Long,
    val cantidad: Int,
    val precioAplicado: Double,
    val subtotal: Double
)

@Entity(
    tableName = "movimientos_inventario",
    foreignKeys = [
        ForeignKey(
            entity = SaborEntity::class,
            parentColumns = ["id"],
            childColumns = ["saborId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("saborId"), Index("fechaMovimiento")]
)
data class MovimientoInventarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saborId: Long,
    val tipo: String,
    val cantidad: Int,
    val fechaMovimiento: Long,
    val fechaRegistro: Long = System.currentTimeMillis(),
    val referenciaId: Long?,
    val motivo: String?,
    val anulado: Boolean = false
)
