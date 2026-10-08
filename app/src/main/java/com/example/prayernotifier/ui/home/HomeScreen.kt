package com.example.prayernotifier.ui.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.prayernotifier.R
import com.example.prayernotifier.data.LocalUiGraph
import com.example.prayernotifier.data.PrayerMath
import com.example.prayernotifier.data.calculation.PrayerCalculator
import com.example.prayernotifier.data.persistence.PrayerNotificationSettings
import com.example.prayernotifier.i18n.prayerNameRes
import com.example.prayernotifier.ui.components.ArchShape
import com.example.prayernotifier.ui.components.CircleButton
import com.example.prayernotifier.ui.components.EyebrowLabel
import com.example.prayernotifier.ui.components.MetaRow
import com.example.prayernotifier.ui.components.OrnamentDivider
import com.example.prayernotifier.ui.components.PrayerIllustration
import com.example.prayernotifier.ui.components.WonderDivider
import com.example.prayernotifier.ui.components.WonderEmptyState
import com.example.prayernotifier.ui.components.WonderPrimaryButton
import com.example.prayernotifier.ui.components.WonderTextButton
import com.example.prayernotifier.ui.theme.WonderAccent1
import com.example.prayernotifier.ui.theme.WonderAccent2
import com.example.prayernotifier.ui.theme.WonderBlack
import com.example.prayernotifier.ui.theme.WonderCorners
import com.example.prayernotifier.ui.theme.WonderGreyMedium
import com.example.prayernotifier.ui.theme.WonderGreyStrong
import com.example.prayernotifier.ui.theme.WonderOffWhite
import com.example.prayernotifier.ui.theme.WonderSpacing
import com.example.prayernotifier.ui.theme.WonderWhite
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The prayer's name in the other script sits under each time, like "BCE"
 * under a Wonderous year: Arabic in English, the English name in Arabic.
 */
private val ARABIC_NAMES: Map<String, String> = mapOf(
    "Fajr" to "الفجر",
    "Dhuhr" to "الظهر",
    "Asr" to "العصر",
    "Maghrib" to "المغرب",
    "Isha" to "العشاء"
)

