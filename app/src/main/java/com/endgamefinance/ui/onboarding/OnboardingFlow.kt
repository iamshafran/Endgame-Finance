package com.endgamefinance.ui.onboarding

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.data.repo.EnvelopeRepository
import com.endgamefinance.data.repo.LedgerRepository
import com.endgamefinance.ui.screens.budget.BudgetMode
import com.endgamefinance.ui.screens.budget.BudgetPrefs
import com.endgamefinance.ui.theme.Spacing
import com.endgamefinance.util.Money
import java.util.UUID
import kotlinx.coroutines.launch

object OnboardingPrefs {
    private const val PREFS = "onboarding_prefs"
    private const val KEY_DONE = "completed"
    fun completed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)
    fun markCompleted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DONE, true).apply()
    }
}

/** Shows [content] normally; routes brand-new installs through the wizard first. */
@Composable
fun OnboardingGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        ready = if (OnboardingPrefs.completed(context)) {
            true
        } else {
            val hasAccounts = DatabaseProvider.get(context).accountDao().count() > 0
            if (hasAccounts) OnboardingPrefs.markCompleted(context) // existing install
            hasAccounts
        }
    }

    when (ready) {
        true -> content()
        false -> OnboardingFlow(
            onDone = {
                OnboardingPrefs.markCompleted(context)
                ready = true
            },
        )
        null -> Unit // one frame while the DB check runs
    }
}

private data class DraftAccount(val name: String, val type: String, val balanceCents: Long)

@Composable
private fun OnboardingFlow(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    val draftAccounts = remember { mutableStateOf(listOf<DraftAccount>()) }
    var firstAccountId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Step ${step + 1} of 4",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (step) {
            0 -> WelcomeStep(onNext = { step = 1 })
            1 -> AccountsStep(
                drafts = draftAccounts.value,
                onAdd = { draftAccounts.value = draftAccounts.value + it },
                onRemove = { index ->
                    draftAccounts.value =
                        draftAccounts.value.filterIndexed { i, _ -> i != index }
                },
                onNext = {
                    scope.launch {
                        val repo = LedgerRepository(DatabaseProvider.get(context))
                        draftAccounts.value.forEachIndexed { index, draft ->
                            val id = UUID.randomUUID().toString()
                            if (index == 0) firstAccountId = id
                            repo.createAccount(
                                Account(id = id, name = draft.name, type = draft.type),
                                initialBalanceCents =
                                if (draft.type == Account.TYPE_LIABILITY) -draft.balanceCents
                                else draft.balanceCents,
                            )
                        }
                        step = 2
                    }
                },
            )
            2 -> ModeStep(
                onPick = { mode ->
                    BudgetPrefs(context.applicationContext).setMode(mode)
                    step = 3
                },
            )
            3 -> EnvelopeStep(
                backingAccountId = firstAccountId,
                onFinish = { name, target ->
                    scope.launch {
                        if (name != null) {
                            EnvelopeRepository(DatabaseProvider.get(context))
                                .create(name, target, firstAccountId)
                        }
                        onDone()
                    }
                },
            )
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Text("Welcome to Endgame Finance", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Your money, only on your phone. No analytics, no tracking, nothing " +
            "sent anywhere. Everything is encrypted on your device, and backups " +
            "are files you hold. The only time this app uses the network is an " +
            "optional AI model download you start yourself — the AI then runs " +
            "fully offline.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "Let's set up your accounts — it takes about a minute.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
}

@Composable
private fun AccountsStep(
    drafts: List<DraftAccount>,
    onAdd: (DraftAccount) -> Unit,
    onRemove: (Int) -> Unit,
    onNext: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(Account.TYPE_ASSET) }
    var balanceText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Text("Your accounts", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Start with the account you use most — checking is typical. " +
            "You can add credit cards and loans now or later.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    drafts.forEachIndexed { index, draft ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "${draft.name} · ${draft.type} · ${Money.format(draft.balanceCents)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = { onRemove(index) }) { Text("Remove") }
        }
    }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Account name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(
            Account.TYPE_ASSET to "Asset",
            Account.TYPE_LIABILITY to "Liability",
            Account.TYPE_INVESTMENT to "Investment",
        ).forEach { (value, label) ->
            FilterChip(selected = type == value, onClick = { type = value },
                label = { Text(label) })
        }
    }
    OutlinedTextField(
        value = balanceText,
        onValueChange = { balanceText = it },
        label = {
            Text(if (type == Account.TYPE_LIABILITY) "Current debt" else "Current balance")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium)
    }
    OutlinedButton(
        onClick = {
            if (name.isBlank()) { error = "Name the account first"; return@OutlinedButton }
            val cents = balanceText.trim().takeIf { it.isNotEmpty() }?.let {
                Money.parse(it) ?: run { error = "Balance isn't a valid amount"; return@OutlinedButton }
            } ?: 0L
            onAdd(DraftAccount(name.trim(), type, cents))
            name = ""; balanceText = ""; error = null
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add account") }

    Button(
        onClick = onNext,
        enabled = drafts.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Continue with ${drafts.size} account${if (drafts.size == 1) "" else "s"}") }
}

@Composable
private fun ModeStep(onPick: (String) -> Unit) {
    Text("How do you want to budget?", style = MaterialTheme.typography.headlineSmall)
    OutlinedButton(
        onClick = { onPick(BudgetMode.ZERO_BASED) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Zero-Based — give every dollar a job") }
    Text(
        "You'll get a gentle nudge when income isn't allocated. Never a block.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = { onPick(BudgetMode.CASH_FLOW) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Cash-Flow — just track in vs out") }
    Text(
        "You can switch anytime in the Budget tab without losing anything.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EnvelopeStep(
    backingAccountId: String?,
    onFinish: (name: String?, targetCents: Long?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Text("A first savings goal? (optional)", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Envelopes are virtual buckets inside your real accounts — " +
            "an Emergency Fund is the classic first one.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Envelope name (e.g. Emergency Fund)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = targetText,
        onValueChange = { targetText = it },
        label = { Text("Target amount (optional)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium)
    }
    Button(
        onClick = {
            if (name.isBlank()) { error = "Name it, or skip below"; return@Button }
            val target = targetText.trim().takeIf { it.isNotEmpty() }?.let {
                Money.parse(it) ?: run { error = "Target isn't a valid amount"; return@Button }
            }
            onFinish(name.trim(), target)
        },
        enabled = backingAccountId != null,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Create and finish") }
    TextButton(
        onClick = { onFinish(null, null) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Skip — take me to my dashboard") }
}
