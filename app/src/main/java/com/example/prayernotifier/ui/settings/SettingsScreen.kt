package com.example.prayernotifier.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.prayernotifier.R
import com.example.prayernotifier.data.LocalUiGraph
import com.example.prayernotifier.data.PrayerMath
import com.example.prayernotifier.data.calculation.AsrMethod
import com.example.prayernotifier.data.calculation.CalculationMethod
import com.example.prayernotifier.data.persistence.AppSettings
import com.example.prayernotifier.data.persistence.PrayerNotificationSettings
import com.example.prayernotifier.data.persistence.SilenceSettings
import com.example.prayernotifier.i18n.AppLanguage
import com.example.prayernotifier.i18n.methodNameRes
import com.example.prayernotifier.i18n.prayerNameRes
import com.example.prayernotifier.ui.components.CircleButton
import com.example.prayernotifier.ui.components.EyebrowLabel
import com.example.prayernotifier.ui.components.MetaRow
import com.example.prayernotifier.ui.components.OrnamentDivider
import com.example.prayernotifier.ui.components.WonderCard
import com.example.prayernotifier.ui.components.WonderChip
import com.example.prayernotifier.ui.components.WonderDivider
import com.example.prayernotifier.ui.components.WonderPageHeader
import com.example.prayernotifier.ui.components.WonderPrimaryButton
import com.example.prayernotifier.ui.components.WonderTextButton
import com.example.prayernotifier.ui.theme.WonderAccent1
import com.example.prayernotifier.ui.theme.WonderAccent2
import com.example.prayernotifier.ui.theme.WonderBlack
import com.example.prayernotifier.ui.theme.WonderCaption
import com.example.prayernotifier.ui.theme.WonderCorners
import com.example.prayernotifier.ui.theme.WonderGreyMedium
import com.example.prayernotifier.ui.theme.WonderGreyStrong
import com.example.prayernotifier.ui.theme.WonderOffWhite
import com.example.prayernotifier.ui.theme.WonderSpacing
import com.example.prayernotifier.ui.theme.WonderWhite
import com.example.prayernotifier.ui.theme.thmanyahAvailable

