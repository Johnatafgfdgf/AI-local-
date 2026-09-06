package dev.nyra.local

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

interface InferenceBackend {
    suspend fun load(file: File)
    suspend fun generate(system: String, history: List<ContextManager.Line>, input: String, onText: (String) -> Unit)
    fun interrupt()
    suspend fun unload()
}

/** One active native operation. Cancellation waits for the native terminal callback before close. */
class LiteRtBackend(private val cache: File) : InferenceBackend {
    private val mutex = Mutex()
    private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var cancelled = false
    var loadedPath: String? = null; private set

    override suspend fun load(file: File) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loadedPath == file.path && engine != null) return@withLock
            engine?.close(); engine = null; loadedPath = null
            check(file.isFile && file.length() > 0) { "Importe um modelo .litertlm válido." }
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
            val candidate = Engine(EngineConfig(modelPath = file.path, backend = Backend.CPU(), maxNumTokens = 4096, cacheDir = cache.path))
            candidate.initialize()
            engine = candidate; loadedPath = file.path
        }
    }

    override suspend fun generate(system: String, history: List<ContextManager.Line>, input: String, onText: (String) -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            cancelled = false
            val current = checkNotNull(engine) { "O modelo ainda não está carregado." }
            val conv = current.createConversation(ConversationConfig(
                systemInstruction = Contents.of(system),
                initialMessages = history.map { if (it.role == "user") Message.user(it.text) else Message.model(it.text) }
            ))
            conversation = conv
            val terminal = CompletableDeferred<Unit>()
            var nativeStarted = false
            try {
                currentCoroutineContext().ensureActive()
                conv.sendMessageAsync(input, object : MessageCallback {
                    override fun onMessage(message: Message) { if (!cancelled) onText(message.text) }
                    override fun onDone() { terminal.complete(Unit) }
                    override fun onError(throwable: Throwable) { terminal.completeExceptionally(throwable) }
                }, maxOutputToken = 512)
                nativeStarted = true
                if (cancelled) conv.cancelProcess()
                terminal.await()
            } catch (error: CancellationException) {
                cancelled = true
                conv.cancelProcess()
                // Freeing JNI resources while native callbacks are active is unsafe.
                if (nativeStarted) withContext(NonCancellable) { runCatching { terminal.await() } }
                throw error
            } finally {
                conversation = null
                conv.close()
            }
        }
    }
    override fun interrupt() { cancelled = true; conversation?.let { runCatching { it.cancelProcess() } } }
    override suspend fun unload() = withContext(Dispatchers.IO) {
        mutex.withLock { engine?.close(); engine = null; loadedPath = null }
    }
}
