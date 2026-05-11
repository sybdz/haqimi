package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val GEN_MEDIA_TABLE = "GenMediaEntity"
private const val GEN_MEDIA_TYPE_COLUMN = "type"
private const val GEN_MEDIA_SOURCE_PATHS_COLUMN = "source_paths"
private const val DEFAULT_GEN_MEDIA_TYPE = "image_generation"

val Migration_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(22, 23)
        try {
            if (!hasColumn(db, GEN_MEDIA_TABLE, GEN_MEDIA_TYPE_COLUMN)) {
                db.execSQL(
                    """
                    ALTER TABLE $GEN_MEDIA_TABLE
                    ADD COLUMN $GEN_MEDIA_TYPE_COLUMN TEXT NOT NULL DEFAULT '$DEFAULT_GEN_MEDIA_TYPE'
                    """.trimIndent()
                )
            }
            if (!hasColumn(db, GEN_MEDIA_TABLE, GEN_MEDIA_SOURCE_PATHS_COLUMN)) {
                db.execSQL(
                    """
                    ALTER TABLE $GEN_MEDIA_TABLE
                    ADD COLUMN $GEN_MEDIA_SOURCE_PATHS_COLUMN TEXT
                    """.trimIndent()
                )
            }
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
    val cursor = db.query("PRAGMA table_info('$tableName')")
    return try {
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex == -1) return false
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == columnName) {
                return true
            }
        }
        false
    } finally {
        cursor.close()
    }
}