private sealed interface Sheet {
    data object Hijri : Sheet
    data object Method : Sheet
    data object Asr : Sheet
    data class Prayer(val prayer: String) : Sheet
    data object Language : Sheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(visible: Boolean, onBack: () -> Unit) {
    val graph = LocalUiGraph.current
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(graph) as T
    })
    val state by vm.state.collectAsState()

    // Both pages stay composed, so re-read everything each time Settings is
    // opened: changes made on Home (a new location) show up.
    LaunchedEffect(visible) {
        if (visible) vm.refresh()
    }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    // Re-check system capabilities whenever the screen resumes.
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshCapabilities(notificationsAllowed = postNotificationsAllowed(context))
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    // Every authorization pops the system dialog first — system settings
    // only when the dialog can no longer appear (denied twice).
    // Always the real system dialog. Settings opens only when Android refused
    // to show it at all (denied twice before) - the one case with no dialog.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val blocked = !granted && answeredNotificationsBefore(context) && !notificationRationale(context)
        markNotificationsAnswered(context)
        vm.refreshCapabilities(notificationsAllowed = postNotificationsAllowed(context))
        if (blocked) openNotificationSettings(context)
    }
    fun requestSystemAuthorization() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Before Android 13 notifications have no runtime dialog; they can
            // only have been switched off in system settings.
            openNotificationSettings(context)
        }
    }
    /** Turning a reminder on is the natural moment to ask, if still off. */
    fun ensureNotificationsAllowed() {
        if (!postNotificationsAllowed(context)) requestSystemAuthorization()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed header: back circle + centered Tenor title — outside the list.
        WonderPageHeader(title = stringResource(R.string.settings), onBack = onBack)

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WonderAccent1)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = WonderSpacing.x24,
                    end = WonderSpacing.x24,
                    top = WonderSpacing.x8,
                    bottom = WonderSpacing.x48
                ),
                verticalArrangement = Arrangement.spacedBy(WonderSpacing.x16)
            ) {
                // Only notifications need the user: exact timing is granted
                // without a prompt, and falls back to inexact if revoked.
                if (!state.notificationsAllowed) {
                    item {
                        PermissionBanner(
                            title = stringResource(R.string.notifications_off),
                            body = stringResource(R.string.permission_needed),
                            action = stringResource(R.string.enable),
                            onClick = { requestSystemAuthorization() }
                        )
                    }
                }

                item {
                    SectionEyebrow(stringResource(R.string.section_notifications))
                    ReminderForAllCard(
                        settings = state.settings,
                        onSelect = { vm.setReminderForAll(it) }
                    )
                }

                item {
                    SectionEyebrow(stringResource(R.string.prayers))
                    WonderCard {
                        vm.prayerNames().forEachIndexed { index, prayer ->
                            PrayerRow(
                                prayer = prayer,
                                reminder = state.settings.getSettingsForPrayer(prayer),
                                adjustment = vm.adjustmentOf(prayer),
                                onOpen = { sheet = Sheet.Prayer(prayer) },
                                onToggle = { on ->
                                    val notif = state.settings.getSettingsForPrayer(prayer)
                                    vm.setPrayerNotifications(prayer, notif.copy(enabled = on))
                                    if (on) ensureNotificationsAllowed()
                                }
                            )
                            if (index < vm.prayerNames().lastIndex) WonderDivider()
                        }
                    }
                }

                item {
                    SectionEyebrow(stringResource(R.string.section_jumua))
                    WonderCard {
                        PrayerRow(
                            prayer = PrayerMath.JUMUA,
                            reminder = state.settings.jumuaSettings,
                            adjustment = 0,
                            onOpen = { sheet = Sheet.Prayer(PrayerMath.JUMUA) },
                            onToggle = { on ->
                                vm.setPrayerNotifications(
                                    PrayerMath.JUMUA, state.settings.jumuaSettings.copy(enabled = on)
                                )
                                if (on) ensureNotificationsAllowed()
                            }
                        )
                    }
                }

                item {
                    SectionEyebrow(stringResource(R.string.section_dnd))
                    // Granted only from Android's own settings screen, never a dialog.
                    if (DND_SUPPORTED && state.settings.silence.enabled && !state.dndAllowed) {
                        PermissionBanner(
                            title = stringResource(R.string.dnd_access_title),
                            body = stringResource(R.string.dnd_access_desc),
                            action = stringResource(R.string.allow),
                            onClick = { openDndAccessSettings(context) }
                        )
                        Spacer(Modifier.height(WonderSpacing.x16))
                    }
                    SilenceCard(
                        silence = state.settings.silence,
                        onToggle = { vm.setSilenceEnabled(it) },
                        onTogglePrayer = { prayer, on -> vm.setSilenceFor(prayer, on) }
                    )
                }

                item {
                    SectionEyebrow(stringResource(R.string.section_prayer_times))
                    WonderCard {
                        MetaRow(
                            label = stringResource(R.string.calculation_method),
                            value = if (state.settings.chosenMethod == null) {
                                stringResource(R.string.method_automatic_value, stringResource(methodNameRes(state.method)))
                            } else {
                                stringResource(methodNameRes(state.method))
                            },
                            onClick = { sheet = Sheet.Method }
                        )
                        WonderDivider()
                        MetaRow(
                            label = stringResource(R.string.asr_time),
                            value = if (state.settings.chosenAsr == null) {
                                stringResource(R.string.method_automatic_value, stringResource(asrNameRes(state.asr)))
                            } else {
                                stringResource(asrNameRes(state.asr))
                            },
                            onClick = { sheet = Sheet.Asr }
                        )
                    }
                }

                item {
                    SectionEyebrow(stringResource(R.string.section_calendar))
                    WonderCard {
                        MetaRow(
                            label = stringResource(R.string.hijri_correction),
                            value = offsetLabel(state.settings.hijriDateAdjustment, days = true),
                            onClick = { sheet = Sheet.Hijri }
                        )
                    }
                }

                item {
                    SectionEyebrow(stringResource(R.string.section_language))
                    WonderCard {
                        MetaRow(
                            label = stringResource(R.string.app_language),
                            value = languageLabel(AppLanguage.current(context)),
                            onClick = { sheet = Sheet.Language }
                        )
                    }
                }

                item {
                    OrnamentDivider(
                        color = WonderAccent2,
                        modifier = Modifier.padding(top = WonderSpacing.x24, bottom = WonderSpacing.x8)
                    )
                    Text(
                        text = stringResource(R.string.attribution),
                        style = MaterialTheme.typography.labelSmall,
                        color = WonderCaption,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // The Thmanyah files are optional (git-ignored); credit
                    // them only in builds that actually bundle them.
                    if (thmanyahAvailable(context)) {
                        Text(
                            text = stringResource(R.string.attribution_font),
                            style = MaterialTheme.typography.labelSmall,
                            color = WonderCaption,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    sheet?.let { current ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = WonderCorners.panel, topEnd = WonderCorners.panel),
            containerColor = WonderGreyStrong
        ) {
            when (current) {
                Sheet.Hijri -> StepperSheet(
                    title = stringResource(R.string.hijri_correction),
                    description = stringResource(R.string.hijri_correction_desc),
                    value = state.settings.hijriDateAdjustment,
                    min = -2,
                    max = 2,
                    valueLabel = { offsetLabel(it, days = true) },
                    onSave = { vm.setHijriOffset(it); sheet = null },
                    onCancel = { sheet = null }
                )
                Sheet.Method -> MethodSheet(
                    chosen = state.settings.chosenMethod,
                    automatic = state.automaticMethod,
                    onSelect = { vm.setMethod(it); sheet = null }
                )
                Sheet.Asr -> AsrSheet(
                    chosen = state.settings.chosenAsr,
                    automatic = state.automaticAsr,
                    onSelect = { vm.setAsr(it); sheet = null }
                )
                is Sheet.Prayer -> PrayerSheet(
                    prayer = current.prayer,
                    calculatedTime = state.today?.let { PrayerMath.timeOf(it, current.prayer) },
                    reminder = state.settings.getSettingsForPrayer(current.prayer),
                    adjustment = vm.adjustmentOf(current.prayer),
                    onSave = { notif, adjustment ->
                        vm.savePrayer(current.prayer, notif, adjustment)
                        if (notif.enabled) ensureNotificationsAllowed()
                        sheet = null
                    },
                    onCancel = { sheet = null }
                )
                Sheet.Language -> LanguageSheet(
                    current = AppLanguage.current(context),
                    onSelect = { tag ->
                        sheet = null
                        // Recreates the Activity in the new language; the
                        // shell keeps Settings open across the recreation.
                        AppLanguage.set(context, tag)
                    }
                )
            }
        }
    }
}

@Composable
private fun languageLabel(tag: String): String = when (tag) {
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
    AppLanguage.ARABIC -> stringResource(R.string.language_arabic)
    else -> stringResource(R.string.language_system)
}

/**
 * Language picker. Mirrors the system's per-app language screen (Android
 * 13+: Settings → Apps → Prayer Notifier → Language); both stay in sync.
 */
@Composable
private fun LanguageSheet(current: String, onSelect: (String) -> Unit) {
    SheetColumn(title = stringResource(R.string.app_language)) {
        listOf(
            AppLanguage.SYSTEM to stringResource(R.string.language_system_desc),
            AppLanguage.ENGLISH to null,
            AppLanguage.ARABIC to null
        ).forEachIndexed { index, (tag, description) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(WonderCorners.card))
                    .clickable(role = Role.RadioButton, onClick = { onSelect(tag) })
                    .padding(horizontal = WonderSpacing.x8, vertical = WonderSpacing.x16),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = languageLabel(tag),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (tag == current) WonderAccent1 else WonderOffWhite
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = WonderAccent2
                        )
                    }
                }
                if (tag == current) {
                    Icon(
                        painter = painterResource(R.drawable.ph_check_light),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = WonderAccent1
                    )
                }
            }
            if (index < 2) HorizontalDivider(thickness = 1.dp, color = WonderBlack)
        }
    }
}

