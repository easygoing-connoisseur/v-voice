package com.example.voicetester.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.voicetester.Backend
import com.example.voicetester.GAP_MAX
import com.example.voicetester.GAP_MIN
import com.example.voicetester.Identity
import com.example.voicetester.LogEntry
import com.example.voicetester.PITCH_MAX
import com.example.voicetester.PITCH_MIN
import com.example.voicetester.QUICK_MAX
import com.example.voicetester.SPEED_MAX
import com.example.voicetester.SPEED_MIN
import com.example.voicetester.Status
import com.example.voicetester.VVoiceState
import com.example.voicetester.VoiceTesterViewModel
import com.example.voicetester.ui.theme.VvBg
import com.example.voicetester.ui.theme.VvDim
import com.example.voicetester.ui.theme.VvGreen
import com.example.voicetester.ui.theme.VvGreenDim
import com.example.voicetester.ui.theme.VvInk
import com.example.voicetester.ui.theme.VvLine
import com.example.voicetester.ui.theme.VvLineSoft
import com.example.voicetester.ui.theme.VvOff
import com.example.voicetester.ui.theme.VvPanel
import com.example.voicetester.ui.theme.VvPanel2
import com.example.voicetester.ui.theme.VvRed
import com.example.voicetester.ui.theme.VvType
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Tab(val label: String) { MAIN("MAIN"), LOG("LOG"), SYSTEM("SYSTEM") }

@Composable
fun VVoiceScreen(viewModel: VoiceTesterViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    var tab by remember { mutableStateOf(Tab.MAIN) }
    var bootDone by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(VvBg)
            // edge-to-edge なのでステータスバー / ナビゲーションバーの領域を自前で避ける
            .windowInsetsPadding(WindowInsets.systemBars)
            // ソフトキーボードの分だけ画面を詰める。詰めた結果スクロール領域が縮み、
            // フォーカス中の入力欄がキーボードの上へ自動で送り出される。
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Header()
            NavBar(tab) { tab = it }
            when (tab) {
                Tab.MAIN -> MainView(state, viewModel)
                Tab.LOG -> LogView(state, viewModel::replay)
                Tab.SYSTEM -> SystemView(state, viewModel)
            }
        }

        if (!bootDone) {
            BootScreen(state) { bootDone = true }
        }
    }
}

/* ---------------------------------------------------------------- boot */

@Composable
private fun BootScreen(state: VVoiceState, onDone: () -> Unit) {
    // 展開が終わるまでは出しっぱなしにする。初回は 158MB のコピーで時間がかかるため。
    val extracting = state.status == Status.BOOTING
    var elapsed by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            withFrameNanos { }
            elapsed = (System.currentTimeMillis() - start).toInt()
            if (elapsed > MIN_BOOT_MS) break
        }
    }
    LaunchedEffect(extracting, elapsed) {
        if (!extracting && elapsed > MIN_BOOT_MS) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VvBg)
            .clickable(enabled = !extracting) { onDone() }
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("V-VOICE", style = VvType.brand.copy(fontSize = 22.sp), color = VvInk)
        Spacer(Modifier.height(8.dp))
        Text("SECURE COMMUNICATION SYSTEM", style = VvType.mark, color = VvDim)
        Spacer(Modifier.height(30.dp))

        BootLine("VOICE ENGINE", if (elapsed > 160) bootValue(state, 0) else null)
        BootLine("AUDIO OUTPUT", if (elapsed > 350) "READY" else null)
        BootLine("SYSTEM", if (elapsed > 540) bootValue(state, 2) else null)

        if (state.bootMessage.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text(state.bootMessage, style = VvType.mark, color = VvGreen)
        }
    }
}

private fun bootValue(state: VVoiceState, index: Int): String = when {
    state.status == Status.FAILED -> "FAILED"
    state.status == Status.BOOTING -> if (index == 0) "LOADING" else "WAIT"
    else -> if (index == 0) "READY" else "ONLINE"
}

@Composable
private fun BootLine(key: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = VvType.mark.copy(fontSize = 11.sp), color = VvDim)
        Text(value ?: "", style = VvType.mark.copy(fontSize = 11.sp), color = VvGreen)
    }
}

/* ------------------------------------------------------------- chrome */

