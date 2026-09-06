package dev.nyra.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class ModelStore(private val context: Context) {
    val directory = File(context.filesDir, "models").apply { mkdirs() }
    fun installed(): List<File> = directory.listFiles()?.filter { it.extension == "litertlm" }?.sortedBy { it.name } ?: emptyList()
    suspend fun import(uri: Uri, progress: (Long, Long?) -> Unit): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var total: Long? = null
        var name = "modelo.litertlm"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) {
                name = it.getString(0) ?: name
                if (!it.isNull(1)) total = it.getLong(1).takeIf { size -> size > 0 }
            }
        }
        require(name.endsWith(".litertlm", true)) { "Este backend aceita .litertlm. GGUF ainda não é compatível." }
        val reserve = 256L * 1024 * 1024
        require(total == null || total!! < directory.usableSpace - reserve) { "Armazenamento insuficiente para copiar o modelo." }
        val part = File(directory, UUID.randomUUID().toString() + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            resolver.openInputStream(uri).use { source ->
                checkNotNull(source) { "Não foi possível abrir o arquivo." }
                part.outputStream().use { target ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer); if (count < 0) break
                        check(directory.usableSpace > reserve + count) { "Espaço livre insuficiente." }
                        target.write(buffer, 0, count); digest.update(buffer, 0, count)
                        copied += count; progress(copied, total)
                    }
                    target.fd.sync()
                }
            }
            check(copied > 0 && (total == null || total == copied)) { "Arquivo incompleto." }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val safeName = name.removeSuffix(".litertlm").replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(64)
            val dest = File(directory, "$safeName-${hash.take(12)}.litertlm")
            if (dest.exists()) { part.delete(); return@withContext dest }
            check(part.renameTo(dest)) { "Falha ao concluir a importação." }
            dest
        } finally { part.delete() }
    }
    fun cleanInterruptedImports() { directory.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() } }
}