/** Section ceremony: uppercase Tenor label between rules. */
@Composable
private fun SectionEyebrow(text: String) {
    EyebrowLabel(
        text = text,
        color = WonderAccent2,
        modifier = Modifier.padding(top = WonderSpacing.x16, bottom = WonderSpacing.x16)
    )
}

@Composable
private fun wonderSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = WonderWhite,
    checkedTrackColor = WonderAccent1,
    checkedBorderColor = WonderAccent1,
    uncheckedThumbColor = WonderGreyMedium,
    uncheckedTrackColor = WonderBlack,
    uncheckedBorderColor = WonderGreyMedium
)

/** Accent-ruled callout, like Wonderous' pull quote: 1dp orange rule at left. */
@Composable
private fun PermissionBanner(title: String, body: String, action: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(WonderCorners.card),
        color = WonderGreyStrong
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(WonderSpacing.x24),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(WonderAccent1)
            )
            Spacer(Modifier.width(WonderSpacing.x16))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                    color = WonderOffWhite
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = WonderAccent2
                )
            }
            Spacer(Modifier.width(WonderSpacing.x12))
            Text(
                text = action.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = WonderAccent1,
                modifier = Modifier.padding(WonderSpacing.x8)
            )
        }
    }
}

/** Choices offered wherever a reminder lead time is picked. */
private val REMINDER_CHOICES = listOf(0, 5, 10, 15, 30)