@Composable
private fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBottomLine(VvLine)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("V-VOICE", style = VvType.brand, color = VvInk)
            Spacer(Modifier.height(3.dp))
            Text("SECURE COMMUNICATION", style = VvType.mark, color = VvDim)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(VvGreen))
            Spacer(Modifier.width(6.dp))
            Text("ONLINE", style = VvType.mark.copy(fontSize = 10.sp), color = VvGreen)
        }
    }
}

@Composable
private fun NavBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().drawBottomLine(VvLine)) {
        Tab.entries.forEach { t ->
            val selected = t == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) VvPanel2 else Color.Transparent)
                    .clickable { onSelect(t) }
                    .then(if (selected) Modifier.drawBottomLine(VvGreen, 2f) else Modifier)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.label, style = VvType.nav, color = if (selected) VvInk else VvDim)
            }
        }
    }
}

/* --------------------------------------------------------------- main */

@Composable
private fun MainView(state: VVoiceState, vm: VoiceTesterViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        StatusBlock(state, vm)

        SectionHeader("INPUT MESSAGE", "%03d".format(state.text.length))
        Box(Modifier.padding(horizontal = 14.dp)) {
            TerminalTextField(
                value = state.text,
                onValueChange = vm::onTextChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = VvType.input,
                placeholder = "INPUT TEXT",
                // 枠の高さは入力欄そのものに持たせる。枠にだけ持たせると、
                // 文字の無い下半分が「押しても反応しない領域」になってしまう。
                minHeight = INPUT_MIN_HEIGHT,
            )
        }
        Spacer(Modifier.height(12.dp))

        // 操作できない言語・翻訳表示。場所を取らせないよう 1 行の帯にする。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawTopLine(VvLineSoft)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("LANGUAGE  JAPANESE", style = VvType.mark.copy(fontSize = 10.sp), color = VvOff)
            Text("TRANSLATION  [ LOCKED ]", style = VvType.mark.copy(fontSize = 10.sp), color = VvOff)
        }

        SpeakButton(state, vm)

        SectionHeader("QUICK COMMAND", null)
        QuickGrid(state, vm)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StatusBlock(state: VVoiceState, vm: VoiceTesterViewModel) {
    val live = state.isSpeaking
    val statusText = when (state.status) {
        Status.BOOTING -> "INITIALIZING"
        Status.SYNTHESIZING -> "SYNTHESIZING"
        Status.ACTIVE -> "VOICE ACTIVE"
        Status.FAILED -> "ENGINE FAILED"
        Status.READY -> "READY"
    }
    val color = when {
        state.status == Status.FAILED -> VvRed
        live -> VvGreen
        else -> VvDim
    }

    Column(Modifier.fillMaxWidth().drawBottomLine(VvLine)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (live) VvGreen else VvOff))
            Spacer(Modifier.width(8.dp))
            Text(statusText, style = VvType.status, color = color)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${state.profileLabel}  ${"%.2f".format(Locale.US, state.speed)}",
                style = VvType.mark.copy(fontSize = 10.sp),
                color = VvOff,
                maxLines = 1,
            )
        }

        if (state.speakingText.isNotEmpty()) {
            Text(
                text = state.speakingText,
                style = VvType.body,
                color = VvInk,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
            )
        }

        Waveform(
            player = vm.player,
            active = state.status == Status.ACTIVE,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SpeakButton(state: VVoiceState, vm: VoiceTesterViewModel) {
    val speaking = state.isSpeaking
    val enabled = state.canSpeak || speaking
    val border = when {
        !enabled -> VvLine
        speaking -> VvRed
        else -> VvGreen
    }
    val fg = when {
        !enabled -> VvOff
        speaking -> VvRed
        else -> Color(0xFFCDEBD9)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 18.dp)
            .background(if (speaking || !enabled) Color.Transparent else VvGreenDim)
            .border(BorderStroke(1.dp, border))
            .clickable(enabled = enabled) { if (speaking) vm.stop() else vm.speak() }
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (speaking) "STOP" else "SPEAK", style = VvType.speak, color = fg)
    }
}

