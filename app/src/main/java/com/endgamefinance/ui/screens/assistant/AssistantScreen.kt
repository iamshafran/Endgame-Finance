package com.endgamefinance.ui.screens.assistant

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.ai.AiModel
import com.endgamefinance.data.ai.ModelState
import com.endgamefinance.data.ai.QueryEngine
import com.endgamefinance.ui.components.EndgameScaffold
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val EXAMPLES = listOf(
    "How much can I spend?",
    "What are my top 5 spending categories this year?",
    "What do my subscriptions cost per month?",
    "What's my net worth?",
    "How are my savings goals doing?",
)

/** One turn in the conversation. */
sealed interface ChatEntry {
    data class User(val text: String) : ChatEntry
    data class Answer(val answer: QueryEngine.Answer) : ChatEntry
    data class Error(val message: String) : ChatEntry
}

class AssistantViewModel(private val appContext: Context) : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatEntry>>(emptyList())
    val messages: StateFlow<List<ChatEntry>> = _messages.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    fun ask(question: String) {
        val q = question.trim()
        if (q.isBlank() || _thinking.value) return
        _messages.value += ChatEntry.User(q)
        _thinking.value = true
        viewModelScope.launch {
            val entry = try {
                ChatEntry.Answer(QueryEngine.ask(appContext, q))
            } catch (e: QueryEngine.QueryError) {
                ChatEntry.Error(e.message ?: "Something went wrong.")
            } catch (e: Exception) {
                ChatEntry.Error(e.message ?: e.javaClass.simpleName)
            }
            _messages.value += entry
            _thinking.value = false
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { AssistantViewModel(context.applicationContext) }
        }
    }
}

@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AssistantViewModel =
        viewModel(factory = AssistantViewModel.factory(LocalContext.current)),
) {
    val modelState by AiModel.state.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val thinking by viewModel.thinking.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the newest message in view as the conversation grows.
    LaunchedEffect(messages.size, thinking) {
        val count = messages.size + if (thinking) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun send() {
        if (draft.isBlank() || thinking) return
        viewModel.ask(draft)
        draft = ""
    }

    EndgameScaffold(title = "Assistant", onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            if (modelState !is ModelState.Ready) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) { ModelNotReady(modelState, onOpenSettings) }
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (messages.isEmpty()) {
                    item { EmptyStateIntro(onExample = { viewModel.ask(it) }) }
                }
                items(messages.size) { i ->
                    when (val m = messages[i]) {
                        is ChatEntry.User -> UserBubble(m.text)
                        is ChatEntry.Answer -> AnswerCard(m.answer)
                        is ChatEntry.Error -> ErrorBubble(m.message)
                    }
                }
                if (thinking) {
                    item { ThinkingBubble() }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Ask about your finances…") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
                FilledIconButton(
                    onClick = { send() },
                    enabled = draft.isNotBlank() && !thinking,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun EmptyStateIntro(onExample: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "Ask about your finances in plain language.",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Answers are computed on your device and never leave it. The AI can " +
                "read your data but can never change it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Try one of these:",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        EXAMPLES.forEach { ex ->
            Surface(
                onClick = { onExample(ex) },
                shape = androidx.compose.ui.graphics.RectangleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    ex,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp))
        Text(
            "Thinking on-device…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorBubble(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

@Composable
private fun AnswerCard(answer: QueryEngine.Answer) {
    var showDetails by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(answer.summary, style = MaterialTheme.typography.bodyLarge)

            answer.chart?.let { pairs ->
                if (pairs.isNotEmpty()) {
                    val max = pairs.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1.0)
                    Column(modifier = Modifier.padding(top = Spacing.md)) {
                        pairs.forEach { (label, value) ->
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(
                                            (kotlin.math.abs(value) / max).toFloat()
                                                .coerceIn(0.02f, 1f) * 0.75f,
                                        )
                                        .height(14.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            androidx.compose.ui.graphics.RectangleShape,
                                        ),
                                )
                                Text(
                                    Money.format((value * 100).toLong()),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }

            TextButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Hide query & data" else "Show query & data")
            }
            if (showDetails) {
                Text(
                    answer.sql,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                Text(
                    answer.result.columns.joinToString(" | "),
                    style = MaterialTheme.typography.labelMedium,
                )
                answer.result.rows.take(50).forEach { row ->
                    Text(
                        row.joinToString(" | ") { it?.toString() ?: "" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (answer.result.truncated) {
                    Text(
                        "…results truncated.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelNotReady(state: ModelState, onOpenSettings: () -> Unit) {
    Text("Assistant needs the AI model", style = MaterialTheme.typography.titleMedium)
    Text(
        when (state) {
            is ModelState.Downloading ->
                "The model is still downloading. Come back once it finishes."
            else ->
                "Download the on-device AI model (~2.6 GB, one time) to use the assistant. " +
                    "It then runs fully offline."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
        Text("Open AI settings")
    }
}