/** Room the floating header needs above the scrolling content. */
private val HEADER_SPACE = 72.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(visible: Boolean, onOpenSettings: () -> Unit) {
    val graph = LocalUiGraph.current
    val vm: HomeViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(graph) as T
    })
    val state by vm.state.collectAsState()

    // Returning from the Settings page doesn't fire ON_RESUME — pick up
    // edited adjustments / reminders here instead.
    LaunchedEffect(visible) {
        if (visible) vm.refreshSettings()
    }

    // Settings (adjustments, Hijri offset) may change in the other tab —
    // reload them whenever Home becomes visible again. Also recovers when
    // the location permission was granted elsewhere while we showed the
    // permission error.
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onResumed(hasLocationPermission(context))
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    // Any system dialog of the first-run location step on screen: the
    // notification request waits for it, so dialogs never stack.
    var locationDialogOpen by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        locationDialogOpen = false
        val granted = grants.values.any { it }
        // Blocked = Android answered without showing the dialog (denied twice
        // before). Only then does the app point to Settings, and only on tap.
        val blocked = !granted && answeredLocationBefore(context) && !locationRationale(context)
        markLocationAnswered(context)
        vm.onPermissionResult(granted = granted, locked = blocked)
    }

    /** Always the real system dialog; Android decides whether it can show. */
    fun askLocationPermission() {
        locationDialogOpen = true
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // "Turn on device location?" — Google Play services' in-app dialog, so
    // switching location on never means leaving the app.
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        locationDialogOpen = false
        if (result.resultCode == Activity.RESULT_OK) vm.refreshLocation()
    }

    fun turnOnLocation() {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build())
            .setAlwaysShow(true)
            .build()
        locationDialogOpen = true
        LocationServices.getSettingsClient(context).checkLocationSettings(request)
            .addOnSuccessListener {
                locationDialogOpen = false
                vm.refreshLocation()
            }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    locationSettingsLauncher.launch(IntentSenderRequest.Builder(e.resolution).build())
                } else {
                    // No Play services dialog available on this device.
                    locationDialogOpen = false
                    openLocationSettings(context)
                }
            }
    }

    /** "Use current location" and every retry: permission, then location on, then fix. */
    fun requestLocation() {
        when {
            !hasLocationPermission(context) -> askLocationPermission()
            !isLocationOn(context) -> turnOnLocation()
            else -> vm.refreshLocation()
        }
    }

    LaunchedEffect(state.askForPermission) {
        // First run: the permission dialog comes first, before anything else.
        if (state.askForPermission) {
            if (hasLocationPermission(context)) {
                vm.onPermissionResult(granted = true, locked = false)
            } else {
                askLocationPermission()
            }
        }
    }

    // Location switched off: show the in-app "turn on" dialog by itself,
    // once per launch; after that the screen's button offers it again.
    var askedToTurnOnLocation by rememberSaveable { mutableStateOf(false) }
    val locationOff = (state.error as? HomeError.LocationRequired)?.cause == LocationCause.ServiceDisabled
    LaunchedEffect(locationOff) {
        if (locationOff && !askedToTurnOnLocation) {
            askedToTurnOnLocation = true
            turnOnLocation()
        }
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showPlaces by remember { mutableStateOf(false) }

    // First run: ask for notifications (Android 13+) once the location step
    // is settled (times on screen, or an error the user can read), never on
    // top of a location dialog. Asked once; later the Settings banner offers it.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val locationStepSettled = !state.loading && !state.askForPermission && !locationDialogOpen &&
        (state.days.isNotEmpty() || state.error != null)
    LaunchedEffect(locationStepSettled) {
        if (locationStepSettled && shouldAskNotificationsOnce(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val noticeText = state.notice?.let { noticeMessage(it) }
    val noticeAction = when (val n = state.notice) {
        is HomeNotice.LocationFailed -> when (n.cause) {
            LocationCause.ServiceDisabled -> stringResource(R.string.open_settings)
            LocationCause.PermissionLocked -> stringResource(R.string.open_settings)
            else -> stringResource(R.string.try_again)
        }
        HomeNotice.LoadFailed -> stringResource(R.string.try_again)
        else -> null
    }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = noticeText.orEmpty(),
            actionLabel = noticeAction,
            duration = SnackbarDuration.Short
        )
        vm.consumeNotice()
        if (result == SnackbarResult.ActionPerformed) {
            when (notice) {
                is HomeNotice.LocationFailed -> when (notice.cause) {
                    LocationCause.ServiceDisabled -> turnOnLocation()
                    LocationCause.PermissionLocked -> openAppSettings(context)
                    else -> requestLocation()
                }
                HomeNotice.LoadFailed -> vm.retry()
                else -> Unit
            }
        }
    }

    val today = LocalDate.now()
    val isToday = state.selectedDate == today
    val day = state.days.firstOrNull { it.date == state.selectedDate }

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading && state.days.isEmpty() -> LoadingState()
            state.error != null && state.days.isEmpty() ->
                HomeErrorState(
                    error = state.error!!,
                    onShareLocation = { requestLocation() },
                    onOpenAppSettings = { openAppSettings(context) },
                    onOpenLocationSettings = { turnOnLocation() },
                    onRetry = { vm.retry() }
                )
            day == null -> LoadingState()
            else -> HomeContent(
                vm = vm,
                isToday = isToday,
                onToday = { vm.goToToday() },
                onPickDate = { showDatePicker = true },
                onPlaces = { vm.loadSavedLocations(); showPlaces = true }
            )
        }

        // Floating circles over the art, Wonderous-style — declared after the
        // content so they stay on top; never inside the scrolling list.
        FloatingHeader(onMenu = onOpenSettings)

        // Notices float above the bottom bar, Wonderous-styled.
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp, start = WonderSpacing.x16, end = WonderSpacing.x16)
        ) { data -> WonderSnackbar(data) }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDate
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            // Times are calculated, so any year works; the Hijri calendar
            // covers about 1882 to 2174.
            yearRange = 1900..2100
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.selectDate(
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showPlaces) {
        ModalBottomSheet(
            onDismissRequest = { showPlaces = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = WonderCorners.panel, topEnd = WonderCorners.panel),
            containerColor = WonderGreyStrong
        ) {
            SavedPlacesSheet(
                vm = vm,
                onSelect = { showPlaces = false },
                onUseCurrent = { showPlaces = false; requestLocation() }
            )
        }
    }

}

