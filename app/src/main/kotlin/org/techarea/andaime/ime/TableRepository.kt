package org.techarea.andaime.ime

import android.content.Context
import org.techarea.andaime.engine.CinTable
import org.techarea.andaime.engine.DecodeRules
import org.techarea.andaime.engine.LayeredDictionary
import org.techarea.andaime.engine.SymbolTable
import org.techarea.andaime.engine.UserDictionary
import java.io.File
import java.util.Properties

/**
 * A code table the user imported. The app ships no tables (their licences
 * don't allow bundling them into an Apache-2.0 app); users download a .cin
 * from PIME or elsewhere and import it.
 */
data class InstalledTable(
    val id: String,
    val name: String,
    val codes: Int,
    val maxCodeLength: Int,
    /** Decode like PIME's chedayi (space + ' [ ] - \, 大易符號 with =); otherwise follow the .cin's own settings. */
    val pimeDayi: Boolean,
)

/**
 * Process-wide store of imported tables and the user's edits. The IME service
 * and the dictionary screen run in the same process, so an import or an edit
 * is what the keyboard uses next.
 *
 * Layout under `files/`: `tables/<id>.cin` (normalised to UTF-8),
 * `tables/<id>.properties` (name, flags) and `user-<id>.txt` (edits).
 */
object TableRepository {
    private val loaded = HashMap<String, LayeredDictionary>()
    private var symbols: SymbolTable? = null

    /** Bumped whenever the symbol table changes, so the keyboard knows to rebuild its engine. */
    @Volatile
    var symbolsVersion = 0
        private set

    /**
     * 大易符號 (`=` + key), used by tables decoded the PIME Dayi way: the
     * user's imported list if there is one, otherwise Andaime's built-in list.
     */
    @Synchronized
    fun symbols(context: Context): SymbolTable = symbols ?: run {
        val custom = customSymbolsFile(context)
        val reader = if (custom.exists()) custom.reader(Charsets.UTF_8)
        else context.applicationContext.assets.open("dayi-symbols.txt").reader(Charsets.UTF_8)
        SymbolTable.read(reader)
    }.also { symbols = it }

    fun hasCustomSymbols(context: Context): Boolean = customSymbolsFile(context).exists()

    /** The current list (custom or built-in), with a header explaining the format for editing. */
    fun exportSymbols(context: Context, out: java.io.OutputStream) {
        out.writer(Charsets.UTF_8).use { symbols(context).write(it) }
    }

    /** Replaces the list with the user's file; null if it holds no `key<TAB>symbols` lines. */
    @Synchronized
    fun importSymbols(context: Context, bytes: ByteArray): SymbolTable? {
        val table = SymbolTable.read(CinTable.decode(bytes).reader())
        if (table.size == 0) return null
        customSymbolsFile(context).writer(Charsets.UTF_8).use(table::write)
        symbols = table
        symbolsVersion++
        return table
    }

    /** Back to Andaime's built-in list. */
    @Synchronized
    fun resetSymbols(context: Context) {
        customSymbolsFile(context).delete()
        symbols = null
        symbolsVersion++
    }

    private fun customSymbolsFile(context: Context) = File(context.applicationContext.filesDir, "dayi-symbols.txt")

    @Synchronized
    fun installed(context: Context): List<InstalledTable> =
        tablesDir(context).listFiles { f -> f.name.endsWith(".properties") }.orEmpty()
            .mapNotNull { readInfo(it) }
            .sortedBy { it.name }

    fun info(context: Context, id: String): InstalledTable? =
        readInfo(File(tablesDir(context), "$id.properties"))

    @Synchronized
    fun get(context: Context, id: String): LayeredDictionary? {
        loaded[id]?.let { return it }
        val file = File(tablesDir(context), "$id.cin")
        if (!file.exists()) return null
        val base = file.reader(Charsets.UTF_8).let(CinTable::parse)
        val userFile = userFile(context, id)
        val user = if (userFile.exists()) userFile.reader(Charsets.UTF_8).let(UserDictionary::read) else UserDictionary()
        return LayeredDictionary(base, user).also { loaded[id] = it }
    }

    fun rules(info: InstalledTable, table: CinTable): DecodeRules =
        if (info.pimeDayi) DecodeRules.pimeDayi(info.maxCodeLength) else DecodeRules.fromCin(table)