@Composable
private fun QuickGrid(state: VVoiceState, vm: VoiceTesterViewModel) {
    // 空欄のスロットは押しても鳴らないので、ボタンとしては並べない。
    val commands = state.quickCommands.filter { it.isNotBlank() }
    if (commands.isEmpty()) {
        Text(
            text = "NO COMMANDS  /  ADD IN SYSTEM",
            style = VvType.mark.copy(fontSize = 11.sp),
            color = VvOff,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().drawTopLine(VvLine).padding(vertical = 26.dp),
        )
        return
    }

    // セリフの長さに幅があるので 2 列。
    Column(Modifier.fillMaxWidth().drawTopLine(VvLine)) {
        commands.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(62.dp)) {
                row.forEachIndexed { index, template ->
                    val label = state.identity.fill(template)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (index == 1) Modifier.drawStartLine(VvLine) else Modifier)
                            .background(VvPanel)
                            // 押したら即発話する（確認の一手間を挟まない）
                            .clickable { vm.speak(label, setInput = true) }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = VvType.body.copy(fontSize = 14.sp),
                            color = VvInk,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // 奇数個のときに最後の 1 個だけ横いっぱいに伸びないよう、空きの半マスを置く
                if (row.size == 1) {
                    Box(Modifier.weight(1f).fillMaxHeight().drawStartLine(VvLine))
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(VvLine))
        }
    }
}

/* ---------------------------------------------------------------- log */

@Composable
private fun LogView(state: VVoiceState, onReplay: (LogEntry) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader("TRANSMISSION LOG", "%02d".format(state.logs.size))
        if (state.logs.isEmpty()) {
            Text(
                text = "NO RECORDS",
                style = VvType.mark.copy(fontSize = 11.sp),
                color = VvOff,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
            )
        } else {
            state.logs.forEach { e ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawTopLine(VvLineSoft)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Text(e.time, style = VvType.mark.copy(fontSize = 11.sp), color = VvDim)
                    Spacer(Modifier.height(4.dp))
                    Text(e.text, style = VvType.body, color = VvInk)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${e.profile}  /  ${"%.2f".format(Locale.US, e.speed)}",
                            style = VvType.mark.copy(fontSize = 10.sp),
                            color = VvDim,
                        )
                        Box(
                            modifier = Modifier
                                .border(BorderStroke(1.dp, VvLine))
                                .clickable { onReplay(e) }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text("PLAY", style = VvType.mark.copy(fontSize = 10.sp), color = VvDim)
                        }
                    }
                }
            }
        }
        Text(
            text = "SESSION LOG IS STORED IN MEMORY AND CLEARED ON EXIT.",
            style = VvType.mark.copy(fontSize = 10.sp),
            color = VvOff,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/* ------------------------------------------------------------- system */

@Composable
private fun SystemView(state: VVoiceState, vm: VoiceTesterViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        SectionHeader("IDENTITY", null)
        IdentityRow("UNIT NAME", state.identity.self) {
            vm.onIdentityChange(state.identity.copy(self = it))
        }
        IdentityRow("CONTACT 01", state.identity.other) {
            vm.onIdentityChange(state.identity.copy(other = it))
        }
        IdentityRow("CONTACT 02", state.identity.other2) {
            vm.onIdentityChange(state.identity.copy(other2 = it))
        }

        SectionHeader("QUICK COMMAND", "%02d".format(state.quickCommands.size))
        state.quickCommands.forEachIndexed { index, template ->
            QuickCommandRow(
                index = index,
                value = template,
                onChange = { vm.onQuickCommandChange(index, it) },
                onRemove = { vm.removeQuickCommand(index) },
            )
        }
        if (state.quickCommands.size < QUICK_MAX) {
            ActionRow("+ ADD COMMAND", onClick = vm::addQuickCommand)
        }
        ResetRow(onConfirm = vm::resetQuickCommands)
        Text(
            text = "{self} {other} {other2} ARE REPLACED BY IDENTITY.",
            style = VvType.mark.copy(fontSize = 10.sp),
            color = VvOff,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )

        SectionHeader("VOICE CONFIG", null)
        state.styles.forEach { (id, label) ->
            val selected = id == state.styleId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawTopLine(VvLineSoft)
                    .clickable { vm.onStyleSelected(id) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, style = VvType.body.copy(fontSize = 14.sp), color = if (selected) VvGreen else VvInk)
                Text(if (selected) "ACTIVE" else "", style = VvType.mark.copy(fontSize = 10.sp), color = VvGreen)
            }
        }

        SliderRow(
            label = "SPEED",
            value = "%.2f".format(Locale.US, state.speed),
            position = state.speed,
            range = SPEED_MIN..SPEED_MAX,
            onChange = vm::onSpeedChange,
        )
        SliderRow(
            label = "PITCH",
            value = if (state.pitch > 0) "+${state.pitch}" else "${state.pitch}",
            position = state.pitch.toFloat(),
            range = PITCH_MIN.toFloat()..PITCH_MAX.toFloat(),
            onChange = vm::onPitchChange,
        )
        SliderRow(
            label = "PAUSE",
            value = "${state.gapMs} ms",
            position = state.gapMs.toFloat(),
            range = GAP_MIN.toFloat()..GAP_MAX.toFloat(),
            onChange = vm::onGapChange,
        )
        InfoRow("INTONATION", state.intonation.label, onClick = vm::cycleIntonation)

        SectionHeader("CHANNEL", null)
        InfoRow("LANGUAGE", "JAPANESE", disabled = true)
        InfoRow("TRANSLATION", "[ LOCKED ]", disabled = true)

        SectionHeader("SYSTEM", null)
        InfoRow("STABILITY", "HIGH")
        InfoRow("NATURALNESS", "HIGH")
        InfoRow("OUTPUT", "SPEAKER")
        InfoRow("CHANNEL", "LOCAL")

        SectionHeader("DIAGNOSTICS", null)
        InfoRow("BACKEND", if (state.backend == Backend.VOICEVOX) "VOICEVOX CORE" else "OS TTS")
        InfoRow("PROFILES FOUND", "%02d".format(state.styles.size))
        InfoRow("INIT TIME", state.initMs?.let { "$it ms" } ?: "--")
        InfoRow("LAST SYNTH", state.lastSynthMs?.let { "$it ms" } ?: "--")
        state.engineError?.let { InfoRow("ERROR", it) }

        SectionHeader("CREDIT", null)
        InfoRow("SYNTHESIS", if (state.backend == Backend.VOICEVOX) "VOICEVOX" else "OS BUILT-IN TTS")
        state.characterName?.let { name ->
            InfoRow("CHARACTER", name)
            InfoRow("CREDIT", "VOICEVOX:$name")
            InfoRow("TERMS", "voicevox.hiroshiba.jp")
        }

        Text(
            text = "V-VOICE 1.0  /  SECURE COMMUNICATION SYSTEM",
            style = VvType.mark.copy(fontSize = 10.sp),
            color = VvOff,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/* --------------------------------------------------------------- bits */

@Composable
private fun SectionHeader(title: String, trailing: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawTopLine(VvLine)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = VvType.mark, color = VvDim)
        trailing?.let { Text(it, style = VvType.mark, color = VvDim) }
    }
}

