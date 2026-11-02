package ru.cheburnet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.cheburnet.LearningState
import ru.cheburnet.TargetCatalog
import ru.cheburnet.TargetResult
import ru.cheburnet.UiState
import ru.cheburnet.Verdict
import ru.cheburnet.VerdictLogic
import kotlin.random.Random

@Composable
fun HomeScreen(
    state: UiState,
    learning: LearningState,
    onCheck: () -> Unit,
    onStartLearning: () -> Unit,
    onShare: (String) -> Unit,
) {
    var detailsOpen by remember { mutableStateOf(false) }
    var learningOpen by remember { mutableStateOf(false) }
    val cheburnet = state.verdict == Verdict.CHEBURNET
    val litCountries = remember(state.results, state.verdict) {
        VerdictLogic.litCountries(state.results, state.verdict)
    }
    val arcCountries = remember(state.results) {
        state.results.filter { it.ok }.map { it.target.country }.toSet()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF02070E), Color(0xFF061726), Color(0xFF02070E)),
                ),
            ),
    ) {
        StarField()
        Globe(
            verdict = state.verdict,
            litCountries = litCountries,
            arcCountries = arcCountries,
            user = state.user,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Header(
                onLongPress = {
                    onStartLearning()
                    learningOpen = true
                },
            )
            state.userLabel?.let { label ->
                Text(
                    text = "Вы: $label",
                    color = DimText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            BottomPanel(
                state = state,
                onCheck = onCheck,
                onShowDetails = { detailsOpen = true },
            )
        }
    }

    if (detailsOpen && state.results.isNotEmpty()) {
        DetailsDialog(state = state, onDismiss = { detailsOpen = false })
    }

    if (learningOpen) {
        LearningDialog(
            learning = learning,
            onShare = onShare,
            onDismiss = { learningOpen = false },
        )
    }
}

@Composable
private fun LearningDialog(
    learning: LearningState,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF071A28),
        title = { Text("Режим обучения", color = IceWhite) },
        text = {
            Column {
                if (learning.running) {
                    Text(
                        "Проверяю ${learning.done} из ${learning.total} хостов…",
                        color = DimText,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (learning.total == 0) 0f
                            else learning.done.toFloat() / learning.total
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentCyan,
                        trackColor = Color(0xFF0E2A3D),
                    )
                } else {
                    Text(
                        "Готово: ${learning.total} хостов. Отправь отчёт боту — по нему " +
                            "я обновлю списки того, что реально работает в БС.",
                        color = DimText,
                    )
                }
            }
        },
        confirmButton = {
            val report = learning.report
            if (!learning.running && report != null) {
                TextButton(onClick = { onShare(report) }) {
                    Text("Отправить в Telegram", color = AccentCyan)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", color = DimText) }
        },
    )
}

@Composable
private fun Header(onLongPress: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(AccentCyan),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "ЧЕБУРНЕТ",
            style = MaterialTheme.typography.titleLarge,
            color = IceWhite,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "v1.43",
            style = MaterialTheme.typography.labelSmall,
            color = DimText,
        )
    }
}

@Composable
private fun BottomPanel(
    state: UiState,
    onCheck: () -> Unit,
    onShowDetails: () -> Unit,
) {
    val verdict = state.verdict
    val banner = verdict == Verdict.CHEBURNET || verdict == Verdict.WHITELIST_ONLY
    val bannerColor = if (verdict == Verdict.WHITELIST_ONLY) AlertAmber else IceWhite
    val accent = when (verdict) {
        Verdict.OPEN -> SignalGreen
        Verdict.PARTIAL -> AlertAmber
        Verdict.CHEBURNET -> IceWhite
        Verdict.WHITELIST_ONLY -> AlertAmber
        Verdict.OFFLINE -> Color(0xFFFF6B6B)
        else -> AccentSoft
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (banner) {
            Text(
                text = headline(verdict),
                color = bannerColor,
                fontSize = 27.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
        } else {
            Text(
                text = headline(verdict),
                color = accent,
                fontSize = if (verdict == Verdict.IDLE) 24.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = subtitle(state),
            color = if (banner) AccentSoft else DimText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onCheck,
            enabled = verdict != Verdict.CHECKING && !state.listsLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (verdict == Verdict.CHEBURNET) IceWhite else AccentCyan,
                contentColor = Color(0xFF04131C),
                disabledContainerColor = AccentCyan.copy(alpha = 0.35f),
                disabledContentColor = Color(0xFF04131C),
            ),
        ) {
            if (state.listsLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color(0xFF04131C),
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text("ЗАГРУЖАЕМ СПИСКИ…", fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 15.sp)
            } else if (verdict == Verdict.CHECKING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color(0xFF04131C),
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text("ПРОВЕРЯЕМ…", fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 15.sp)
            } else {
                Text(
                    text = if (state.results.isEmpty()) "ПРОВЕРИТЬ СВЯЗЬ" else "ПРОВЕРИТЬ СНОВА",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontSize = 15.sp,
                )
            }
        }

        if (state.results.isNotEmpty() && verdict != Verdict.CHECKING) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CounterChip(
                    label = "МИР",
                    value = "${state.intlOk}/${state.international.size}",
                    active = state.intlOk > 0,
                    onClick = onShowDetails,
                    modifier = Modifier.weight(1f),
                )
                CounterChip(
                    label = "РОССИЯ",
                    value = "${state.ruBlockedOk}/${state.ruBlocked.size}",
                    active = state.ruBlockedOk > 0,
                    onClick = onShowDetails,
                    modifier = Modifier.weight(1f),
                )
                CounterChip(
                    label = "БЕЛЫЙ СПИСОК",
                    value = "${state.ruOk}/${state.whitelist.size}",
                    active = state.ruOk > 0,
                    onClick = onShowDetails,
                    modifier = Modifier.weight(1f),
                )
            }
        }

    }
}

