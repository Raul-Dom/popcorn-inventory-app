package com.popcorn.inventory.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SaborEntity::class,
        ConfiguracionEntity::class,
        PromocionEntity::class,
        PromocionSaborEntity::class,
        VentaEntity::class,
        DetalleVentaEntity::class,
        MovimientoInventarioEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PopcornDatabase : RoomDatabase() {
    abstract fun dao(): PopcornDao

    companion object {
        fun create(context: Context): PopcornDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PopcornDatabase::class.java,
                "inventario_palomitas.db"
            ).build()
        }
    }
}