@Composable
private fun InfoRow(
    key: String,
    value: String,
    disabled: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawTopLine(VvLineSoft)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = VvType.label, color = if (disabled) VvOff else VvDim)
        Text(
            text = if (onClick != null) "$value ▸" else value,
            style = VvType.value,
            color = if (disabled) VvOff else VvInk,
        )
    }
}

@Composable
private fun IdentityRow(key: String, value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawTopLine(VvLineSoft)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = VvType.label, color = VvDim)
        TerminalTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.width(170.dp),
            textStyle = VvType.body.copy(fontSize = 14.sp, textAlign = TextAlign.End),
            singleLine = true,
        )
    }
}

@Composable
private fun QuickCommandRow(
    index: Int,
    value: String,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawTopLine(VvLineSoft)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d".format(index + 1), style = VvType.label, color = VvDim)
        Spacer(Modifier.width(10.dp))
        TerminalTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            textStyle = VvType.body.copy(fontSize = 14.sp),
            placeholder = "EMPTY",
            singleLine = true,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .border(BorderStroke(1.dp, VvLine))
                .clickable { onRemove() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text("DEL", style = VvType.mark.copy(fontSize = 10.sp), color = VvDim)
        }
    }
}

@Composable
private fun ActionRow(label: String, color: Color = VvGreen, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawTopLine(VvLineSoft)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = VvType.label, color = color)
    }
}