@Composable
private fun CounterChip(
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) AccentCyan.copy(alpha = 0.12f) else Color(0xFF0B1B27))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 8.sp,
            letterSpacing = 0.4.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) AccentCyan else DimText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) AccentCyan else DimText,
            maxLines = 1,
        )
    }
}

private fun headline(verdict: Verdict): String = when (verdict) {
    Verdict.IDLE -> "ПРОВЕРЬ СВЯЗЬ"
    Verdict.CHECKING -> "ПРОВЕРЯЕМ…"
    Verdict.OPEN -> "ЕСТЬ СВЯЗЬ"
    Verdict.PARTIAL -> "СВЯЗЬ ЧАСТИЧНАЯ"
    Verdict.CHEBURNET -> "ДОБРО ПОЖАЛОВАТЬ В ЧЕБУРНЕТ"
    Verdict.WHITELIST_ONLY -> "ТОЛЬКО БЕЛЫЕ СПИСКИ"
    Verdict.OFFLINE -> "НЕТ СВЯЗИ"
}

private fun subtitle(state: UiState): String = when (state.verdict) {
    Verdict.IDLE -> "${TargetCatalog.all.size} ресурсов: ${TargetCatalog.international.size} международных, " +
        "${TargetCatalog.ruBlocked.size} российских и ${TargetCatalog.whitelist.size} из белого списка. " +
        "Нажми кнопку — покажем, что доступно."
    Verdict.CHECKING -> "Проверено ${state.done} из ${state.total}"
    Verdict.OPEN -> "Мир: ${state.intlOk}/${state.international.size} · Россия: ${state.ruBlockedOk}/${state.ruBlocked.size} · белый список: ${state.ruOk}/${state.whitelist.size}"
    Verdict.PARTIAL -> "Часть мира недоступна. Мир: ${state.intlOk}/${state.international.size} · Россия: ${state.ruBlockedOk}/${state.ruBlocked.size}"
    Verdict.CHEBURNET -> "Зарубежные ресурсы недоступны, но российский интернет работает: доступны сервисы вне белого списка."
    Verdict.WHITELIST_ONLY -> "Работает только белый список: российские сервисы вне него отключены."
    Verdict.OFFLINE -> "Не отвечает ни один ресурс. Проверь подключение к сети."
}

@Composable
private fun StarField() {
    val stars = remember {
        val random = Random(42)
        List(170) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    }
    Canvas(Modifier.fillMaxSize()) {
        stars.forEach { (x, y, r) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.18f + 0.35f * r),
                radius = 0.6.dp.toPx() + 1.2.dp.toPx() * r,
                center = Offset(x * size.width, y * size.height),
            )
        }
    }
}

@Composable
private fun DetailsDialog(state: UiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF06131F),
        titleContentColor = IceWhite,
        textContentColor = DimText,
        title = {
            Column {
                Text("Результаты проверки")
                Text(
                    "Баллы: ${state.score}",
                    color = AccentCyan,
                    fontSize = 13.sp,
                )
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { SectionTitle("МЕЖДУНАРОДНЫЕ") }
                items(state.international, key = { it.target.host }) { ResultRow(it) }
                item { SectionTitle("РОССИЯ") }
                items(state.ruBlocked, key = { it.target.host }) { ResultRow(it) }
                item { SectionTitle("БЕЛЫЙ СПИСОК") }
                items(state.whitelist, key = { it.target.host }) { ResultRow(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", color = AccentCyan) }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = DimText,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ResultRow(result: TargetResult) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (result.ok) AccentCyan else Color(0xFFFF6B6B)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(result.target.title, color = IceWhite, fontSize = 14.sp)
            Text(result.target.host, color = DimText, fontSize = 11.sp)
        }
        Text(
            text = result.ms?.let { "$it мс" } ?: "нет",
            color = if (result.ok) AccentSoft else Color(0xFFFF6B6B),
            fontSize = 12.sp,
        )
    }
}