    /** Parses a candidate file without installing it, so the user can confirm first. */
    fun preview(bytes: ByteArray, fileName: String?): ImportPreview? {
        val table = runCatching { CinTable.parse(bytes) }.getOrNull() ?: return null
        if (table.size == 0) return null
        val name = table.cname.ifBlank { fileName?.substringBeforeLast('.').orEmpty() }.ifBlank { "未命名碼表" }
        val looksDayi = "大易" in table.cname || table.ename.contains("dayi", ignoreCase = true) ||
            fileName.orEmpty().contains("dayi", ignoreCase = true)
        return ImportPreview(table, name, looksDayi, fileName)
    }

    @Synchronized
    fun install(context: Context, preview: ImportPreview, name: String, pimeDayi: Boolean): InstalledTable {
        val dir = tablesDir(context).apply { mkdirs() }
        val id = uniqueId(dir, slug(preview.fileName ?: preview.table.ename.ifBlank { name }))
        // Store our own UTF-8 copy so later loads don't depend on the original encoding
        File(dir, "$id.cin").writer(Charsets.UTF_8).use { w -> preview.table.writeCin(w) }
        val info = InstalledTable(id, name.ifBlank { preview.name }, preview.table.size, preview.table.maxCodeLength, pimeDayi)
        writeInfo(dir, info)
        return info
    }

    @Synchronized
    fun setPimeDayi(context: Context, id: String, enabled: Boolean) {
        val info = info(context, id) ?: return
        writeInfo(tablesDir(context), info.copy(pimeDayi = enabled))
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        val dir = tablesDir(context)
        File(dir, "$id.cin").delete()
        File(dir, "$id.properties").delete()
        userFile(context, id).delete()
        loaded.remove(id)
    }

    @Synchronized
    fun saveUserEdits(context: Context, id: String) {
        val dict = loaded[id] ?: return
        val file = userFile(context, id)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writer(Charsets.UTF_8).use(dict.user::write)
        tmp.renameTo(file)
    }

    /** This table's edits as an editable text file (header explains the format). */
    fun exportUserEdits(context: Context, id: String, out: java.io.OutputStream) {
        val dict = get(context, id) ?: return
        out.writer(Charsets.UTF_8).use { dict.user.write(it) }
    }

    /**
     * Replaces all of this table's edits with the file's; returns how many codes
     * it holds, or null if it has no `code<TAB>...` lines at all.
     */
    @Synchronized
    fun importUserEdits(context: Context, id: String, bytes: ByteArray): Int? {
        val dict = get(context, id) ?: return null
        val imported = UserDictionary.read(CinTable.decode(bytes).reader())
        if (imported.entries.isEmpty()) return null
        dict.user.replaceAll(imported)
        saveUserEdits(context, id)
        return imported.entries.size
    }

    @Synchronized
    fun clearUserEdits(context: Context, id: String) {
        val dict = get(context, id) ?: return
        dict.user.clear()
        saveUserEdits(context, id)
    }

    private fun tablesDir(context: Context) = File(context.applicationContext.filesDir, "tables")

    /** Plain text so it can be pulled with adb run-as and backed up. */
    private fun userFile(context: Context, id: String) = File(context.applicationContext.filesDir, "user-$id.txt")

    private fun readInfo(file: File): InstalledTable? {
        if (!file.exists()) return null
        val p = Properties().apply { file.reader(Charsets.UTF_8).use(::load) }
        return InstalledTable(
            id = file.name.removeSuffix(".properties"),
            name = p.getProperty("name") ?: return null,
            codes = p.getProperty("codes")?.toIntOrNull() ?: 0,
            maxCodeLength = p.getProperty("maxCodeLength")?.toIntOrNull() ?: 4,
            pimeDayi = p.getProperty("pimeDayi").toBoolean(),
        )
    }

    private fun writeInfo(dir: File, info: InstalledTable) {
        val p = Properties().apply {
            setProperty("name", info.name)
            setProperty("codes", info.codes.toString())
            setProperty("maxCodeLength", info.maxCodeLength.toString())
            setProperty("pimeDayi", info.pimeDayi.toString())
        }
        File(dir, "${info.id}.properties").writer(Charsets.UTF_8).use { p.store(it, null) }
    }

    private fun slug(source: String): String =
        source.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "table" }

    private fun uniqueId(dir: File, base: String): String {
        var id = base
        var n = 2
        while (File(dir, "$id.cin").exists()) id = "$base-${n++}"
        return id
    }
}

class ImportPreview(
    val table: CinTable,
    val name: String,
    /** Guess for the PIME Dayi decoding switch; the user can change it before installing. */
    val looksDayi: Boolean,
    val fileName: String?,
)
