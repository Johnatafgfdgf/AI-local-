package dev.nyra.local

data class CatalogModel(
    val id: String,
    val title: String,
    val description: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val contextTokens: Int,
    val license: String,
    val recommended: Boolean = false
)

/**
 * Curated models that Nyra can download without requiring the user to handle files manually.
 *
 * Keep each entry pinned to an exact filename, size and SHA-256. The downloader refuses to
 * install a file that does not match these values.
 */
object ModelCatalog {
    val models: List<CatalogModel> = listOf(
        CatalogModel(
            id = "qwen3-0.6b-dynamic-int4",
            title = "Qwen3 0.6B INT4",
            description = "Recomendado para começar • ~328 MB • contexto de 4096 tokens • funciona localmente após o download",
            fileName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm?download=true",
            sizeBytes = 344_437_808L,
            sha256 = "e3e290109da4388d65a17510a0c66af91c8039f52d2c465868dbc43c09a776cf",
            contextTokens = 4096,
            license = "Apache-2.0",
            recommended = true
        )
    )

    val recommended: CatalogModel get() = models.first { it.recommended }
}
