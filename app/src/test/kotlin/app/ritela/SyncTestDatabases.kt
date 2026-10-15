package app.ritela

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import app.ritela.data.PartnerCodec
import app.ritela.data.RitelaDatabase
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal val syncMigrations get() = arrayOf(
    RitelaDatabase.MIGRATION_1_2,
    RitelaDatabase.MIGRATION_2_3,
    RitelaDatabase.MIGRATION_3_4,
    RitelaDatabase.MIGRATION_4_5,
    RitelaDatabase.MIGRATION_5_6,
    RitelaDatabase.MIGRATION_6_7,
    RitelaDatabase.MIGRATION_7_8
)

internal fun syncTestDatabase(context: Context, name: String): RitelaDatabase =
    Room.databaseBuilder(context, RitelaDatabase::class.java, name)
        .addMigrations(*syncMigrations).build()

internal fun legacySyncDatabase(
    context: Context,
    name: String,
    version: Int,
    populate: (SQLiteDatabase, List<String>) -> Unit = { _, _ -> }
) {
    val schema = JSONObject(
        File("schemas/app.ritela.data.RitelaDatabase/$version.json").readText()
    ).getJSONObject("database")
    val entities = PartnerCodec.objects(schema.getJSONArray("entities"))
    val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
    SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
        entities.forEach { entity ->
            val table = entity.getString("tableName")
            old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            PartnerCodec.objects(entity.optJSONArray("indices") ?: JSONArray()).forEach {
                old.execSQL(it.getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        populate(old, entities.map { it.getString("tableName") })
        old.version = version
    }
}

internal fun copyLegacySyncRows(context: Context, name: String, source: SupportSQLiteDatabase) =
    legacySyncDatabase(context, name, 6) { old, tables ->
        tables.forEach { table ->
            source.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                while (cursor.moveToNext()) {
                    val columns = cursor.columnNames.joinToString(",") { "`$it`" }
                    val values = Array<Any?>(cursor.columnCount) { index ->
                        when (cursor.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                            else -> cursor.getString(index)
                        }
                    }
                    old.execSQL(
                        "INSERT INTO `$table` ($columns) VALUES (${values.joinToString(",") {
                            "?"
                        }})",
                        values
                    )
                }
            }
        }
    }

internal fun syncCursorRows(cursor: Cursor): List<List<String?>> = cursor.use {
    buildList {
        while (it.moveToNext()) {
            add(
                List(it.columnCount) { column ->
                    if (it.isNull(column)) null else it.getString(column)
                }
            )
        }
    }
}