/** Jumua's: people leave earlier for the sermon. */
private val JUMUA_REMINDER_CHOICES = listOf(0, 15, 30, 45, 60)

/** Do Not Disturb rules the app can switch on and off need Android 10. */
private val DND_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/**
 * The master switch, then one switch per prayer with how long the phone
 * stays silent (Fajr 35 min, Jumua 1 hour, the others 15 min).
 */
@Composable
private fun SilenceCard(
    silence: SilenceSettings,
    onToggle: (Boolean) -> Unit,
    onTogglePrayer: (String, Boolean) -> Unit
) {
    WonderCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = DND_SUPPORTED, role = Role.Switch) { onToggle(!silence.enabled) }
                .padding(horizontal = WonderSpacing.x24, vertical = WonderSpacing.x16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dnd_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = WonderOffWhite
                )
                Spacer(Modifier.height(WonderSpacing.x4))
                Text(
                    text = stringResource(if (DND_SUPPORTED) R.string.dnd_desc else R.string.dnd_needs_android10),
                    style = MaterialTheme.typography.bodySmall,
                    color = WonderAccent2
                )
            }
            Spacer(Modifier.width(WonderSpacing.x12))
            Switch(
                checked = DND_SUPPORTED && silence.enabled,
                onCheckedChange = onToggle,
                enabled = DND_SUPPORTED,
                colors = wonderSwitchColors()
            )
        }
        if (DND_SUPPORTED && silence.enabled) {
            (PrayerMath.ORDER + PrayerMath.JUMUA).forEach { prayer ->
                WonderDivider()
                SilenceRow(
                    prayer = prayer,
                    on = silence.isOnFor(prayer),
                    onToggle = { onTogglePrayer(prayer, it) }
                )
            }
        }
    }
}

@Composable
private fun SilenceRow(prayer: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    val name = stringResource(prayerNameRes(prayer))
    val minutes = SilenceSettings.minutesFor(prayer)
    val description = stringResource(R.string.cd_dnd_prayer, name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch, onClickLabel = description) { onToggle(!on) }
            .padding(horizontal = WonderSpacing.x24, vertical = WonderSpacing.x12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, color = WonderOffWhite)
            Text(
                text = if (minutes % 60 == 0) {
                    stringResource(R.string.dnd_for_hour)
                } else {
                    pluralStringResource(R.plurals.dnd_for_minutes, minutes, minutes.toString())
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (on) WonderAccent2 else WonderCaption
            )
        }
        Switch(checked = on, onCheckedChange = onToggle, colors = wonderSwitchColors())
    }
}

@Composable
private fun reminderChoiceLabel(minutes: Int): String =
    if (minutes == 0) {
        stringResource(R.string.on_time)
    } else {
        pluralStringResource(R.plurals.offset_minutes, minutes, minutes.toString())
    }

