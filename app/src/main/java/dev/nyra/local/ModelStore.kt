package dev.nyra.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

class ModelStore(private val context: Context) {
    val directory = File(context.filesDir, "models").apply { mkdirs() }
    private val reserveBytes = 256L * 1024 * 1024

    fun installed(): List<File> = directory.listFiles()
        ?.filter { it.extension.equals("litertlm", true) }
        ?.sortedBy { it.name }
        ?: emptyList()

    fun partialBytes(model: CatalogModel): Long = downloadPart(model).takeIf { it.isFile }?.length() ?: 0L

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
        require(total == null || total!! < directory.usableSpace - reserveBytes) { "Armazenamento insuficiente para copiar o modelo." }

        val part = File(directory, UUID.randomUUID().toString() + ".importpart")
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            resolver.openInputStream(uri).use { source ->
                checkNotNull(source) { "Não foi possível abrir o arquivo." }
                part.outputStream().use { target ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        check(directory.usableSpace > reserveBytes + count) { "Espaço livre insuficiente." }
                        target.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        copied += count
                        progress(copied, total)
                    }
                    target.fd.sync()
                }
            }
            check(copied > 0 && (total == null || total == copied)) { "Arquivo incompleto." }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val safeName = name.removeSuffix(".litertlm")
                .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
                .take(64)
            val dest = File(directory, "$safeName-${hash.take(12)}.litertlm")
            if (dest.exists()) {
                part.delete()
                return@withContext dest
            }
            check(part.renameTo(dest)) { "Falha ao concluir a importação." }
            dest
        } finally {
            part.delete()
        }
    }

    /**
     * Downloads a catalog model directly into Nyra's private storage.
     * A stable .downloadpart file is kept on cancellation/network failure so the next attempt can
     * resume with an HTTP Range request. The final file is only exposed after size + SHA-256 pass.
     */
    suspend fun download(model: CatalogModel, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        require(model.fileName.endsWith(".litertlm", true)) { "Entrada de catálogo inválida." }
        val dest = File(directory, model.fileName)
        val part = downloadPart(model)

        if (dest.isFile) {
            if (dest.length() == model.sizeBytes && sha256(dest).equals(model.sha256, true)) {
                progress(model.sizeBytes, model.sizeBytes)
                return@withContext dest
            }
            dest.delete()
        }

        if (part.length() > model.sizeBytes) part.delete()
        var offset = part.length()
        val remaining = model.sizeBytes - offset
        require(remaining <= directory.usableSpace - reserveBytes) {
            "Espaço insuficiente. Libere pelo menos ${(remaining + reserveBytes) / 1048576} MB e tente novamente."
        }
        progress(offset, model.sizeBytes)

        var connection = openConnection(model.url, offset)
        try {
            // Some CDNs ignore Range. In that case restart cleanly instead of appending a full file.
            if (offset > 0 && connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                part.delete()
                offset = 0L
                connection = openConnection(model.url, 0L)
            }

            val code = connection.responseCode
            val validResponse = code == HttpURLConnection.HTTP_OK || (offset > 0 && code == HttpURLConnection.HTTP_PARTIAL)
            check(validResponse) {
                when (code) {
                    HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                        "O servidor recusou o download deste modelo."
                    HttpURLConnection.HTTP_NOT_FOUND -> "O modelo não foi encontrado no servidor."
                    else -> "Falha no download (HTTP $code)."
                }
            }

            connection.inputStream.use { source ->
                FileOutputStream(part, offset > 0).use { target ->
                    val buffer = ByteArray(1024 * 1024)
                    var done = offset
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        check(done + count <= model.sizeBytes) { "O servidor enviou um arquivo maior que o esperado." }
                        check(directory.usableSpace > reserveBytes + count) { "Espaço livre insuficiente durante o download." }
                        target.write(buffer, 0, count)
                        done += count
                        progress(done, model.sizeBytes)
                    }
                    target.fd.sync()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            connection.disconnect()
        }

        check(part.length() == model.sizeBytes) {
            "Download incompleto. Toque em Retomar para continuar de ${part.length() / 1048576} MB."
        }

        val actualHash = sha256(part)
        if (!actualHash.equals(model.sha256, true)) {
            part.delete()
            error("O arquivo baixado falhou na verificação SHA-256. O download parcial foi descartado por segurança.")
        }

        if (dest.exists()) dest.delete()
        check(part.renameTo(dest)) { "Falha ao instalar o modelo após o download." }
        progress(model.sizeBytes, model.sizeBytes)
        dest
    }

    fun cleanInterruptedImports() {
        directory.listFiles()?.filter { it.extension == "importpart" || it.extension == "part" }?.forEach { it.delete() }
    }

    private fun downloadPart(model: CatalogModel): File = File(directory, ".${model.id}.downloadpart")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1024 * 1024).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun openConnection(rawUrl: String, offset: Long): HttpURLConnection {
        var url = URL(rawUrl)
        repeat(8) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Nyra-Android/0.1.1")
                setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }
            val code = connection.responseCode
            if (code in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                    ?: error("Redirecionamento de download sem destino.")
                connection.disconnect()
                url = URL(url, location)
            } else {
                return connection
            }
        }
        error("O servidor redirecionou o download vezes demais.")
    }
}
