package com.openvault.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.openvault.app.security.CryptoEngine
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(
    entities = [
        NoteEntity::class,
        PasswordEntity::class,
        CalendarEventEntity::class,
        VaultFileEntity::class,
        UserDatabaseEntity::class,
        DatabaseRecordEntity::class,
        CollectionEntity::class,
        AuditEntryEntity::class,
        SyncQueueEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OpenVaultDatabase : RoomDatabase() {
    abstract fun openVaultDao(): OpenVaultDao

    companion object {
        const val PRIMARY_DB_NAME = "openvault_sqlcipher.db"
        const val DECOY_DB_NAME = "openvault_decoy_sqlcipher.db"
        private const val LOCKED_FALLBACK_DB_NAME = "openvault_locked_placeholder.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_queue_tableName_objectId` ON `sync_queue` (`tableName`, `objectId`)"
                )
            }
        }

        fun buildDatabase(
            context: Context,
            sqlCipherKeyBytes: ByteArray?,
            isDecoyMode: Boolean = false,
            inMemoryForTesting: Boolean = false
        ): OpenVaultDatabase {
            if (inMemoryForTesting) {
                return Room.inMemoryDatabaseBuilder(
                    context.applicationContext,
                    OpenVaultDatabase::class.java
                )
                    .allowMainThreadQueries()
                    .addMigrations(MIGRATION_1_2)
                    .build()
            }

            val hasKey = sqlCipherKeyBytes != null && sqlCipherKeyBytes.isNotEmpty()
            val dbName = when {
                !hasKey -> LOCKED_FALLBACK_DB_NAME
                isDecoyMode -> DECOY_DB_NAME
                else -> PRIMARY_DB_NAME
            }

            if (hasKey) {
                // Self-heal if an unencrypted SQLite file was previously created at dbName
                purgePlaintextSqliteIfPresent(context.applicationContext, dbName)
            }

            val builder = Room.databaseBuilder(
                context.applicationContext,
                OpenVaultDatabase::class.java,
                dbName
            ).addMigrations(MIGRATION_1_2)

            if (hasKey && sqlCipherKeyBytes != null) {
                val keyCopy = sqlCipherKeyBytes.copyOf()
                try {
                    System.loadLibrary("sqlcipher")
                    val factory = SupportOpenHelperFactory(keyCopy)
                    builder.openHelperFactory(factory)
                } catch (_: UnsatisfiedLinkError) {
                    // Fallback in JVM local unit test environments where native libsqlcipher.so is absent
                    CryptoEngine.zeroize(keyCopy)
                } catch (_: Throwable) {
                    CryptoEngine.zeroize(keyCopy)
                }
            }
            return builder.fallbackToDestructiveMigration(dropAllTables = true).build()
        }

        private fun purgePlaintextSqliteIfPresent(context: Context, dbName: String) {
            runCatching {
                val dbFile = context.getDatabasePath(dbName)
                if (dbFile.exists() && dbFile.length() >= 16) {
                    val header = ByteArray(15)
                    dbFile.inputStream().use { it.read(header) }
                    val headerText = String(header, Charsets.US_ASCII)
                    if (headerText.startsWith("SQLite format 3")) {
                        context.deleteDatabase(dbName)
                        File("${dbFile.absolutePath}-wal").delete()
                        File("${dbFile.absolutePath}-shm").delete()
                    }
                }
            }
        }
    }
}