/**
 * One control for the common case: the same lead time for every prayer.
 * No chip is selected when prayers differ; the hint points to the rows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderForAllCard(settings: AppSettings, onSelect: (Int) -> Unit) {
    val leads = PrayerMath.ORDER.map { settings.getSettingsForPrayer(it).prePrayerReminderMinutes }.distinct()
    val shared = leads.singleOrNull()
    WonderCard {
        Column(Modifier.padding(horizontal = WonderSpacing.x24, vertical = WonderSpacing.x16)) {
            Text(
                text = stringResource(R.string.before_every_prayer).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = WonderAccent2
            )
            Spacer(Modifier.height(WonderSpacing.x12))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WonderSpacing.x8),
                verticalArrangement = Arrangement.spacedBy(WonderSpacing.x8)
            ) {
                REMINDER_CHOICES.forEach { minutes ->
                    WonderChip(
                        text = reminderChoiceLabel(minutes),
                        selected = minutes == shared,
                        onClick = { onSelect(minutes) }
                    )
                }
            }
            if (shared == null || shared !in REMINDER_CHOICES) {
                Spacer(Modifier.height(WonderSpacing.x8))
                Text(
                    text = stringResource(R.string.custom_per_prayer),
                    style = MaterialTheme.typography.labelSmall,
                    color = WonderCaption
                )
            }
        }
    }
}

/**
 * One prayer, one row: name, a summary of both its settings, and a bell
 * that toggles the reminder in place. Tap the row for the detail sheet.
 */
@Composable
private fun PrayerRow(
    prayer: String,
    reminder: PrayerNotificationSettings,
    adjustment: Int,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val name = stringResource(prayerNameRes(prayer))
    val reminderText = if (reminder.enabled) {
        if (reminder.prePrayerReminderMinutes == 0) {
            stringResource(R.string.at_prayer_time)
        } else {
            pluralStringResource(
                R.plurals.minutes_before,
                reminder.prePrayerReminderMinutes,
                reminder.prePrayerReminderMinutes.toString()
            )
        }
    } else {
        stringResource(R.string.silent)
    }
    val summary = if (adjustment == 0) {
        reminderText
    } else {
        reminderText + " · " + stringResource(R.string.summary_time, offsetLabel(adjustment, days = false))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = WonderSpacing.x24, end = WonderSpacing.x8, top = WonderSpacing.x12, bottom = WonderSpacing.x12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                color = WonderOffWhite
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (reminder.enabled) WonderAccent2 else WonderCaption
            )
        }
        IconButton(
            onClick = { onToggle(!reminder.enabled) },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (reminder.enabled) R.drawable.ph_bell_ringing_light else R.drawable.ph_bell_slash_light
                ),
                contentDescription = stringResource(
                    if (reminder.enabled) R.string.cd_reminder_off else R.string.cd_reminder_on, name
                ),
                tint = if (reminder.enabled) WonderAccent1 else WonderGreyMedium
            )
        }
    }
}

/**
 * Automatic first (the method of the place's country, named), then every
 * method by name with its angles, for matching a local mosque.
 */
@Composable
private fun MethodSheet(
    chosen: CalculationMethod?,
    automatic: CalculationMethod,
    onSelect: (CalculationMethod?) -> Unit
) {
    SheetColumn(title = stringResource(R.string.calculation_method)) {
        Text(
            text = stringResource(R.string.method_sheet_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = WonderAccent2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(WonderSpacing.x8))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            MethodOption(
                title = stringResource(R.string.method_automatic),
                description = stringResource(R.string.method_automatic_desc, stringResource(methodNameRes(automatic))),
                selected = chosen == null,
                onClick = { onSelect(null) }
            )
            CalculationMethod.entries.forEach { method ->
                HorizontalDivider(thickness = 1.dp, color = WonderBlack)
                MethodOption(
                    title = stringResource(methodNameRes(method)),
                    description = methodAngles(method),
                    selected = chosen == method,
                    onClick = { onSelect(method) }
                )
            }
        }
    }
}

@Composable
private fun MethodOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WonderCorners.card))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = WonderSpacing.x8, vertical = WonderSpacing.x12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) WonderAccent1 else WonderOffWhite
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = WonderAccent2
            )
        }
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ph_check_light),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = WonderAccent1
            )
        }
    }
}

/** Automatic (the country's rule, named), then the two rules explained. */
@Composable
private fun AsrSheet(
    chosen: AsrMethod?,
    automatic: AsrMethod,
    onSelect: (AsrMethod?) -> Unit
) {
    SheetColumn(title = stringResource(R.string.asr_time)) {
        MethodOption(
            title = stringResource(R.string.method_automatic),
            description = stringResource(R.string.method_automatic_desc, stringResource(asrNameRes(automatic))),
            selected = chosen == null,
            onClick = { onSelect(null) }
        )
        AsrMethod.entries.forEach { asr ->
            HorizontalDivider(thickness = 1.dp, color = WonderBlack)
            MethodOption(
                title = stringResource(asrNameRes(asr)),
                description = stringResource(
                    if (asr == AsrMethod.HANAFI) R.string.asr_hanafi_desc else R.string.asr_standard_desc
                ),
                selected = chosen == asr,
                onClick = { onSelect(asr) }
            )
        }
    }
}