/** Menu circle top-left over the art; no bar surface. */
@Composable
private fun FloatingHeader(onMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WonderSpacing.x16, vertical = WonderSpacing.x12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleButton(
            icon = R.drawable.ph_list_light,
            contentDescription = stringResource(R.string.settings),
            onClick = onMenu
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = WonderAccent1)
    }
}

@Composable
private fun HomeErrorState(
    error: HomeError,
    onShareLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = HEADER_SPACE)
            .padding(horizontal = WonderSpacing.x24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (error) {
            is HomeError.LocationRequired -> when (error.cause) {
                LocationCause.PermissionRequestable -> {
                    WonderEmptyState(
                        title = stringResource(R.string.location_needed_title),
                        description = stringResource(R.string.location_needed_desc),
                        prayer = "Fajr"
                    )
                    Spacer(Modifier.height(WonderSpacing.x32))
                    WonderPrimaryButton(text = stringResource(R.string.share_location), onClick = onShareLocation)
                }
                LocationCause.PermissionLocked -> {
                    WonderEmptyState(
                        title = stringResource(R.string.location_off_title),
                        description = stringResource(R.string.location_off_desc),
                        prayer = "Fajr"
                    )
                    Spacer(Modifier.height(WonderSpacing.x32))
                    WonderPrimaryButton(text = stringResource(R.string.open_settings), onClick = onOpenAppSettings)
                }
                LocationCause.ServiceDisabled -> {
                    WonderEmptyState(
                        title = stringResource(R.string.location_services_off_title),
                        description = stringResource(R.string.location_services_off_desc),
                        prayer = "Fajr"
                    )
                    Spacer(Modifier.height(WonderSpacing.x32))
                    WonderPrimaryButton(text = stringResource(R.string.turn_on_location), onClick = onOpenLocationSettings)
                    Spacer(Modifier.height(WonderSpacing.x8))
                    WonderTextButton(text = stringResource(R.string.try_again), onClick = onShareLocation)
                }
                LocationCause.NoFix -> {
                    WonderEmptyState(
                        title = stringResource(R.string.no_fix_title),
                        description = stringResource(R.string.no_fix_desc),
                        prayer = "Fajr"
                    )
                    Spacer(Modifier.height(WonderSpacing.x32))
                    WonderPrimaryButton(text = stringResource(R.string.try_again), onClick = onShareLocation)
                }
            }
            is HomeError.LoadFailed -> {
                WonderEmptyState(
                    title = stringResource(R.string.something_wrong),
                    description = error.message.ifBlank { stringResource(R.string.unknown_error) },
                    prayer = "Maghrib"
                )
                Spacer(Modifier.height(WonderSpacing.x32))
                WonderPrimaryButton(text = stringResource(R.string.try_again), onClick = onRetry)
            }
        }
    }
}

