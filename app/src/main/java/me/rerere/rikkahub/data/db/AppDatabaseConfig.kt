package me.rerere.rikkahub.data.db

import androidx.room.migration.Migration
import me.rerere.rikkahub.data.db.migrations.Migration_11_12
import me.rerere.rikkahub.data.db.migrations.Migration_13_14
import me.rerere.rikkahub.data.db.migrations.Migration_14_15
import me.rerere.rikkahub.data.db.migrations.Migration_15_16
import me.rerere.rikkahub.data.db.migrations.Migration_17_18
import me.rerere.rikkahub.data.db.migrations.Migration_18_19
import me.rerere.rikkahub.data.db.migrations.Migration_19_20
import me.rerere.rikkahub.data.db.migrations.Migration_20_21
import me.rerere.rikkahub.data.db.migrations.Migration_21_22
import me.rerere.rikkahub.data.db.migrations.Migration_22_23
import me.rerere.rikkahub.data.db.migrations.Migration_23_24
import me.rerere.rikkahub.data.db.migrations.Migration_24_25
import me.rerere.rikkahub.data.db.migrations.Migration_6_7

const val APP_DATABASE_VERSION = 25

val APP_DATABASE_MANUAL_MIGRATIONS: Array<Migration> = arrayOf(
    Migration_6_7,
    Migration_11_12,
    Migration_13_14,
    Migration_14_15,
    Migration_15_16,
    Migration_17_18,
    Migration_18_19,
    Migration_19_20,
    Migration_20_21,
    Migration_21_22,
    Migration_22_23,
    Migration_23_24,
    Migration_24_25
)
