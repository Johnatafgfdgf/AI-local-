package dev.nyra.local

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
    val onboarded by vm.onboarded.collectAsStateWithLifecycle()
    if (!onboarded) {
        Welcome(vm::finishOnboarding)
        return
    }

    val chats by vm.chats.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val importing by vm.importBusy.collectAsStateWithLifecycle()
    val download by vm.downloadState.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val stream by vm.stream.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val memoryEnabled by vm.memoryEnabled.collectAsStateWithLifecycle()
    val voiceStatus by vm.voiceStatus.collectAsStateWithLifecycle()
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importModel)
    }

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
                    "Chat" -> {
                        if (messages.isEmpty() && !busy) {
                            Column(
                                Modifier.weight(1f).fillMaxWidth().padding(28.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "Espaço para\nsuas ideias.",
                                    fontSize = 34.sp,
                                    lineHeight = 40.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    if (models.isEmpty()) {
                                        "A Nyra ainda precisa de um cérebro local. Agora você pode baixar e instalar um modelo sem sair do app."
                                    } else {
                                        "Escreva uma pergunta para começar. Você pode guardar informações importantes na aba Memória."
                                    },
                                    color = Color(0xFFB7ACCA)
                                )
                                if (models.isEmpty()) {
                                    Spacer(Modifier.height(20.dp))
                                    Button(onClick = { tab = "Modelos" }) {
                                        Icon(Icons.Outlined.Download, null)
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
                                    val text = if (message.state == "generating" && busy) {
                                        stream.ifEmpty { "Preparando resposta…" }
                                    } else message.text
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
                                                    Text("Ouvir")
                                                }
                                            }
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(12.dp)) }
                            }
                        }

                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                input,
                                { input = it },
                                placeholder = { Text("Fale com a Nyra") },
                                modifier = Modifier.weight(1f),
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp)
                            )
                            IconButton(
                                onClick = if (busy) vm::stop else if (input.isNotBlank()) {
                                    {
                                        if (vm.send(input)) input = ""
                                    }
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

                    "Modelos" -> LazyColumn(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            SectionTitle(
                                "Baixar dentro do Nyra",
                                "Escolha um modelo e toque em Baixar e instalar. O app faz o download, retoma se cair e verifica o SHA-256 antes de liberar o modelo."
                            )
                        }

                        items(catalogModels, key = { it.id }) { model ->
                            val installed = models.firstOrNull { it.name == model.fileName }
                            val state = download?.takeIf { it.modelId == model.id }
                            val fraction = if (state != null && state.total > 0L) {
                                (state.done.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(model.title, style = MaterialTheme.typography.titleMedium)
                                            if (model.recommended) {
                                                Text(
                                                    "RECOMENDADO",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                        if (installed != null) {
                                            Icon(Icons.Outlined.CheckCircle, "Instalado", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    Text(model.description, color = Color(0xFFB7ACCA))
                                    Text(
                                        "${model.sizeBytes / 1048576} MB • contexto ${model.contextTokens} • ${model.license}",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    if (state != null && (state.active || state.done in 1 until state.total)) {
                                        LinearProgressIndicator(
                                            progress = { fraction },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            "${state.done / 1048576} MB / ${state.total / 1048576} MB • ${(fraction * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }

                                    when {
                                        installed != null -> {
                                            Text(
                                                if (installed.name == selected) "Instalado e em uso" else "Instalado",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Row {
                                                TextButton(onClick = { vm.selectModel(installed) }, enabled = !busy) {
                                                    Text(if (installed.name == selected) "Em uso" else "Usar")
                                                }
                                                TextButton(
                                                    onClick = { vm.deleteModel(installed) },
                                                    enabled = !busy && !importing && download?.active != true
                                                ) {
                                                    Text("Excluir")
                                                }
                                            }
                                        }

                                        state?.active == true -> {
                                            OutlinedButton(onClick = vm::cancelDownload) {
                                                Icon(Icons.Outlined.Pause, null)
                                                Text("Pausar")
                                            }
                                        }

                                        else -> {
                                            Button(
                                                onClick = { vm.downloadModel(model) },
                                                enabled = !busy && !importing && download?.active != true
                                            ) {
                                                Icon(Icons.Outlined.Download, null)
                                                Text(if ((state?.done ?: 0L) > 0L) "Retomar" else "Baixar e instalar")
                                            }
                                        }
                                    }

                                    Text(
                                        "O arquivo só vira um modelo instalado depois de passar pela verificação de tamanho e SHA-256.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        item {
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            SectionTitle(
                                "Importação manual",
                                "Opcional. Use isto apenas se você já tiver outro arquivo .litertlm compatível no celular."
                            )
                        }
                        item {
                            OutlinedButton(
                                onClick = { picker.launch(arrayOf("*/*")) },
                                enabled = !busy && !importing && download?.active != true
                            ) {
                                Icon(Icons.Outlined.FileOpen, null)
                                Text("Importar .litertlm")
                            }
                        }
                        if (importing) {
                            item {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                TextButton(onClick = vm::cancelImport) { Text("Cancelar importação") }
                            }
                        }
                        items(
                            models.filter { file -> catalogModels.none { it.fileName == file.name } },
                            key = { it.path }
                        ) { file ->
                            Card {
                                Column(Modifier.padding(16.dp)) {
                                    Text(file.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${file.length() / 1048576} MB • ${if (file.name == selected) "Selecionado" else "Instalado manualmente"}")
                                    Row {
                                        TextButton(onClick = { vm.selectModel(file) }, enabled = !busy) { Text("Usar") }
                                        TextButton(
                                            onClick = { vm.deleteModel(file) },
                                            enabled = !busy && !importing && download?.active != true
                                        ) { Text("Excluir") }
                                    }
                                }
                            }
                        }
                    }

                    "Memória" -> LazyColumn(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            SectionTitle(
                                "O que vale guardar",
                                "Memórias manuais, editáveis e com conversa de origem. Busca por palavras nesta versão; embeddings e consolidação automática ainda pendentes."
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

                    "Avatar" -> Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SectionTitle("Esme", "Avatar padrão • soun.dhaptics")
                        Text("O perfil do VRM foi extraído. A renderização e o motor de atuação ainda não estão integrados nesta build inicial.")
                        Text("Dedos articulados e morphs A/I/U/E/O identificados. Calibração visual, física, gestos e sincronização labial pendentes.")
                        Text("Uso comercial não permitido pelos metadados do avatar.", style = MaterialTheme.typography.bodySmall)
                    }

                    "Ajustes" -> LazyColumn(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        item {
                            SectionTitle(
                                "Privacidade",
                                "A internet é usada somente quando você manda baixar um modelo do catálogo. Depois de instalado, a inferência e suas conversas continuam locais; nenhuma API de IA é usada para responder."
                            )
                        }
                        item {
                            SectionTitle("Voz", voiceStatus)
                            Row {
                                TextButton(onClick = { vm.speak("Olá, este é um teste da voz local em português.") }) { Text("Testar voz") }
                                TextButton(onClick = vm::stop) { Text("Parar") }
                            }
                        }
                        item { SectionTitle("Dispositivo", remember { vm.deviceInfo() }) }
                        item { SectionTitle("Medições da última resposta", metrics) }
                        item { TextButton(onClick = vm::releaseModel, enabled = !busy) { Text("Liberar modelo da RAM") } }
                        item {
                            SectionTitle(
                                "Sobre",
                                "Nyra 0.1.1-dev • Download de modelos integrado\nPurpleCore, STT, lip sync, renderer VRM e autotune ainda não concluídos."
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
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, color = Color(0xFFB7ACCA))
    }
}

@Composable
private fun Welcome(done: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Nyra", "Um cérebro\nno seu aparelho", "Suas ideias\ncontinuam aqui")
    val descriptions = listOf(
        "Uma IA pessoal local. Conversas, memória e voz em um só lugar.",
        "Você não precisa procurar arquivos manualmente. Depois de entrar, abra Modelos e o Nyra baixa e instala o cérebro recomendado para você.",
        "Histórico e memórias ficam neste aparelho. Esta é uma versão inicial de desenvolvimento; o avatar animado ainda está sendo integrado."
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
        Text(
            titles[page],
            color = Color.White,
            fontSize = 42.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.SemiBold
        )
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