private fun asrNameRes(asr: AsrMethod): Int =
    if (asr == AsrMethod.HANAFI) R.string.asr_hanafi else R.string.asr_standard

/** "Fajr 18° · Isha 17°", or Isha as minutes after Maghrib. */
@Composable
private fun methodAngles(method: CalculationMethod): String {
    fun degrees(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    return if (method.ishaMinutes > 0) {
        stringResource(R.string.method_angles_interval, degrees(method.fajrAngle), method.ishaMinutes.toString())
    } else {
        stringResource(R.string.method_angles, degrees(method.fajrAngle), degrees(method.ishaAngle))
    }
}

/** Stepper editor: minus / Tenor value / plus, dark Save + quiet Cancel. */
@Composable
private fun StepperSheet(
    title: String,
    description: String,
    value: Int,
    min: Int,
    max: Int,
    valueLabel: @Composable (Int) -> String,
    onSave: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var draft by remember(value) { mutableIntStateOf(value) }
    SheetColumn(title = title) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = WonderAccent2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(WonderSpacing.x32))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleButton(
                icon = R.drawable.ph_minus_light,
                contentDescription = stringResource(R.string.decrease),
                onClick = { if (draft > min) draft-- },
                containerColor = WonderBlack
            )
            Text(
                text = valueLabel(draft),
                style = MaterialTheme.typography.headlineMedium,
                color = WonderAccent1,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(176.dp)
            )
            CircleButton(
                icon = R.drawable.ph_plus_light,
                contentDescription = stringResource(R.string.increase),
                onClick = { if (draft < max) draft++ },
                containerColor = WonderBlack
            )
        }
        Spacer(Modifier.height(WonderSpacing.x32))
        WonderPrimaryButton(text = stringResource(R.string.save), onClick = { onSave(draft) }, containerColor = WonderBlack)
        Spacer(Modifier.height(WonderSpacing.x8))
        WonderTextButton(text = stringResource(R.string.cancel), onClick = onCancel)
    }
}

/**
 * Everything about one prayer in one place: reminder on/off and lead time,
 * then the time correction. One Save for both.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrayerSheet(
    prayer: String,
    /** Today's time before correction; null when there is no place yet. */
    calculatedTime: String?,
    reminder: PrayerNotificationSettings,
    adjustment: Int,
    onSave: (PrayerNotificationSettings, Int) -> Unit,
    onCancel: () -> Unit
) {
    var enabled by remember(reminder) { mutableStateOf(reminder.enabled) }
    var minutes by remember(reminder) { mutableIntStateOf(reminder.prePrayerReminderMinutes) }
    var shift by remember(adjustment) { mutableIntStateOf(adjustment) }
    val isJumua = prayer == PrayerMath.JUMUA
    val choices = ((if (isJumua) JUMUA_REMINDER_CHOICES else REMINDER_CHOICES) + minutes).distinct().sorted()
    SheetColumn(title = stringResource(prayerNameRes(prayer))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.reminder).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = WonderAccent2,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = wonderSwitchColors())
        }
        if (enabled) {
            Spacer(Modifier.height(WonderSpacing.x12))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WonderSpacing.x8),
                verticalArrangement = Arrangement.spacedBy(WonderSpacing.x8)
            ) {
                choices.forEach { choice ->
                    WonderChip(
                        text = reminderChoiceLabel(choice),
                        selected = choice == minutes,
                        onClick = { minutes = choice }
                    )
                }
            }
        }

        if (isJumua) {
            Spacer(Modifier.height(WonderSpacing.x24))
            Text(
                text = stringResource(R.string.jumua_note),
                style = MaterialTheme.typography.bodySmall,
                color = WonderCaption
            )
        } else {
            Spacer(Modifier.height(WonderSpacing.x24))
            Text(
                text = stringResource(R.string.time_correction).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = WonderAccent2
            )
            Spacer(Modifier.height(WonderSpacing.x4))
            Text(
                text = stringResource(R.string.prayer_correction_desc),
                style = MaterialTheme.typography.bodySmall,
                color = WonderCaption
            )
            if (calculatedTime != null) {
                Spacer(Modifier.height(WonderSpacing.x16))
                CorrectionPreview(calculatedTime, shift)
            }
            Spacer(Modifier.height(WonderSpacing.x12))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleButton(
                    icon = R.drawable.ph_minus_light,
                    contentDescription = stringResource(R.string.decrease),
                    onClick = { if (shift > -30) shift-- },
                    containerColor = WonderBlack
                )
                Text(
                    text = offsetLabel(shift, days = false),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (shift == 0) WonderOffWhite else WonderAccent1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(176.dp)
                )
                CircleButton(
                    icon = R.drawable.ph_plus_light,
                    contentDescription = stringResource(R.string.increase),
                    onClick = { if (shift < 30) shift++ },
                    containerColor = WonderBlack
                )
            }
        }

        Spacer(Modifier.height(WonderSpacing.x32))
        WonderPrimaryButton(
            text = stringResource(R.string.save),
            onClick = { onSave(PrayerNotificationSettings(enabled, minutes), shift) },
            containerColor = WonderBlack
        )
        Spacer(Modifier.height(WonderSpacing.x8))
        WonderTextButton(text = stringResource(R.string.cancel), onClick = onCancel)
    }
}

