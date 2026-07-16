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
        PromocionReglaEntity::class,
        PromocionReglaSaborEntity::class,
        VentaEntity::class,
        DetalleVentaEntity::class,
        MovimientoInventarioEntity::class
    ],
    version = 3,
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE promociones ADD COLUMN tipo TEXT NOT NULL DEFAULT 'FIJA'"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS promocion_reglas (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "promocionId INTEGER NOT NULL, " +
                        "alcance TEXT NOT NULL, " +
                        "categoria TEXT, " +
                        "cantidad INTEGER NOT NULL, " +
                        "permiteRepetir INTEGER NOT NULL, " +
                        "orden INTEGER NOT NULL, " +
                        "FOREIGN KEY(promocionId) REFERENCES promociones(id) ON DELETE CASCADE"
                        + ")"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_promocion_reglas_promocionId " +
                        "ON promocion_reglas(promocionId)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS promocion_regla_sabores (" +
                        "reglaId INTEGER NOT NULL, " +
                        "promocionId INTEGER NOT NULL, " +
                        "saborId INTEGER NOT NULL, " +
                        "PRIMARY KEY(reglaId, saborId), " +
                        "FOREIGN KEY(reglaId) REFERENCES promocion_reglas(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(promocionId) REFERENCES promociones(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(saborId) REFERENCES sabores(id) ON DELETE RESTRICT"
                        + ")"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_promocion_regla_sabores_saborId " +
                        "ON promocion_regla_sabores(saborId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_promocion_regla_sabores_promocionId " +
                        "ON promocion_regla_sabores(promocionId)"
                )
            }
        }
    }
}
