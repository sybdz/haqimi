package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(24, 25)
        try {
            if (!hasColumn(db, "ConversationEntity", "mode_injection_ids")) {
                db.execSQL(
                    """
                    ALTER TABLE ConversationEntity
                    ADD COLUMN mode_injection_ids TEXT NOT NULL DEFAULT '[]'
                    """.trimIndent()
                )
            }
            if (!hasColumn(db, "ConversationEntity", "lorebook_ids")) {
                db.execSQL(
                    """
                    ALTER TABLE ConversationEntity
                    ADD COLUMN lorebook_ids TEXT NOT NULL DEFAULT '[]'
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
