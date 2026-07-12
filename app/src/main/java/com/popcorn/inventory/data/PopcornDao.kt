package com.popcorn.inventory.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PopcornDao {
    @Query("SELECT * FROM configuracion WHERE id = 1")
    fun observeConfiguracion(): Flow<ConfiguracionEntity?>

    @Query("SELECT * FROM configuracion WHERE id = 1")
    suspend fun getConfiguracion(): ConfiguracionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfiguracion(configuracion: ConfiguracionEntity)

    @Query("SELECT * FROM sabores ORDER BY activo DESC, nombre COLLATE NOCASE ASC")
    fun observeSabores(): Flow<List<SaborEntity>>

    @Query("SELECT * FROM sabores WHERE activo = 1 ORDER BY nombre COLLATE NOCASE ASC")
    fun observeSaboresActivos(): Flow<List<SaborEntity>>

    @Query("SELECT * FROM sabores WHERE id = :id")
    suspend fun getSabor(id: Long): SaborEntity?

    @Query("SELECT * FROM sabores WHERE nombre = :nombre COLLATE NOCASE AND categoria = :categoria LIMIT 1")
    suspend fun findSaborPorNombre(nombre: String, categoria: String): SaborEntity?

    @Insert
    suspend fun insertSabor(sabor: SaborEntity): Long

    @Update
    suspend fun updateSabor(sabor: SaborEntity)

    @Query("SELECT COUNT(*) FROM detalle_ventas WHERE saborId = :saborId")
    suspend fun contarDetallesPorSabor(saborId: Long): Int

    @Query("SELECT COUNT(*) FROM movimientos_inventario WHERE saborId = :saborId")
    suspend fun contarMovimientosPorSabor(saborId: Long): Int

    @Query("DELETE FROM sabores WHERE id = :saborId")
    suspend fun borrarSabor(saborId: Long)

    @Query("UPDATE sabores SET inventarioActual = inventarioActual + :cantidad WHERE id = :saborId")
    suspend fun sumarInventario(saborId: Long, cantidad: Int)

    @Query("UPDATE sabores SET activo = 0 WHERE id = :saborId")
    suspend fun desactivarSabor(saborId: Long)

    @Query("UPDATE sabores SET activo = 1 WHERE id = :saborId")
    suspend fun reactivarSabor(saborId: Long)

    @Query("UPDATE sabores SET precioVenta = :precio, precioPersonalizado = 1 WHERE id IN (:saborIds)")
    suspend fun actualizarPreciosSeleccion(saborIds: List<Long>, precio: Double)

    @Query("UPDATE sabores SET precioVenta = :precio, precioPersonalizado = 0 WHERE categoria IN (:categorias) AND activo = 1")
    suspend fun actualizarPreciosCategorias(categorias: List<String>, precio: Double)

    @Query("SELECT * FROM promociones ORDER BY activa DESC, creadaEn DESC")
    fun observePromociones(): Flow<List<PromocionEntity>>

    @Transaction
    @Query("SELECT * FROM promociones ORDER BY activa DESC, creadaEn DESC")
    fun observePromocionesConSabores(): Flow<List<PromocionConSabores>>

    @Query("SELECT * FROM promociones WHERE id = :id")
    suspend fun getPromocion(id: Long): PromocionEntity?

    @Query("SELECT * FROM promociones WHERE nombre = :nombre COLLATE NOCASE LIMIT 1")
    suspend fun findPromocionPorNombre(nombre: String): PromocionEntity?

    @Insert
    suspend fun insertPromocion(promocion: PromocionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPromocionSabores(sabores: List<PromocionSaborEntity>)

    @Query("UPDATE promociones SET activa = 0 WHERE id = :promocionId")
    suspend fun desactivarPromocion(promocionId: Long)

    @Query("UPDATE promociones SET activa = 1 WHERE id = :promocionId")
    suspend fun reactivarPromocion(promocionId: Long)

    @Insert
    suspend fun insertVenta(venta: VentaEntity): Long

    @Update
    suspend fun updateVenta(venta: VentaEntity)

    @Insert
    suspend fun insertDetallesVenta(detalles: List<DetalleVentaEntity>)

    @Query("DELETE FROM detalle_ventas WHERE ventaId = :ventaId")
    suspend fun borrarDetallesVenta(ventaId: Long)

    @Query("SELECT * FROM detalle_ventas WHERE ventaId = :ventaId")
    suspend fun getDetallesVenta(ventaId: Long): List<DetalleVentaEntity>

    @Transaction
    @Query("SELECT * FROM ventas WHERE id = :ventaId")
    suspend fun getVentaConDetalles(ventaId: Long): VentaConDetalles?

    @Query("UPDATE ventas SET anulada = 1 WHERE id = :ventaId")
    suspend fun anularVenta(ventaId: Long)

    @Insert
    suspend fun insertMovimiento(movimiento: MovimientoInventarioEntity): Long

    @Query("DELETE FROM movimientos_inventario WHERE tipo = :tipo AND referenciaId = :referenciaId")
    suspend fun borrarMovimientosPorReferencia(tipo: String, referenciaId: Long)

    @Query("UPDATE movimientos_inventario SET anulado = 1 WHERE tipo = :tipo AND referenciaId = :referenciaId")
    suspend fun anularMovimientosPorReferencia(tipo: String, referenciaId: Long)

    @Query("SELECT * FROM movimientos_inventario WHERE id = :movimientoId")
    suspend fun getMovimiento(movimientoId: Long): MovimientoInventarioEntity?

    @Update
    suspend fun updateMovimiento(movimiento: MovimientoInventarioEntity)

    @Query("SELECT * FROM ventas WHERE anulada = 0 AND fechaVenta BETWEEN :inicio AND :fin ORDER BY fechaVenta DESC, fechaRegistro DESC")
    fun observeVentasEntre(inicio: Long, fin: Long): Flow<List<VentaEntity>>

    @Transaction
    @Query("SELECT * FROM ventas WHERE anulada = 0 AND fechaVenta BETWEEN :inicio AND :fin ORDER BY fechaVenta DESC, fechaRegistro DESC")
    fun observeVentasConDetallesEntre(inicio: Long, fin: Long): Flow<List<VentaConDetalles>>

    @Query("SELECT * FROM movimientos_inventario WHERE anulado = 0 ORDER BY fechaMovimiento DESC, fechaRegistro DESC LIMIT :limite")
    fun observeMovimientosRecientes(limite: Int = 80): Flow<List<MovimientoInventarioEntity>>

    @Query("SELECT * FROM movimientos_inventario WHERE anulado = 0 AND fechaMovimiento BETWEEN :inicio AND :fin ORDER BY fechaMovimiento DESC, fechaRegistro DESC")
    fun observeMovimientosEntre(inicio: Long, fin: Long): Flow<List<MovimientoInventarioEntity>>
}
