package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(23, 24)
        try {
            if (!hasColumn(db, "ConversationEntity", "custom_system_prompt")) {
                db.execSQL(
                    """
                    ALTER TABLE ConversationEntity
                    ADD COLUMN custom_system_prompt TEXT NOT NULL DEFAULT ''
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