@Composable
private fun HomeContent(
    vm: HomeViewModel,
    isToday: Boolean,
    onToday: () -> Unit,
    onPickDate: () -> Unit,
    onPlaces: () -> Unit
) {
    val state by vm.state.collectAsState()
    val day = state.days.firstOrNull { it.date == state.selectedDate } ?: return
    val adjustments = state.settings.timeAdjustments

    // 1s ticker for the countdown — recomputes from PrayerMath each tick.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(isToday, day) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    val countdown = if (isToday) PrayerMath.countdown(day.timings, adjustments, now) else null
    // Null after Isha: every prayer of the day is past.
    val nextToday = if (isToday) PrayerMath.nextPrayer(day.timings, adjustments, now) else null
    val heroPrayer = countdown?.nextPrayer ?: "Isha"
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ENGLISH
    val isArabic = locale.language == "ar"
    val hijriDate = PrayerCalculator.hijri(state.selectedDate, state.settings.hijriDateAdjustment)
    val hijriMonths = stringArrayResource(R.array.hijri_months)
    val hijri = hijriDate?.let { "${it.day} ${hijriMonths[it.month - 1]} ${it.year}" }.orEmpty()
    val longDate = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale) }

    Column(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = WonderSpacing.x24,
                end = WonderSpacing.x24,
                top = HEADER_SPACE,
                bottom = WonderSpacing.x32
            ),
            verticalArrangement = Arrangement.spacedBy(WonderSpacing.x16)
        ) {
            item {
                Hero(
                    prayer = heroPrayer,
                    title = if (countdown != null) {
                        stringResource(prayerNameRes(countdown.nextPrayer))
                    } else {
                        state.selectedDate.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
                    },
                    locationLine = state.locationName.ifBlank { stringResource(R.string.current_location) },
                    countdown = countdown?.let {
                        formatDuration(
                            it.remaining.toHours(),
                            (it.remaining.toMinutes() % 60).toInt(),
                            (it.remaining.seconds % 60).toInt()
                        )
                    },
                    hijriLine = hijri,
                    gregorianLine = state.selectedDate.format(longDate),
                    onPickDate = onPickDate
                )
            }

            item {
                EyebrowLabel(
                    text = stringResource(if (isToday) R.string.todays_prayers else R.string.prayers),
                    color = WonderAccent2,
                    modifier = Modifier.padding(top = WonderSpacing.x16, bottom = WonderSpacing.x8)
                )
            }

            items(PrayerMath.ORDER, key = { it }) { prayer ->
                val nextIndex = nextToday?.let { PrayerMath.ORDER.indexOf(it) }
                val index = PrayerMath.ORDER.indexOf(prayer)
                val isPast = isToday && (nextIndex == null || index < nextIndex)
                PrayerEventCard(
                    prayer = prayer,
                    subtitle = if (isArabic) prayer else ARABIC_NAMES[prayer].orEmpty(),
                    time = PrayerMath.adjustTime(
                        PrayerMath.timeOf(day.timings, prayer),
                        adjustments.getAdjustmentForPrayer(prayer)
                    ),
                    reminder = state.settings.getSettingsForPrayer(prayer),
                    isNext = prayer == nextToday,
                    isPast = isPast
                )
            }

        }

        // One tap back to today whenever another date is shown.
        androidx.compose.animation.AnimatedVisibility(
            visible = !isToday,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WonderSpacing.x16),
            enter = fadeIn(tween(300)) + slideInVertically(tween(300, easing = EaseOutCubic)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
        ) {
            BackToTodayPill(onClick = onToday)
        }
      }

        // Fixed bottom bar, outside the scrolling list.
        WonderActionBar(
            prayer = heroPrayer,
            isToday = isToday,
            onToday = onToday,
            onPickDate = onPickDate,
            onPlaces = onPlaces
        )
    }
}

/**
 * Editorial hero: arch illustration with the prayer name in Yeseva One
 * sinking into it, the location as an eyebrow between rules, countdown,
 * compass ornament, then the date as the quiet bold line.
 */
@Composable
private fun Hero(
    prayer: String,
    title: String,
    locationLine: String,
    countdown: String?,
    hijriLine: String,
    gregorianLine: String,
    onPickDate: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            PrayerIllustration(
                prayer = prayer,
                modifier = Modifier
                    .padding(bottom = WonderSpacing.x24)
                    .size(width = 232.dp, height = 300.dp)
                    .clip(ArchShape)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 4f), 12f)
                ),
                color = WonderOffWhite,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        Spacer(Modifier.height(WonderSpacing.x16))
        EyebrowLabel(text = locationLine)
        if (countdown != null) {
            Spacer(Modifier.height(WonderSpacing.x8))
            Text(
                text = stringResource(R.string.countdown_in, countdown),
                style = MaterialTheme.typography.displaySmall,
                color = WonderOffWhite,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(WonderSpacing.x16))
        OrnamentDivider()
        Spacer(Modifier.height(WonderSpacing.x8))
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(WonderCorners.card))
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.pick_a_date), onClick = onPickDate)
                .padding(horizontal = WonderSpacing.x16, vertical = WonderSpacing.x8),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = hijriLine,
                style = MaterialTheme.typography.titleMedium,
                color = WonderOffWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = gregorianLine,
                style = MaterialTheme.typography.bodySmall,
                color = WonderAccent2,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Wonderous event card: big Tenor time with the Arabic name under it
 * (like "2575 / BCE"), a thin vertical rule, then name and reminder.
 * The next prayer takes the orange accent; past ones step back.
 */
