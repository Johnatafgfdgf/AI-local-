package dev.nyra.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Metadata that Nyra can safely expose without keeping the whole GLB JSON in memory. */
data class AvatarDescriptor(
    val file: File,
    val title: String,
    val author: String,
    val version: String,
    val humanoidBones: Int,
    val blendShapes: Int,
    val isVrm: Boolean,
    val sourceName: String
)

/**
 * Owns the private avatar copy used by the native renderer.
 *
 * The import is atomic. A selected file is validated as GLB/VRM before it replaces the current
 * avatar, so a broken import can never destroy the previously working avatar.
 */
class AvatarStore(private val context: Context) {
    private val directory = File(context.filesDir, "avatar").apply { mkdirs() }
    private val currentFile = File(directory, "nyra.vrm.glb")
    private val reserveBytes = 128L * 1024L * 1024L

    suspend fun current(): AvatarDescriptor? = withContext(Dispatchers.IO) {
        if (!currentFile.isFile || currentFile.length() <= 20L) return@withContext null
        runCatching { inspect(currentFile, currentFile.name) }.getOrNull()
    }

    suspend fun import(uri: Uri, progress: (Long, Long?) -> Unit): AvatarDescriptor = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = "avatar.vrm"
        var total: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                displayName = cursor.getString(0) ?: displayName
                if (!cursor.isNull(1)) total = cursor.getLong(1).takeIf { it > 0L }
            }
        }

        val lower = displayName.lowercase()
        require(lower.endsWith(".vrm") || lower.endsWith(".glb")) {
            "Escolha um avatar VRM ou GLB."
        }
        total?.let {
            require(it < directory.usableSpace - reserveBytes) {
                "Não há armazenamento livre suficiente para importar o avatar."
            }
        }

        val part = File(directory, ".avatar-import.part")
        part.delete()
        var copied = 0L
        try {
            resolver.openInputStream(uri).use { input ->
                checkNotNull(input) { "Não foi possível abrir o avatar selecionado." }
                part.outputStream().buffered(1024 * 1024).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(directory.usableSpace > reserveBytes + count) { "Armazenamento insuficiente durante a importação." }
                        output.write(buffer, 0, count)
                        copied += count
                        progress(copied, total)
                    }
                    output.flush()
                }
            }
            check(copied > 20L && (total == null || copied == total)) { "O arquivo do avatar ficou incompleto." }
            val descriptor = inspect(part, displayName)
            require(descriptor.isVrm) {
                "Este GLB não contém a extensão VRM. Escolha um arquivo .vrm compatível."
            }

            val backup = File(directory, ".nyra.vrm.backup")
            backup.delete()
            if (currentFile.exists()) check(currentFile.renameTo(backup)) { "Falha ao preparar a troca do avatar." }
            try {
                check(part.renameTo(currentFile)) { "Falha ao concluir a importação do avatar." }
                backup.delete()
            } catch (error: Throwable) {
                currentFile.delete()
                backup.renameTo(currentFile)
                throw error
            }
            descriptor.copy(file = currentFile)
        } finally {
            part.delete()
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        currentFile.delete()
    }

    /** Reads only the GLB JSON chunk; the embedded textures and mesh payload never get duplicated. */
    private fun inspect(file: File, sourceName: String): AvatarDescriptor {
        RandomAccessFile(file, "r").use { raf ->
            require(raf.length() >= 20L) { "Arquivo de avatar pequeno demais." }
            val header = ByteArray(20)
            raf.readFully(header)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val magic = bb.int
            val version = bb.int
            val totalLength = bb.int.toLong() and 0xffffffffL
            val jsonLength = bb.int
            val jsonType = bb.int
            require(magic == 0x46546C67) { "Avatar inválido: cabeçalho GLB ausente." }
            require(version == 2) { "Avatar inválido: apenas glTF/GLB 2.0 é suportado." }
            require(totalLength <= raf.length() && totalLength > 20L) { "Avatar GLB truncado." }
            require(jsonType == 0x4E4F534A && jsonLength in 2..8_000_000) { "Avatar GLB sem bloco JSON válido." }

            val jsonBytes = ByteArray(jsonLength)
            raf.readFully(jsonBytes)
            val root = JSONObject(String(jsonBytes, Charsets.UTF_8).trimEnd('\u0000', ' ', '\n', '\r', '\t'))
            val extensions = root.optJSONObject("extensions")
            val vrm = extensions?.optJSONObject("VRM")
            val meta = vrm?.optJSONObject("meta")
            val bones = vrm?.optJSONObject("humanoid")?.optJSONArray("humanBones")?.length() ?: 0
            val shapes = vrm?.optJSONObject("blendShapeMaster")?.optJSONArray("blendShapeGroups")?.length() ?: 0

            return AvatarDescriptor(
                file = file,
                title = meta?.optString("title")?.takeIf { it.isNotBlank() } ?: "Avatar",
                author = meta?.optString("author")?.takeIf { it.isNotBlank() } ?: "Autor não informado",
                version = meta?.optString("version")?.takeIf { it.isNotBlank() } ?: "—",
                humanoidBones = bones,
                blendShapes = shapes,
                isVrm = vrm != null,
                sourceName = sourceName
            )
        }
    }
}
