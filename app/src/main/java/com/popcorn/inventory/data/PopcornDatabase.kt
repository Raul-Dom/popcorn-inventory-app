package com.popcorn.inventory.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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
            ).addMigrations(MIGRATION_1_2).build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE promocion_sabores ADD COLUMN cantidad INTEGER NOT NULL DEFAULT 1"
                )
                database.execSQL(
                    "UPDATE promocion_sabores SET cantidad = " +
                        "(SELECT cantidadUnidades FROM promociones " +
                        "WHERE promociones.id = promocion_sabores.promocionId) " +
                        "WHERE promocionId IN " +
                        "(SELECT promocionId FROM promocion_sabores GROUP BY promocionId HAVING COUNT(*) = 1)"
                )
            }
        }
    }
}