@Composable
private fun PrayerEventCard(
    prayer: String,
    subtitle: String,
    time: String,
    reminder: PrayerNotificationSettings,
    isNext: Boolean,
    isPast: Boolean
) {
    val ruleColor = if (isNext) WonderAccent1 else WonderOffWhite.copy(alpha = 0.6f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isPast) 0.5f else 1f),
        shape = RoundedCornerShape(WonderCorners.card),
        color = WonderGreyStrong
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(horizontal = WonderSpacing.x24, vertical = WonderSpacing.x24)
        ) {
            Column(Modifier.width(88.dp)) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isNext) WonderAccent1 else WonderOffWhite
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = WonderAccent2
                )
            }
            Box(
                Modifier
                    .padding(horizontal = WonderSpacing.x16)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(ruleColor)
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(prayerNameRes(prayer)),
                        style = MaterialTheme.typography.titleMedium,
                        color = WonderOffWhite,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isNext) {
                        Spacer(Modifier.width(WonderSpacing.x8))
                        Text(
                            text = stringResource(R.string.next).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = WonderAccent1
                        )
                    }
                }
                Text(
                    text = if (reminder.enabled) {
                        pluralStringResource(
                            R.plurals.reminds_before,
                            reminder.prePrayerReminderMinutes,
                            reminder.prePrayerReminderMinutes.toString()
                        )
                    } else {
                        stringResource(R.string.silent)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = WonderAccent2
                )
            }
        }
    }
}

/**
 * Wonderous tab bar: a circular portrait of the current prayer's scene on
 * the left (tap = back to today), then line icons; the active one turns
 * orange. Top corners use the large 32dp radius.
 */
@Composable
private fun WonderActionBar(
    prayer: String,
    isToday: Boolean,
    onToday: () -> Unit,
    onPickDate: () -> Unit,
    onPlaces: () -> Unit
) {
    val todayLabel = stringResource(R.string.today)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = WonderCorners.panel, topEnd = WonderCorners.panel),
        color = WonderGreyStrong
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = WonderSpacing.x16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(3.dp, if (isToday) WonderOffWhite else WonderGreyMedium, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.go_to_today), onClick = onToday)
                    .semantics { contentDescription = todayLabel }
            ) {
                PrayerIllustration(
                    prayer = prayer,
                    fadeTo = null,
                    modifier = Modifier.fillMaxSize().padding(3.dp).clip(CircleShape)
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BarIcon(R.drawable.ph_calendar_blank_light, stringResource(R.string.pick_a_date), active = !isToday, onClick = onPickDate)
                BarIcon(R.drawable.ph_map_pin_light, stringResource(R.string.places), active = false, onClick = onPlaces)
            }
        }
    }
}

