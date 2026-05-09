package com.example.gemmachat

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GemmaChatTheme { GemmaChatApp() } }
    }
}

// ════════════════════════════ THEME ════════════════════════════

@Composable
fun GemmaChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary          = Color(0xFF4FC3F7),
            onPrimary        = Color(0xFF003549),
            secondary        = Color(0xFF81D4FA),
            background       = Color(0xFF0D1117),
            surface          = Color(0xFF161B22),
            surfaceVariant   = Color(0xFF21262D),
            onBackground     = Color(0xFFE6EDF3),
            onSurface        = Color(0xFFE6EDF3),
            onSurfaceVariant = Color(0xFF8B949E),
            outline          = Color(0xFF30363D),
            error            = Color(0xFFF85149),
        ),
        content = content
    )
}

// ════════════════════════════ ROOT NAV ════════════════════════════

enum class Tab { CHAT, MODELS }

@Composable
fun GemmaChatApp(vm: ChatViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(Tab.CHAT) }
    val state by vm.engineState

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(vm, state, selectedTab) },
        bottomBar = { AppBottomNav(selectedTab) { selectedTab = it } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                Tab.CHAT   -> ChatTab(vm, state)
                Tab.MODELS -> ModelsTab(vm) { selectedTab = Tab.CHAT }
            }
        }
    }
}

