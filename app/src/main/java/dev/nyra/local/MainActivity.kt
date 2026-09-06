package dev.nyra.local

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFBEA0FF),
                    onPrimary = Color(0xFF281346),
                    background = Color(0xFF09070F),
                    surface = Color(0xFF15101F),
                    surfaceVariant = Color(0xFF251B35)
                )
            ) {
                NyraApp(viewModel())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NyraApp(vm: NyraViewModel) {
    val context = LocalContext.current
    val onboarded by vm.onboarded.collectAsStateWithLifecycle()
    if (!onboarded) {
        Welcome(vm::finishOnboarding)
        return
    }

    val chats by vm.chats.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val responseMode by vm.responseMode.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val importing by vm.importBusy.collectAsStateWithLifecycle()
    val avatarImporting by vm.avatarImportBusy.collectAsStateWithLifecycle()
    val download by vm.downloadState.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val stream by vm.stream.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val memoryEnabled by vm.memoryEnabled.collectAsStateWithLifecycle()
    val voiceStatus by vm.voiceStatus.collectAsStateWithLifecycle()
    val voiceViseme by vm.voiceViseme.collectAsStateWithLifecycle()
    val speechStatus by vm.speechStatus.collectAsStateWithLifecycle()
    val speechListening by vm.speechListening.collectAsStateWithLifecycle()
    val speechTranscript by vm.speechTranscript.collectAsStateWithLifecycle()
    val catalogModels = vm.catalogModels

    var tab by rememberSaveable { mutableStateOf("Chat") }
    var input by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var memoryInput by rememberSaveable { mutableStateOf("") }
    var editingMemory by remember { mutableStateOf<Memory?>(null) }
    var renameChat by remember { mutableStateOf<Chat?>(null) }
    var rename by remember { mutableStateOf("") }
    var deletingChat by remember { mutableStateOf<Chat?>(null) }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importModel)
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importAvatar)
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening()
    }

    LaunchedEffect(speechTranscript) {
        if (speechTranscript.isNotBlank()) input = speechTranscript
    }

    val requestMicOrListen: () -> Unit = {
        if (speechListening) {
            vm.stopListening()
        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startListening()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val performance = AvatarPerformance(
        speaking = voiceStatus == "Falando",
        thinking = busy,
        happy = false,
        energy = if (busy) 0.72f else 0.52f,
        viseme = voiceViseme
    )

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text("Suas conversas", modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall)
                Button(
                    onClick = {
                        vm.newChat()
                        tab = "Chat"
                        scope.launch { drawer.close() }
                    },
                    enabled = !busy,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Novo chat")
                }
                OutlinedTextField(
                    search,
                    { search = it },
                    label = { Text("Pesquisar títulos") },
                    modifier = Modifier.padding(16.dp)
                )
                LazyColumn {
                    items(chats.filter { it.title.contains(search, true) }, key = { it.id }) { chat ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            TextButton(
                                onClick = {
                                    vm.open(chat)
                                    tab = "Chat"
                                    scope.launch { drawer.close() }
                                },
                                enabled = !busy
                            ) {
                                Text((if (chat.pinned) "★ " else "") + chat.title)
                            }
                            Row {
                                TextButton(onClick = { vm.updateChat(chat.copy(pinned = !chat.pinned)) }) {
                                    Text(if (chat.pinned) "Desafixar" else "Fixar")
                                }
                                TextButton(onClick = {
                                    renameChat = chat
                                    rename = chat.title
                                }, enabled = !busy) {
                                    Text("Renomear")
                                }
                                IconButton(onClick = { deletingChat = chat }, enabled = !busy) {
                                    Icon(Icons.Outlined.Delete, "Excluir conversa")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Nyra", fontWeight = FontWeight.SemiBold)
                            Text(
                                "LOCAL • ${if (busy) "PROCESSANDO" else "NO SEU APARELHO"}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Outlined.Menu, "Abrir histórico")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf(
                        "Chat" to Icons.Outlined.ChatBubbleOutline,
                        "Memória" to Icons.Outlined.BookmarkBorder,
                        "Modelos" to Icons.Outlined.Hub,
                        "Avatar" to Icons.Outlined.PersonOutline,
                        "Ajustes" to Icons.Outlined.Settings
                    ).forEach { (name, icon) ->
                        NavigationBarItem(
                            selected = tab == name,
                            onClick = { tab = name },
                            icon = { Icon(icon, null) },
                            label = { Text(name, fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .background(Brush.verticalGradient(listOf(Color(0xFF171022), Color(0xFF09070F))))
            ) {
                if (banner.isNotEmpty()) {
                    Text(
                        banner,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                when (tab) {
                    "Chat" -> ChatPage(
                        vm = vm,
                        modelsEmpty = models.isEmpty(),
                        avatar = avatar,
                        performance = performance,
                        messages = messages,
                        busy = busy,
                        stream = stream,
                        input = input,
                        responseMode = responseMode,
                        speechListening = speechListening,
                        speechStatus = speechStatus,
                        onInput = { input = it },
                        onMic = requestMicOrListen,
                        onCycleMode = {
                            vm.setResponseMode(
                                when (responseMode) {
                                    ResponseMode.FAST -> ResponseMode.AUTO
                                    ResponseMode.AUTO -> ResponseMode.THINK
                                    ResponseMode.THINK -> ResponseMode.FAST
                                }
                            )
                        },
                        onOpenModels = { tab = "Modelos" },
                        onOpenAvatar = { tab = "Avatar" }
                    )

                    "Modelos" -> ModelPage(
                        vm = vm,
                        models = models,
                        selected = selected,
                        busy = busy,
                        importing = importing,
                        download = download,
                        catalogModels = catalogModels,
                        onManualImport = { modelPicker.launch(arrayOf("*/*")) }
                    )

                    "Memória" -> LazyColumn(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            SectionTitle(
                                "O que vale guardar",
                                "Memórias manuais, editáveis e com conversa de origem. A recuperação local já participa do contexto das respostas."
                            )
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Usar memória nas respostas", modifier = Modifier.weight(1f))
                                Switch(memoryEnabled, vm::setMemory)
                            }
                        }
                        item {
                            OutlinedTextField(
                                memoryInput,
                                { memoryInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (editingMemory == null) "Nova memória" else "Editar memória") }
                            )
                            Button(
                                onClick = {
                                    vm.saveMemory(memoryInput, editingMemory)
                                    memoryInput = ""
                                    editingMemory = null
                                },
                                enabled = memoryInput.isNotBlank()
                            ) { Text("Salvar memória") }
                        }
                        items(memories, key = { it.id }) { memory ->
                            Card {
                                Column(Modifier.padding(16.dp)) {
                                    SelectionContainer { Text((if (memory.pinned) "★ " else "") + memory.text) }
                                    memory.sourceChatId?.let { id ->
                                        Text(
                                            "Origem: ${chats.find { it.id == id }?.title ?: "Conversa excluída"}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Row {
                                        TextButton(onClick = {
                                            editingMemory = memory
                                            memoryInput = memory.text
                                        }) { Text("Editar") }
                                        TextButton(onClick = { vm.pinMemory(memory) }) {
                                            Text(if (memory.pinned) "Desafixar" else "Fixar")
                                        }
                                        IconButton(onClick = { vm.deleteMemory(memory) }) {
                                            Icon(Icons.Outlined.Delete, "Excluir memória")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Avatar" -> AvatarPage(
                        avatar = avatar,
                        performance = performance,
                        importing = avatarImporting,
                        onImport = { avatarPicker.launch(arrayOf("model/gltf-binary", "application/octet-stream", "*/*")) },
                        onCancelImport = vm::cancelAvatarImport,
                        onDelete = vm::deleteAvatar
                    )

                    "Ajustes" -> LazyColumn(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        item {
                            SectionTitle(
                                "Privacidade",
                                "A internet é usada para baixar modelos que você escolher. Depois de instalados, inferência, histórico, memória, voz, ditado e avatar continuam no aparelho."
                            )
                        }
                        item {
                            SectionTitle("Modo de resposta", "Escolha entre menor latência, equilíbrio automático e análise mais cuidadosa.")
                            ResponseModeChips(responseMode, vm::setResponseMode)
                        }
                        item {
                            SectionTitle("Voz", voiceStatus)
                            Row {
                                TextButton(onClick = { vm.speak("Olá, este é um teste da voz local em português.") }) { Text("Testar voz") }
                                TextButton(onClick = vm::stop) { Text("Parar") }
                            }
                        }
                        item {
                            SectionTitle("Ditado", speechStatus)
                            Text("O Nyra usa apenas o reconhecedor on-device do Android. Se o pacote offline não existir, ele não troca silenciosamente para nuvem.")
                        }
                        item { SectionTitle("Dispositivo", remember(responseMode) { vm.deviceInfo() }) }
                        item { SectionTitle("Medições da última resposta", metrics) }
                        item { TextButton(onClick = vm::releaseModel, enabled = !busy) { Text("Liberar modelo da RAM") } }
                        item {
                            SectionTitle(
                                "Sobre",
                                "Nyra 0.2.0-dev • inferência local + modelos + memória + renderer VRM nativo + TTS/STT offline + lip sync por ranges\nPurpleCore já possui governor térmico/RAM; GPU/NPU, embeddings e atuação corporal avançada ainda exigem benchmark e calibração real."
                            )
                        }
                    }
                }
            }
        }
    }

    renameChat?.let { chat ->
        AlertDialog(
            onDismissRequest = { renameChat = null },
            title = { Text("Renomear conversa") },
            text = { OutlinedTextField(rename, { rename = it }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.updateChat(chat.copy(title = rename.trim().take(80)))
                        renameChat = null
                    },
                    enabled = rename.isNotBlank()
                ) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { renameChat = null }) { Text("Cancelar") } }
        )
    }

    deletingChat?.let { chat ->
        AlertDialog(
            onDismissRequest = { deletingChat = null },
            title = { Text("Excluir conversa?") },
            text = { Text("As mensagens serão excluídas. Memórias salvas separadamente permanecem editáveis na aba Memória.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteChat(chat)
                    deletingChat = null
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deletingChat = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ResponseModeChips(current: ResponseMode, select: (ResponseMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResponseMode.entries.forEach { mode ->
            FilterChip(
                selected = current == mode,
                onClick = { select(mode) },
                label = { Text(mode.label) },
                leadingIcon = if (current == mode) {
                    { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun ChatPage(
    vm: NyraViewModel,
    modelsEmpty: Boolean,
    avatar: AvatarDescriptor?,
    performance: AvatarPerformance,
    messages: List<ChatMessage>,
    busy: Boolean,
    stream: String,
    input: String,
    responseMode: ResponseMode,
    speechListening: Boolean,
    speechStatus: String,
    onInput: (String) -> Unit,
    onMic: () -> Unit,
    onCycleMode: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenAvatar: () -> Unit
) {
    if (avatar != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (messages.isEmpty()) 310.dp else 220.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF100B18))
        ) {
            NativeAvatarView(file = avatar.file, performance = performance, modifier = Modifier.fillMaxSize())
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1327))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PersonOutline, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Avatar 3D ainda não importado", fontWeight = FontWeight.SemiBold)
                    Text("Importe seu VRM uma vez e ele fica salvo no aparelho.", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onOpenAvatar) { Text("Avatar") }
            }
        }
    }

    if (messages.isEmpty() && !busy) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Espaço para\nsuas ideias.", fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text(
                if (modelsEmpty) {
                    "A Nyra ainda precisa de um cérebro local. Você pode baixar e instalar o modelo recomendado sem sair do app."
                } else {
                    "Escreva ou fale uma pergunta. O histórico e as memórias ficam neste aparelho."
                },
                color = Color(0xFFB7ACCA)
            )
            if (modelsEmpty) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onOpenModels) {
                    Icon(Icons.Outlined.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Baixar modelo")
                }
            }
        }
    } else {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val text = if (message.state == "generating" && busy) stream.ifEmpty { "Pensando localmente…" } else message.text
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.role == "user") Color(0xFF302044) else Color(0xFF191322)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (message.role == "user") "Você" else "Nyra",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer { Text(text) }
                        if (message.state == "interrupted" || message.state == "failed") {
                            Text(
                                if (message.state == "failed") "Resposta incompleta: falha" else "Interrompida",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (message.role == "model" && message.state == "complete" && text.isNotEmpty()) {
                            TextButton(onClick = { vm.speak(text) }) {
                                Icon(Icons.Outlined.VolumeUp, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Ouvir")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCycleMode, enabled = !busy) {
            Icon(
                when (responseMode) {
                    ResponseMode.FAST -> Icons.Outlined.Bolt
                    ResponseMode.AUTO -> Icons.Outlined.AutoAwesome
                    ResponseMode.THINK -> Icons.Outlined.Psychology
                },
                null
            )
            Spacer(Modifier.width(4.dp))
            Text(responseMode.label)
        }
        if (speechListening) {
            Text(speechStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            input,
            onInput,
            placeholder = { Text(if (speechListening) "Escutando…" else "Fale com a Nyra") },
            modifier = Modifier.weight(1f),
            maxLines = 4,
            shape = RoundedCornerShape(24.dp)
        )
        IconButton(onClick = onMic, enabled = !busy) {
            Icon(
                if (speechListening) Icons.Outlined.StopCircle else Icons.Outlined.Mic,
                if (speechListening) "Parar ditado" else "Ditado offline"
            )
        }
        IconButton(
            onClick = if (busy) vm::stop else if (input.isNotBlank()) {
                { if (vm.send(input)) onInput("") }
            } else {
                {}
            },
            enabled = busy || input.isNotBlank()
        ) {
            Icon(
                if (busy) Icons.Outlined.StopCircle else Icons.Outlined.ArrowUpward,
                if (busy) "Interromper geração" else "Enviar mensagem"
            )
        }
    }
}

@Composable
private fun ModelPage(
    vm: NyraViewModel,
    models: List<java.io.File>,
    selected: String?,
    busy: Boolean,
    importing: Boolean,
    download: ModelDownloadState?,
    catalogModels: List<CatalogModel>,
    onManualImport: () -> Unit
) {
    LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionTitle(
                "Baixar dentro do Nyra",
                "Escolha um modelo e toque em Baixar e instalar. O app retoma se a rede cair e verifica o SHA-256 antes de liberar o modelo."
            )
        }
        items(catalogModels, key = { it.id }) { model ->
            val installed = models.firstOrNull { it.name == model.fileName }
            val state = download?.takeIf { it.modelId == model.id }
            val fraction = if (state != null && state.total > 0L) {
                (state.done.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model.title, style = MaterialTheme.typography.titleMedium)
                            if (model.recommended) {
                                Text("RECOMENDADO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (installed != null) Icon(Icons.Outlined.CheckCircle, "Instalado", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(model.description, color = Color(0xFFB7ACCA))
                    Text("${model.sizeBytes / 1048576} MB • contexto ${model.contextTokens} • ${model.license}", style = MaterialTheme.typography.bodySmall)
                    if (state != null && (state.active || state.done in 1 until state.total)) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text("${state.done / 1048576} MB / ${state.total / 1048576} MB • ${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                    when {
                        installed != null -> {
                            Text(if (installed.name == selected) "Instalado e em uso" else "Instalado", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            Row {
                                TextButton(onClick = { vm.selectModel(installed) }, enabled = !busy) { Text(if (installed.name == selected) "Em uso" else "Usar") }
                                TextButton(onClick = { vm.deleteModel(installed) }, enabled = !busy && !importing && download?.active != true) { Text("Excluir") }
                            }
                        }
                        state?.active == true -> {
                            OutlinedButton(onClick = vm::cancelDownload) {
                                Icon(Icons.Outlined.Pause, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Pausar")
                            }
                        }
                        else -> {
                            Button(onClick = { vm.downloadModel(model) }, enabled = !busy && !importing && download?.active != true) {
                                Icon(Icons.Outlined.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if ((state?.done ?: 0L) > 0L) "Retomar" else "Baixar e instalar")
                            }
                        }
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SectionTitle("Importação manual", "Opcional, para outro arquivo .litertlm compatível que já esteja no celular.")
        }
        item {
            OutlinedButton(onClick = onManualImport, enabled = !busy && !importing && download?.active != true) {
                Icon(Icons.Outlined.FileOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("Importar .litertlm")
            }
        }
        if (importing) {
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = vm::cancelImport) { Text("Cancelar importação") }
            }
        }
        items(models.filter { file -> catalogModels.none { it.fileName == file.name } }, key = { it.path }) { file ->
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(file.name, style = MaterialTheme.typography.titleSmall)
                    Text("${file.length() / 1048576} MB • ${if (file.name == selected) "Selecionado" else "Instalado manualmente"}")
                    Row {
                        TextButton(onClick = { vm.selectModel(file) }, enabled = !busy) { Text("Usar") }
                        TextButton(onClick = { vm.deleteModel(file) }, enabled = !busy && !importing && download?.active != true) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarPage(
    avatar: AvatarDescriptor?,
    performance: AvatarPerformance,
    importing: Boolean,
    onImport: () -> Unit,
    onCancelImport: () -> Unit,
    onDelete: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionTitle(
                "Presença 3D",
                "Renderer nativo Filament. O arquivo VRM é lido diretamente do armazenamento privado do Nyra; nenhuma página web é usada para desenhar o avatar."
            )
        }
        if (avatar != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().height(440.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0913))
                ) {
                    NativeAvatarView(file = avatar.file, performance = performance, modifier = Modifier.fillMaxSize())
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(avatar.title, style = MaterialTheme.typography.titleLarge)
                        Text("por ${avatar.author} • versão ${avatar.version}")
                        Text("${avatar.humanoidBones} ossos humanoides • ${avatar.blendShapes} grupos de expressão")
                        Text("Arquivo privado: ${avatar.file.length() / 1048576} MB", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onImport, enabled = !importing) {
                        Icon(Icons.Outlined.SwapHoriz, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Trocar VRM")
                    }
                    OutlinedButton(onClick = onDelete, enabled = !importing) {
                        Icon(Icons.Outlined.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Remover")
                    }
                }
            }
            item {
                Text(
                    "A atuação inicial inclui respiração sutil, cabeça/pescoço, piscadas e A/I/U/E/O sincronizados aos ranges reais do TTS offline. O toque na área 3D permite orbitar e dar zoom.",
                    color = Color(0xFFB7ACCA)
                )
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.ViewInAr, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Importe o VRM da Nyra", style = MaterialTheme.typography.titleLarge)
                        Text("Selecione o arquivo 4024685333778527948.vrm.glb. O Nyra valida o GLB/VRM, copia de forma atômica e passa a carregá-lo automaticamente nas próximas aberturas.")
                        Button(onClick = onImport, enabled = !importing) {
                            Icon(Icons.Outlined.FileOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Selecionar VRM")
                        }
                    }
                }
            }
        }
        if (importing) {
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = onCancelImport) { Text("Cancelar importação") }
            }
        }
        item {
            Text(
                "Metadados do avatar fornecido indicam uso comercial não permitido e crédito necessário. O app preserva o arquivo local e não publica o avatar.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9E94AD)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, color = Color(0xFFB7ACCA))
    }
}

@Composable
private fun Welcome(done: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Nyra", "Um cérebro\nno seu aparelho", "Uma presença\nque fica com você")
    val descriptions = listOf(
        "Uma IA pessoal local. Conversas, memória, voz e avatar 3D em um só lugar.",
        "Na aba Modelos, o Nyra baixa e instala o cérebro recomendado sem você precisar lidar com arquivos de modelo manualmente.",
        "Histórico e memórias ficam no aparelho. Na aba Avatar, importe uma vez o VRM da Esme e o renderer 3D nativo passa a carregá-lo automaticamente."
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF321C4F), Color(0xFF09070F))))
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("LOCAL • PESSOAL • CONFIGURÁVEL", color = Color(0xFFC3A4FF), fontSize = 11.sp)
        Spacer(Modifier.height(32.dp))
        Text(titles[page], color = Color.White, fontSize = 42.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        Text(descriptions[page], color = Color(0xFFCCBFD9), fontSize = 17.sp)
        Spacer(Modifier.height(48.dp))
        Text("${page + 1} / 3", color = Color(0xFFC3A4FF))
        Button(
            onClick = { if (page < 2) page++ else done() },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(if (page < 2) "Continuar" else "Começar")
        }
        if (page > 0) TextButton(onClick = { page-- }) { Text("Voltar") }
    }
}