/**
 * Today's time as calculated and, while a correction is set, the time it
 * becomes: "12:35 › 12:40". Updates with every tap of the stepper.
 */
@Composable
private fun CorrectionPreview(calculatedTime: String, shift: Int) {
    val corrected = PrayerMath.adjustTime(calculatedTime, shift)
    val description = if (shift == 0) {
        stringResource(R.string.cd_correction_preview, calculatedTime)
    } else {
        stringResource(R.string.cd_correction_preview_shifted, calculatedTime, corrected)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.today).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = WonderAccent2
        )
        Spacer(Modifier.height(WonderSpacing.x4))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = calculatedTime,
                style = MaterialTheme.typography.headlineMedium,
                color = if (shift == 0) WonderOffWhite else WonderCaption
            )
            if (shift != 0) {
                Icon(
                    painter = painterResource(R.drawable.ph_caret_right_light),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = WonderSpacing.x8)
                        .size(20.dp),
                    tint = WonderCaption
                )
                Text(
                    text = corrected,
                    style = MaterialTheme.typography.headlineMedium,
                    color = WonderAccent1
                )
            }
        }
    }
}

/** Sheet ceremony: centered Tenor title, ornament, then content. */
@Composable
private fun SheetColumn(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = WonderSpacing.x24)
            .padding(bottom = WonderSpacing.x32)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = WonderOffWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = WonderSpacing.x8)
        )
        OrnamentDivider(Modifier.padding(horizontal = WonderSpacing.x24, vertical = WonderSpacing.x8))
        Spacer(Modifier.height(WonderSpacing.x8))
        content()
    }
}

@Composable
private fun offsetLabel(value: Int, days: Boolean): String {
    if (value == 0) return stringResource(R.string.no_change)
    val signed = if (value > 0) "+$value" else "$value"
    val plurals = if (days) R.plurals.offset_days else R.plurals.offset_minutes
    return pluralStringResource(plurals, kotlin.math.abs(value), signed)
}

private fun postNotificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun notificationRationale(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    val activity = context as? Activity ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity, Manifest.permission.POST_NOTIFICATIONS
    )
}

/** Set only after the user actually answered the dialog, never before. */
private fun answeredNotificationsBefore(context: Context): Boolean =
    context.getSharedPreferences(NOTIF_PERM_PREFS, Context.MODE_PRIVATE).getBoolean(NOTIF_ANSWERED, false)

private fun markNotificationsAnswered(context: Context) {
    context.getSharedPreferences(NOTIF_PERM_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(NOTIF_ANSWERED, true).apply()
}

private const val NOTIF_PERM_PREFS = "settings_notif_perm"
private const val NOTIF_ANSWERED = "notifications_answered"

/** Android's "Do Not Disturb access" list, where the app can be allowed. */
private fun openDndAccessSettings(context: Context) {
    context.startActivity(
        Intent(SystemSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