// ════════════════════════════ TOP BAR ════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(vm: ChatViewModel, state: EngineState, tab: Tab) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Column {
                Text(
                    if (tab == Tab.CHAT) "Gemma Chat" else "LiteRT Models",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
                val sub = when (state) {
                    is EngineState.Ready   -> state.speedupLabel
                    is EngineState.Loading -> "Loading model..."
                    else -> "No model loaded"
                }
                val subColor = when (state) {
                    is EngineState.Ready -> if (state.mtpActive) Color(0xFF4FC3F7)
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(sub, style = MaterialTheme.typography.labelSmall, color = subColor)
            }
        },
        actions = {
            if (state is EngineState.Ready && tab == Tab.CHAT) {
                if (state.mtpActive) {
                    Surface(
                        color  = Color(0xFF4FC3F7).copy(alpha = 0.12f),
                        shape  = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF4FC3F7).copy(alpha = 0.35f))
                    ) {
                        Text("MTP ⚡3x",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = { vm.newChat() }) {
                    Icon(Icons.Outlined.AddComment, "New Chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

// ════════════════════════════ BOTTOM NAV ════════════════════════════

@Composable
fun AppBottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        val navColors = NavigationBarItemDefaults.colors(
            selectedIconColor   = Color(0xFF4FC3F7),
            selectedTextColor   = Color(0xFF4FC3F7),
            indicatorColor      = Color(0xFF4FC3F7).copy(alpha = 0.12f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NavigationBarItem(
            selected = current == Tab.CHAT,
            onClick  = { onSelect(Tab.CHAT) },
            icon     = { Icon(if (current == Tab.CHAT) Icons.Filled.Chat else Icons.Outlined.Chat, null) },
            label    = { Text("Chat") }, colors = navColors
        )
        NavigationBarItem(
            selected = current == Tab.MODELS,
            onClick  = { onSelect(Tab.MODELS) },
            icon     = { Icon(if (current == Tab.MODELS) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome, null) },
            label    = { Text("Models") }, colors = navColors
        )
    }
}

// ════════════════════════════ CHAT TAB ════════════════════════════

@Composable
fun ChatTab(vm: ChatViewModel, state: EngineState) {
    when (state) {
        is EngineState.Idle    -> LoadModelPrompt(vm)
        is EngineState.Loading -> LoadingScreen()
        is EngineState.Error   -> ErrorScreen(state.message) { vm.engineState.value = EngineState.Idle }
        is EngineState.Ready   -> {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { ChatMessages(vm) }
                ChatInput(vm)
            }
        }
    }
}

// ── Load Prompt ───────────────────────────────────────────────────

@Composable
fun LoadModelPrompt(vm: ChatViewModel) {
    val modelPath   by vm.modelPath
    val drafterPath by vm.drafterPath
    val useDrafter  by vm.useDrafter

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🤖", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("Load Gemma 4 E4B", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("LiteRT on-device inference", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = modelPath, onValueChange = { vm.modelPath.value = it },
            label = { Text("Model path (.task)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = outlinedFieldColors()
        )
        Spacer(Modifier.height(12.dp))

        // MTP Drafter card
        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("MTP Drafter", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Speculative decoding  •  up to 3x faster",
                            style = MaterialTheme.typography.labelSmall, color = Color(0xFF4FC3F7))
                    }
                    Switch(checked = useDrafter,
                        onCheckedChange = { vm.useDrafter.value = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4FC3F7),
                            checkedTrackColor = Color(0xFF4FC3F7).copy(alpha = 0.3f)
                        ))
                }

                AnimatedVisibility(visible = useDrafter) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        // How MTP Drafter works
                        Surface(color = Color(0xFF4FC3F7).copy(alpha = 0.07f),
                            shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("How MTP Drafter works:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF4FC3F7), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                listOf(
                                    "1. Lightweight 4-layer drafter predicts 5 tokens fast",
                                    "2. Gemma 4 verifies all 5 in ONE forward pass",
                                    "3. Accepted tokens output simultaneously",
                                    "4. Rejected token still produces correct output",
                                    "5. Result: same quality, up to 3x tokens/sec"
                                ).forEach {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 1.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = drafterPath, onValueChange = { vm.drafterPath.value = it },
                            label = { Text("Drafter model path") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            colors = outlinedFieldColors()
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Model download tip
        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Download Models", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Main:    kaggle.com/models/google/gemma",
                    "         → gemma-4-e4b-it-gpu-int4.task",
                    "         → rename to gemma4.task",
                    "Drafter: huggingface.co/google/gemma-4-E4B-it-assistant",
                    "         → rename to gemma4-drafter.task",
                    "Place both in: /sdcard/Download/"
                ).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                        fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(onClick = { vm.loadModel() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7),
                contentColor = Color(0xFF003549))) {
            Icon(Icons.Filled.RocketLaunch, null)
            Spacer(Modifier.width(8.dp))
            Text("Load Model${if (useDrafter) " + MTP Drafter" else ""}",
                style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ── Chat Messages ─────────────────────────────────────────────────

@Composable
fun ChatMessages(vm: ChatViewModel) {
    val messages  = vm.messages
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (messages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✨", fontSize = 42.sp)
                Spacer(Modifier.height(8.dp))
                Text("Gemma 4 E4B ready", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("MTP Drafter active — up to 3x faster",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4FC3F7), modifier = Modifier.padding(top = 4.dp))
            }
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(messages.size) { i -> MessageBubble(messages[i]) }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isUser) {
            Box(
                modifier = Modifier.size(32.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))),
                        CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("G", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart    = if (msg.isUser) 16.dp else 4.dp,
                    topEnd      = if (msg.isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp, bottomEnd = 16.dp
                ),
                color  = if (msg.isUser) Color(0xFF4FC3F7).copy(alpha = 0.12f)
                         else MaterialTheme.colorScheme.surfaceVariant,
                border = if (msg.isUser) BorderStroke(1.dp, Color(0xFF4FC3F7).copy(alpha = 0.25f)) else null
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (msg.text.isNotEmpty()) {
                        Text(msg.text, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
                    }
                    if (msg.isStreaming) {
                        val inf = rememberInfiniteTransition(label = "blink")
                        val a by inf.animateFloat(1f, 0f,
                            infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")
                        Text("▌", color = Color(0xFF4FC3F7).copy(alpha = a), fontSize = 14.sp)
                    }
                }
            }
        }

        if (msg.isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)) }
        }
    }
}

// ── Chat Input ────────────────────────────────────────────────────

@Composable
fun ChatInput(vm: ChatViewModel) {
    val isGenerating by vm.isGenerating
    var input by remember { mutableStateOf("") }
    fun send() { if (input.isNotBlank() && !isGenerating) { vm.sendMessage(input.trim()); input = "" } }

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text(if (isGenerating) "Generating…" else "Message Gemma…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.weight(1f), maxLines = 5, enabled = !isGenerating,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = outlinedFieldColors(), shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { send() }, enabled = input.isNotBlank() && !isGenerating,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF4FC3F7), disabledContainerColor = MaterialTheme.colorScheme.outline)
            ) {
                if (isGenerating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF003549))
                else Icon(Icons.Filled.Send, null, tint = Color(0xFF003549))
            }
        }
    }
}

// ════════════════════════════ MODELS TAB ════════════════════════════