@Composable
private fun BarIcon(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(28.dp),
            tint = if (active) WonderAccent1 else WonderOffWhite
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedPlacesSheet(
    vm: HomeViewModel,
    onSelect: () -> Unit,
    onUseCurrent: () -> Unit
) {
    val state by vm.state.collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = WonderSpacing.x24)
    ) {
        Text(
            text = stringResource(R.string.saved_places),
            style = MaterialTheme.typography.headlineMedium,
            color = WonderOffWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = WonderSpacing.x8)
        )
        OrnamentDivider(Modifier.padding(horizontal = WonderSpacing.x48, vertical = WonderSpacing.x8))
        if (state.savedLocations.isEmpty()) {
            Text(
                text = stringResource(R.string.no_saved_places),
                style = MaterialTheme.typography.bodyMedium,
                color = WonderAccent2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = WonderSpacing.x16)
            )
        } else {
            state.savedLocations.forEachIndexed { index, place ->
                MetaRow(
                    label = stringResource(R.string.place_n, (index + 1).toString()),
                    value = place.name.ifBlank { stringResource(R.string.current_location) },
                    onClick = { vm.selectSavedLocation(place); onSelect() }
                )
                if (index < state.savedLocations.lastIndex) WonderDivider()
            }
        }
        Spacer(Modifier.height(WonderSpacing.x16))
        WonderPrimaryButton(
            text = stringResource(R.string.use_current_location),
            onClick = onUseCurrent,
            containerColor = WonderBlack,
            modifier = Modifier.padding(horizontal = WonderSpacing.x24)
        )
    }
}

/** Accent pill: the one place orange fills a control, because it's the way home. */
@Composable
private fun BackToTodayPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = WonderAccent1,
        modifier = Modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = WonderSpacing.x24),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ph_calendar_check_light),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = WonderWhite
            )
            Spacer(Modifier.width(WonderSpacing.x8))
            Text(
                text = stringResource(R.string.back_to_today).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = WonderWhite
            )
        }
    }
}

@Composable
private fun WonderSnackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        shape = RoundedCornerShape(WonderCorners.card),
        containerColor = WonderGreyStrong,
        contentColor = WonderOffWhite,
        actionColor = WonderAccent1
    )
}

@Composable
private fun noticeMessage(notice: HomeNotice): String = when (notice) {
    is HomeNotice.LocationUpdated -> stringResource(
        R.string.notice_location_updated,
        notice.name.ifBlank { stringResource(R.string.current_location) }
    )
    is HomeNotice.LocationFailed -> stringResource(
        when (notice.cause) {
            LocationCause.ServiceDisabled -> R.string.notice_location_off
            LocationCause.PermissionLocked -> R.string.notice_permission_locked
            else -> R.string.notice_no_fix
        }
    )
    HomeNotice.LoadFailed -> stringResource(R.string.notice_load_failed)
}

/** True exactly once per install, on Android 13+, while notifications are off. */
private fun shouldAskNotificationsOnce(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 33) return false
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    if (granted) return false
    val prefs = context.getSharedPreferences(PERM_PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(NOTIF_ASKED_ON_START, false)) return false
    prefs.edit().putBoolean(NOTIF_ASKED_ON_START, true).apply()
    return true
}

private const val NOTIF_ASKED_ON_START = "notifications_asked_on_start"

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED ||
        coarse == PackageManager.PERMISSION_GRANTED
}

private fun locationRationale(context: Context): Boolean {
    val activity = context as? Activity ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity, Manifest.permission.ACCESS_FINE_LOCATION
    ) || ActivityCompat.shouldShowRequestPermissionRationale(
        activity, Manifest.permission.ACCESS_COARSE_LOCATION
    )
}

/** Set only after the user actually answered the dialog, never before. */
private fun answeredLocationBefore(context: Context): Boolean =
    context.getSharedPreferences(PERM_PREFS, Context.MODE_PRIVATE).getBoolean(LOCATION_ANSWERED, false)

private fun markLocationAnswered(context: Context) {
    context.getSharedPreferences(PERM_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(LOCATION_ANSWERED, true).apply()
}

private fun isLocationOn(context: Context): Boolean =
    context.getSystemService(LocationManager::class.java)
        ?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

private const val PERM_PREFS = "home_location_perm"
private const val LOCATION_ANSWERED = "location_answered"

private fun openAppSettings(context: Context) {
    val intent = Intent(
        SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openLocationSettings(context: Context) {
    val intent = Intent(SystemSettings.ACTION_LOCATION_SOURCE_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun formatDuration(hours: Long, minutes: Int, seconds: Int): String {
    val totalMinutes = hours * 60 + minutes
    val h = totalMinutes / 60
    val m = (totalMinutes % 60).toString().padStart(2, '0')
    val s = seconds.toString().padStart(2, '0')
    return "$h:$m:$s"
}