/** 手が滑って書き換えた内容を失わないよう、2 度押しで初期値に戻す。 */
@Composable
private fun ResetRow(onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(CONFIRM_TIMEOUT_MS)
            armed = false
        }
    }
    ActionRow(
        label = if (armed) "TAP AGAIN TO CONFIRM" else "RESET TO DEFAULTS",
        color = if (armed) VvRed else VvGreen,
    ) {
        if (armed) onConfirm()
        armed = !armed
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderRow(
    label: String,
    value: String,
    position: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .drawTopLine(VvLineSoft)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = VvType.label, color = VvDim)
            Text(value, style = VvType.value, color = VvInk)
        }
        // Material3 既定のつまみは太くて端末風から浮くので、
        // 1px のレールに細い縦棒という HTML 版と同じ見た目に置き換える。
        Slider(
            value = position,
            onValueChange = onChange,
            valueRange = range,
            thumb = {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(VvGreen),
                )
            },
            track = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(VvLine),
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TerminalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = VvType.body,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minHeight: Dp = Dp.Unspecified,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var imeWasShown by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible

    // 戻るキーでキーボードを閉じても、入力欄はフォーカスを持ったまま残る。
    // その状態でもう一度タップしても「すでにフォーカス済み」なのでキーボードが出てこない。
    // 閉じられたらフォーカスも手放し、次のタップを普通の「入力開始」に戻す。
    //
    // フォーカスした直後はキーボードがまだ上がっていないので、
    // 一度上がったのを見てから閉じたかどうかを判定する。
    LaunchedEffect(focused, imeVisible) {
        when {
            !focused -> imeWasShown = false
            imeVisible -> imeWasShown = true
            imeWasShown -> focusManager.clearFocus()
        }
    }

    Box(
        modifier = modifier
            .background(VvPanel)
            .border(BorderStroke(1.dp, VvLine))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty() && placeholder != null) {
            Text(placeholder, style = textStyle, color = VvOff)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle.merge(LocalTextStyle.current.copy(color = VvInk)).copy(color = VvInk),
            cursorBrush = SolidColor(VvGreen),
            singleLine = singleLine,
            // 高さは枠ではなく入力欄自身に持たせる。タップを受けるのは入力欄の範囲だけなので、
            // 枠だけを高くすると文字の無い部分が反応しない領域になる。
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .onFocusChanged { focused = it.isFocused },
        )
    }
}

/**
 * 再生中の実波形。VOICEVOX の PCM から作った包絡線を、再生位置を中心に流す。
 * 待機中は細い水平線。
 */
@Composable
private fun Waveform(
    player: com.example.voicetester.AudioPlayer,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        while (active) {
            withFrameNanos { }
            progress = player.progress
        }
        progress = 0f
    }

    androidx.compose.foundation.Canvas(modifier) {
        val mid = size.height / 2f
        val env = player.envelope
        if (!active || env.isEmpty()) {
            drawLine(VvLine, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1f)
            return@Canvas
        }

        val center = (progress * env.size).toInt()
        val half = WINDOW_BUCKETS / 2
        val path = Path()
        for (x in 0 until size.width.toInt()) {
            val t = x / size.width
            val idx = center - half + (t * WINDOW_BUCKETS).toInt()
            val amp = if (idx in env.indices) env[idx] else 0f
            // 端をすぼめて、画面外から湧いて出たように見えないようにする
            val taper = kotlin.math.sin(Math.PI * t).toFloat()
            val y = mid - amp * taper * mid * 0.92f
            if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
        }
        for (x in size.width.toInt() - 1 downTo 0) {
            val t = x / size.width
            val idx = center - half + (t * WINDOW_BUCKETS).toInt()
            val amp = if (idx in env.indices) env[idx] else 0f
            val taper = kotlin.math.sin(Math.PI * t).toFloat()
            path.lineTo(x.toFloat(), mid + amp * taper * mid * 0.92f)
        }
        drawPath(path, VvGreen, style = Stroke(width = 1.5f))
    }
}

/* ------------------------------------------------------------- helper */

private const val MIN_BOOT_MS = 1150
private const val WINDOW_BUCKETS = 150

/** RESET の 2 度押しを待つ時間。押しっぱなしで待たせない程度。 */
private const val CONFIRM_TIMEOUT_MS = 3000L

/** INPUT MESSAGE の入力欄の高さ。枠の上下 padding 10dp を足して従来どおり 168dp になる。 */
private val INPUT_MIN_HEIGHT = 148.dp

/** 細い罫線。枠線ではなく背景として引くので、行の高さに影響しない。 */
private fun Modifier.drawBottomLine(color: Color, width: Float = 1f) = drawBehind {
    val y = size.height - width / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = width)
}

private fun Modifier.drawTopLine(color: Color, width: Float = 1f) = drawBehind {
    val y = width / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = width)
}

private fun Modifier.drawStartLine(color: Color) = drawBehind {
    drawLine(color, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
}