@Composable
fun ModelsTab(vm: ChatViewModel, onNavigateToChat: () -> Unit) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf<ModelCategory?>(null) }

    val categories = listOf(
        null              to "All",
        ModelCategory.GEMMA4   to "Gemma 4",
        ModelCategory.DRAFTER  to "MTP Drafters",
        ModelCategory.GEMMA3N  to "Gemma 3n",
        ModelCategory.GEMMA3   to "Gemma 3",
        ModelCategory.COMMUNITY to "Community"
    )

    val filtered = if (selectedCategory == null) ModelCatalogue.all
                   else ModelCatalogue.byCategory(selectedCategory!!)

    Column(Modifier.fillMaxSize()) {
        // Category filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories.size) { i ->
                val (cat, label) = categories[i]
                val selected = selectedCategory == cat
                FilterChip(
                    selected = selected,
                    onClick  = { selectedCategory = cat },
                    label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4FC3F7).copy(alpha = 0.2f),
                        selectedLabelColor     = Color(0xFF4FC3F7),
                        containerColor         = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor             = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        
                        selectedBorderColor = Color(0xFF4FC3F7).copy(alpha = 0.4f),
                        borderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered.size) { i ->
                ModelCard(
                    model = filtered[i],
                    onOpenUrl = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    onUseModel = { model ->
                        val drafter = if (model.isMTPDrafter) null
                                      else ModelCatalogue.all.find {
                                          it.isMTPDrafter && it.targetModelId == model.id
                                      }
                        vm.loadFromCatalogue(model, drafter)
                        onNavigateToChat()
                    }
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    model: LiteRTModel,
    onOpenUrl: (String) -> Unit,
    onUseModel: (LiteRTModel) -> Unit
) {
    val isDrafter = model.isMTPDrafter

    Surface(
        color  = MaterialTheme.colorScheme.surfaceVariant,
        shape  = RoundedCornerShape(14.dp),
        border = if (isDrafter) BorderStroke(1.dp, Color(0xFF4FC3F7).copy(alpha = 0.4f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        if (isDrafter) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = Color(0xFF4FC3F7).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)) {
                                Text("MTP ⚡", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(model.variant, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp))
                }

                // Size badge
                Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        if (model.sizeMB >= 1000) "${"%.1f".format(model.sizeMB / 1000f)} GB"
                        else "${model.sizeMB} MB",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // MTP speedup label
            if (isDrafter && model.speedupFactor != null) {
                Spacer(Modifier.height(6.dp))
                Surface(color = Color(0xFF4FC3F7).copy(alpha = 0.07f), shape = RoundedCornerShape(6.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${model.speedupFactor} inference speedup • zero quality loss",
                            style = MaterialTheme.typography.labelSmall, color = Color(0xFF4FC3F7))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(model.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)

            Spacer(Modifier.height(10.dp))

            // Feature tags
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items(model.features.size) { i ->
                    Surface(
                        color  = MaterialTheme.colorScheme.background,
                        shape  = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(model.features[i],
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Kaggle
                OutlinedButton(
                    onClick = { onOpenUrl(model.kaggleUrl) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kaggle", style = MaterialTheme.typography.labelSmall)
                }
                // HuggingFace
                OutlinedButton(
                    onClick = { onOpenUrl(model.hfUrl) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("HF", style = MaterialTheme.typography.labelSmall)
                }
                // Use this model
                if (!isDrafter) {
                    Button(
                        onClick = { onUseModel(model) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7),
                            contentColor = Color(0xFF003549)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.RocketLaunch, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Use", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Drafter pairing info
            if (isDrafter && model.targetModelId != null) {
                Spacer(Modifier.height(8.dp))
                val target = ModelCatalogue.findById(model.targetModelId)
                Text("Pairs with: ${target?.name ?: model.targetModelId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ════════════════════════════ UTILITY SCREENS ════════════════════════════

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF4FC3F7))
            Spacer(Modifier.height(16.dp))
            Text("Loading Gemma 4 E4B…", color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge)
            Text("This may take 10–30 seconds",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠️", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("Load failed", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                Text(message, modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) { Text("Try Again") }
        }
    }
}

// ════════════════════════════ HELPERS ════════════════════════════

@Composable
fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Color(0xFF4FC3F7),
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTextColor     = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
    cursorColor          = Color(0xFF4FC3F7),
    focusedLabelColor    = Color(0xFF4FC3F7)
)
