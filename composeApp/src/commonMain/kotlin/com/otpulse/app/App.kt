package com.otpulse.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.otpulse.core.time.PlatformClock
import com.otpulse.core.time.Clock
import com.otpulse.core.lifecycle.AppLifecycleController
import com.otpulse.bluetooth.HidTestController
import com.otpulse.bluetooth.HidTestState
import com.otpulse.bluetooth.HidSendOutcome
import com.otpulse.bluetooth.MacSetupKey
import com.otpulse.bluetooth.UnsupportedHidTestController
import com.otpulse.bluetooth.canSendOverHid
import com.otpulse.bluetooth.toBluetoothUxPhase
import com.otpulse.backup.BackupExportResult
import com.otpulse.backup.BackupFileGateway
import com.otpulse.backup.BackupFileReadResult
import com.otpulse.backup.BackupFileWriteResult
import com.otpulse.backup.BackupPreview
import com.otpulse.backup.BackupPreviewResult
import com.otpulse.backup.BackupRestoreResult
import com.otpulse.backup.BackupService
import com.otpulse.backup.UnsupportedBackupFileGateway
import com.otpulse.design.OtpulseColors
import com.otpulse.design.OtpulseIcon
import com.otpulse.design.OtpulseIconSizes
import com.otpulse.design.OtpulseIcons
import com.otpulse.design.OtpulseSpacing
import com.otpulse.design.OtpulseTheme
import com.otpulse.design.OtpulseTypography
import com.otpulse.presentation.AccountCodeView
import com.otpulse.presentation.AccountInputResult
import com.otpulse.presentation.AccountMutationResult
import com.otpulse.presentation.AddAccountResult
import com.otpulse.presentation.AuthenticatorStore
import com.otpulse.presentation.CodeListPresenter
import com.otpulse.presentation.CountdownArcTransitionTracker
import com.otpulse.presentation.CodeTransitionPlan
import com.otpulse.presentation.CodeTransitionTracker
import com.otpulse.persistence.StorageResult
import com.otpulse.persistence.AccountRepository
import com.otpulse.persistence.InMemoryAccountMetadataStore
import com.otpulse.persistence.InMemorySecretStore
import com.otpulse.qr.QrCodeScanner
import com.otpulse.qr.QrScanResult
import com.otpulse.qr.UnsupportedQrCodeScanner
import com.otpulse.timeshift.InMemoryTimeShiftSettingsStore
import com.otpulse.timeshift.TimeShiftSettingResult
import com.otpulse.timeshift.TimeShiftSettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.resources.stringResource
import otpulse.composeapp.generated.resources.Res
import otpulse.composeapp.generated.resources.*
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private sealed interface Screen {
    data object Codes : Screen
    data class Add(val initialSecret: String = "") : Screen
    data class Edit(val accountId: String) : Screen
    data object Settings : Screen
    data object TimeShiftSettings : Screen
}

private data class SendAnimationSnapshot(
    val id: Long,
    val accountId: String,
    val codeBounds: Rect,
    val hostBounds: Rect,
)

private val CodeScaleEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private const val CountdownArcTransitionDurationMillis = 700

@Composable
fun OTPulseApp(
    accountRepository: AccountRepository = AccountRepository(InMemoryAccountMetadataStore(), InMemorySecretStore()),
    hidTestController: HidTestController = UnsupportedHidTestController,
    qrCodeScanner: QrCodeScanner = UnsupportedQrCodeScanner,
    clock: Clock = PlatformClock,
    lifecycleController: AppLifecycleController? = null,
    timeShiftSettingsStore: TimeShiftSettingsStore? = null,
    languageSettingsStore: LanguageSettingsStore? = null,
    themeSettingsStore: ThemeSettingsStore? = null,
    backupFileGateway: BackupFileGateway = UnsupportedBackupFileGateway,
    onRequestBluetoothPermissions: () -> Unit = {},
    onRequestEnableBluetooth: () -> Unit = {},
    onRequestDiscoverable: () -> Unit = {},
    onApplyLanguage: (String?) -> Unit = {},
    onApplyTheme: (AppTheme, Boolean) -> Unit = { _, _ -> },
) {
    val activeLanguageSettingsStore = remember(languageSettingsStore) {
        languageSettingsStore ?: InMemoryLanguageSettingsStore()
    }
    var selectedLanguageTag by remember(activeLanguageSettingsStore) {
        mutableStateOf(activeLanguageSettingsStore.selectedLanguageTag())
    }
    val selectedLanguage = AppLanguage.fromTag(selectedLanguageTag)
    val activeThemeSettingsStore = remember(themeSettingsStore) {
        themeSettingsStore ?: InMemoryThemeSettingsStore()
    }
    var selectedTheme by remember(activeThemeSettingsStore) {
        mutableStateOf(activeThemeSettingsStore.selectedTheme())
    }
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (selectedTheme) {
        AppTheme.SYSTEM -> systemDarkTheme
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    LaunchedEffect(selectedTheme, darkTheme) { onApplyTheme(selectedTheme, darkTheme) }
    OtpulseTheme(darkTheme = darkTheme) {
    var screen by remember { mutableStateOf<Screen>(Screen.Codes) }
    val store = remember(accountRepository) { AuthenticatorStore(accountRepository) }
    val backupService = remember(accountRepository) { BackupService(accountRepository) }
    val activeLifecycleController = remember(lifecycleController) {
        lifecycleController ?: AppLifecycleController().also { it.onForeground() }
    }
    val lifecycleState by activeLifecycleController.state.collectAsState()
    val activeTimeShiftSettingsStore = remember(timeShiftSettingsStore) {
        timeShiftSettingsStore ?: InMemoryTimeShiftSettingsStore()
    }
    var expectedManualInputMilliseconds by remember(activeTimeShiftSettingsStore) {
        mutableLongStateOf(activeTimeShiftSettingsStore.expectedManualInputMilliseconds())
    }
    var accountRevision by remember { mutableIntStateOf(0) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var showExportWarning by remember { mutableStateOf(false) }
    var backupPreview by remember { mutableStateOf<BackupPreview?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val backupSavedFormat = stringResource(Res.string.backup_saved)
    val backupImportedFormat = stringResource(Res.string.backup_imported)
    val backupUntitled = stringResource(Res.string.backup_untitled)
    PlatformBackHandler(enabled = screen != Screen.Codes) {
        screen = when (screen) {
            Screen.TimeShiftSettings -> Screen.Settings
            else -> Screen.Codes
        }
    }
    if (scanError != null) {
        AlertDialog(
            onDismissRequest = { scanError = null },
            title = { Text(stringResource(Res.string.dialog_add_failed)) },
            text = { Text(localizedFailure(scanError.orEmpty())) },
            confirmButton = { Button(onClick = { scanError = null }) { Text(stringResource(Res.string.action_ok)) } },
        )
    }
    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = { Text(stringResource(Res.string.dialog_export_secrets)) },
            text = { Text(stringResource(Res.string.dialog_export_warning)) },
            confirmButton = {
                Button(onClick = {
                    showExportWarning = false
                    when (val exported = backupService.exportText()) {
                        is BackupExportResult.Failure -> backupMessage = exported.message
                        is BackupExportResult.Success -> backupFileGateway.createBackup(exported.text) { result ->
                            backupMessage = when (result) {
                                BackupFileWriteResult.Success -> backupSavedFormat.formatResourceNumbers(exported.accountCount)
                                BackupFileWriteResult.Cancelled -> null
                                is BackupFileWriteResult.Failure -> result.message
                            }
                        }
                    }
                }) { Text(stringResource(Res.string.action_choose_file)) }
            },
            dismissButton = { OutlinedButton(onClick = { showExportWarning = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    backupPreview?.let { preview ->
        val sample = preview.entries.take(5).joinToString("\n") { entry ->
            "• ${entry.account.issuer.ifBlank { backupUntitled }} — ${entry.account.accountName}"
        }
        AlertDialog(
            onDismissRequest = { backupPreview = null },
            title = { Text(stringResource(Res.string.dialog_import_preview)) },
            text = {
                Text(
                    stringResource(Res.string.backup_preview_counts, preview.importCount, preview.newCount, preview.duplicateCount) +
                        (if (sample.isEmpty()) "" else "\n\n$sample") +
                        (if (preview.entries.size > 5) "\n…" else "")
                )
            },
            confirmButton = {
                Button(
                    enabled = preview.newCount > 0,
                    onClick = {
                        when (val restored = backupService.restore(preview)) {
                            is BackupRestoreResult.Success -> {
                                if (restored.importedCount > 0) accountRevision++
                                backupMessage = backupImportedFormat.formatResourceNumbers(restored.importedCount, restored.skippedDuplicateCount)
                            }
                            is BackupRestoreResult.Failure -> backupMessage = restored.message
                        }
                        backupPreview = null
                    },
                ) { Text(stringResource(Res.string.action_import)) }
            },
            dismissButton = { OutlinedButton(onClick = { backupPreview = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text(stringResource(Res.string.dialog_backup)) },
            text = { Text(localizedFailure(message)) },
            confirmButton = { Button(onClick = { backupMessage = null }) { Text(stringResource(Res.string.action_ok)) } },
        )
    }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = OtpulseColors.Text,
        modifier = Modifier.background(
            Brush.radialGradient(
                colors = if (darkTheme) {
                    listOf(Color(0xFF14213A), MaterialTheme.colorScheme.background)
                } else {
                    listOf(Color(0xFFE9EEFF), MaterialTheme.colorScheme.background)
                },
                center = Offset(180f, 100f),
                radius = 900f,
            )
        ),
    ) { padding ->
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(padding),
        ) { current ->
            when (current) {
                Screen.Codes -> CodesScreen(
                    store,
                    accountRevision,
                    clock = clock,
                    refreshRevision = lifecycleState.refreshRevision,
                    expectedManualInputMilliseconds = expectedManualInputMilliseconds,
                    hidController = hidTestController,
                    onRequestBluetoothPermissions = onRequestBluetoothPermissions,
                    onRequestEnableBluetooth = onRequestEnableBluetooth,
                    onRequestDiscoverable = onRequestDiscoverable,
                    onAdd = {
                        scanError = null
                        qrCodeScanner.scan { result ->
                            when (result) {
                                is QrScanResult.Success -> when (val input = store.consumeAccountInput(result.value)) {
                                    AccountInputResult.Imported -> accountRevision++
                                    is AccountInputResult.NeedsDetails -> screen = Screen.Add(input.normalizedSecret)
                                    is AccountInputResult.Failure -> scanError = input.message
                                }
                                QrScanResult.ManualRequested -> screen = Screen.Add()
                                QrScanResult.Cancelled -> Unit
                                is QrScanResult.Failure -> scanError = result.message
                            }
                        }
                    },
                    onOpenSettings = { screen = Screen.Settings },
                    onEdit = { screen = Screen.Edit(it) },
                )
                is Screen.Add -> AddScreen(
                    initialSecret = current.initialSecret,
                    onBack = { screen = Screen.Codes },
                    onAddManual = { issuer, account, secret, algorithm, digits, period ->
                        store.addManual(issuer, account, secret, algorithm, digits, period).also {
                            if (it is AddAccountResult.Success) accountRevision++
                        }
                    },
                )
                is Screen.Edit -> EditScreen(
                    store = store,
                    accountId = current.accountId,
                    onBack = { screen = Screen.Codes },
                    onChanged = {
                        accountRevision++
                        screen = Screen.Codes
                    },
                )
                Screen.Settings -> SettingsScreen(
                    expectedManualInputMilliseconds = expectedManualInputMilliseconds,
                    selectedLanguage = selectedLanguage,
                    selectedTheme = selectedTheme,
                    onSelectTheme = { theme ->
                        activeThemeSettingsStore.setSelectedTheme(theme)
                        selectedTheme = theme
                    },
                    onSelectLanguage = { language ->
                        val tag = language?.tag
                        activeLanguageSettingsStore.setSelectedLanguageTag(tag)
                        selectedLanguageTag = tag
                        onApplyLanguage(tag)
                    },
                    onBack = { screen = Screen.Codes },
                    onOpenTimeShift = { screen = Screen.TimeShiftSettings },
                    onExportBackup = { showExportWarning = true },
                    onImportBackup = {
                        backupFileGateway.openBackup { result ->
                            when (result) {
                                BackupFileReadResult.Cancelled -> Unit
                                is BackupFileReadResult.Failure -> backupMessage = result.message
                                is BackupFileReadResult.Success -> when (val parsed = backupService.preview(result.text)) {
                                    is BackupPreviewResult.Success -> backupPreview = parsed.preview
                                    is BackupPreviewResult.Failure -> backupMessage = parsed.message
                                }
                            }
                        }
                    },
                )
                Screen.TimeShiftSettings -> TimeShiftSettingsScreen(
                    initialMilliseconds = expectedManualInputMilliseconds,
                    onBack = { screen = Screen.Settings },
                    onSave = { value ->
                        activeTimeShiftSettingsStore.setExpectedManualInputMilliseconds(value).also { result ->
                            if (result is TimeShiftSettingResult.Success) {
                                expectedManualInputMilliseconds = value
                                screen = Screen.Settings
                            }
                        }
                    },
                )
            }
        }
    }
}
}

@Composable
private fun CodesScreen(
    store: AuthenticatorStore,
    accountRevision: Int,
    clock: Clock,
    refreshRevision: Long,
    expectedManualInputMilliseconds: Long,
    hidController: HidTestController,
    onRequestBluetoothPermissions: () -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    onRequestDiscoverable: () -> Unit,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val hidState by hidController.state.collectAsState()
    val bluetoothUxPhase = hidState.toBluetoothUxPhase()
    val clipboardManager = LocalClipboardManager.current
    val codeBoundsByAccount = remember { mutableStateMapOf<String, Rect>() }
    var hostBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var listViewportBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var codesOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var animationSequence by remember { mutableLongStateOf(0L) }
    var activeSendAnimation by remember { mutableStateOf<SendAnimationSnapshot?>(null) }
    var hostSelectorExpanded by remember { mutableStateOf(false) }
    var showBluetoothDiagnostics by remember { mutableStateOf(false) }
    val settingsDescription = stringResource(Res.string.cd_settings)
    val addCodeDescription = stringResource(Res.string.cd_add_code)
    val listState = rememberLazyListState()
    var addButtonVisible by remember { mutableStateOf(true) }
    LaunchedEffect(hidController) { hidController.initialize() }
    val presenter = remember(store, clock, expectedManualInputMilliseconds) {
        CodeListPresenter(store, clock, expectedManualInputMilliseconds)
    }
    var accounts by remember(presenter, accountRevision, refreshRevision) {
        mutableStateOf(presenter.refresh())
    }
    LaunchedEffect(presenter, accountRevision, refreshRevision) {
        while (isActive) {
            presenter.refreshIfPresentationChanged()?.let { accounts = it }
            delay(presenter.millisecondsUntilNextCheck())
        }
    }
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingDown = index > previousIndex || index == previousIndex && offset > previousOffset
                val scrollingUp = index < previousIndex || index == previousIndex && offset < previousOffset
                when {
                    !listState.canScrollBackward -> addButtonVisible = true
                    scrollingDown -> addButtonVisible = false
                    scrollingUp -> addButtonVisible = true
                }
                previousIndex = index
                previousOffset = offset
            }
    }

    hidState.recoveryError?.let { error ->
        AlertDialog(
            onDismissRequest = hidController::dismissRecovery,
            title = { Text(stringResource(Res.string.dialog_connection_lost)) },
            text = { Text(stringResource(Res.string.hid_connection_recovery)) },
            confirmButton = { Button(onClick = hidController::retryConnection) { Text(stringResource(Res.string.action_retry)) } },
            dismissButton = {
                OutlinedButton(onClick = {
                    hidController.selectDifferentHost()
                    hostSelectorExpanded = true
                }) { Text(stringResource(Res.string.action_choose_another)) }
            },
        )
    }

    if (showBluetoothDiagnostics) {
        BluetoothDiagnosticsDialog(
            controller = hidController,
            onDismiss = { showBluetoothDiagnostics = false },
            onRequestPermissions = onRequestBluetoothPermissions,
            onRequestEnableBluetooth = onRequestEnableBluetooth,
            onRequestDiscoverable = onRequestDiscoverable,
        )
    }

    Box(
        Modifier.fillMaxSize().onGloballyPositioned { codesOriginInRoot = it.positionInRoot() }
    ) {
    Column(Modifier.fillMaxSize().padding(horizontal = OtpulseSpacing.md)) {
        AppHeader(
            title = stringResource(Res.string.title_codes),
            horizontalPadding = 0.dp,
            trailing = {
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                    OtpulseIcon(
                        imageVector = OtpulseIcons.Settings,
                        contentDescription = settingsDescription,
                        modifier = Modifier.size(OtpulseIconSizes.Standard),
                        tint = OtpulseColors.TextMuted,
                    )
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        HostSelector(
            state = hidState,
            expanded = hostSelectorExpanded,
            onExpandedChange = { hostSelectorExpanded = it },
            onOpenDiagnostics = { showBluetoothDiagnostics = true },
            onRequestPermissions = onRequestBluetoothPermissions,
            onSelectHost = hidController::connect,
            onBoundsChanged = { hostBoundsInRoot = it },
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.onGloballyPositioned { listViewportBoundsInRoot = it.unclippedBoundsInRoot() },
        ) {
            if (accounts.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.empty_codes),
                        color = OtpulseColors.TextMuted,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            items(accounts, key = { it.id }, contentType = { "account" }) { account ->
                val selectedHostConnected = bluetoothUxPhase.canSendOverHid
                AccountCard(
                    account = account,
                    clock = clock,
                    refreshRevision = refreshRevision,
                    onEdit = onEdit,
                    primaryActionEnabled = !hidState.sending,
                    sendsOverBluetooth = selectedHostConnected,
                    viewportBoundsInRoot = listViewportBoundsInRoot,
                    onCodeBoundsChanged = { codeBoundsByAccount[account.id] = it },
                    onPrimaryAction = {
                        if (selectedHostConnected) {
                            val sourceBounds = codeBoundsByAccount[account.id]
                            val targetBounds = hostBoundsInRoot
                            if (sourceBounds != null && targetBounds != null) {
                                animationSequence++
                                activeSendAnimation = SendAnimationSnapshot(
                                    id = animationSequence,
                                    accountId = account.id,
                                    codeBounds = sourceBounds.relativeTo(codesOriginInRoot),
                                    hostBounds = targetBounds.relativeTo(codesOriginInRoot),
                                )
                            }
                            hidController.sendOtp(account.id, account.periodMilliseconds) { now ->
                                store.bluetoothSnapshot(account.id, now)
                            }
                        } else {
                            clipboardManager.setText(AnnotatedString(account.currentCode))
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
        AnimatedVisibility(
            visible = addButtonVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = OtpulseColors.Accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                OtpulseIcon(
                    imageVector = OtpulseIcons.Add,
                    contentDescription = addCodeDescription,
                    modifier = Modifier.size(OtpulseIconSizes.Standard),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        activeSendAnimation?.let { animation ->
            val animatedCode = accounts.firstOrNull { it.id == animation.accountId }?.currentCode
            if (animatedCode != null) {
                SendTransferOverlay(
                    animation = animation,
                    code = animatedCode,
                    hidState = hidState,
                    onFinished = {
                        if (activeSendAnimation?.id == animation.id) {
                            activeSendAnimation = null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HostSelector(
    state: HidTestState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onRequestPermissions: () -> Unit,
    onSelectHost: (String) -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    val selectedHost = state.hosts.firstOrNull { it.address == state.selectedAddress }
    val connected = selectedHost?.connected == true
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val selectorWidth = maxWidth
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(OtpulseColors.SurfaceRaised)
                .clickable { onExpandedChange(true) }
                .onGloballyPositioned { onBoundsChanged(it.unclippedBoundsInRoot()) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OtpulseIcon(
                imageVector = OtpulseIcons.Computer,
                contentDescription = null,
                modifier = Modifier.size(OtpulseIconSizes.Standard),
                tint = OtpulseColors.Accent,
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(Res.string.bluetooth_hid_label), color = OtpulseColors.TextMuted, fontSize = 10.sp)
                Text(
                    selectedHost?.name ?: if (state.hosts.isEmpty()) stringResource(Res.string.host_not_selected) else stringResource(Res.string.host_choose),
                    fontSize = 16.sp,
                )
                if (selectedHost != null) {
                    Text(
                        if (connected) stringResource(Res.string.status_connected) else if (state.connecting) stringResource(Res.string.status_connecting) else stringResource(Res.string.status_disconnected),
                        color = if (connected) OtpulseColors.Success else OtpulseColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }
            OtpulseIcon(
                imageVector = OtpulseIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(OtpulseIconSizes.Standard),
                tint = OtpulseColors.TextMuted,
            )
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(if (connected) OtpulseColors.Accent.copy(alpha = .18f) else OtpulseColors.Border),
                contentAlignment = Alignment.Center,
            ) {
                OtpulseIcon(
                    imageVector = OtpulseIcons.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(OtpulseIconSizes.Standard),
                    tint = if (connected) OtpulseColors.Accent else OtpulseColors.TextMuted,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width(selectorWidth),
        ) {
            if (state.hosts.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(if (state.permissionGranted) Res.string.paired_hosts_empty else Res.string.action_allow_nearby),
                            color = if (state.permissionGranted) OtpulseColors.TextMuted else OtpulseColors.Accent,
                        )
                    },
                    enabled = !state.permissionGranted,
                    onClick = {
                        onExpandedChange(false)
                        onRequestPermissions()
                    },
                )
            }
            state.hosts.forEach { host ->
                val selected = host.address == state.selectedAddress
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(host.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            Text(
                                if (host.connected) stringResource(Res.string.status_connected) else if (selected && state.connecting) stringResource(Res.string.status_connecting) else stringResource(Res.string.status_disconnected),
                                color = if (host.connected) OtpulseColors.Success else OtpulseColors.TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                    },
                    trailingIcon = {
                        HostConnectionIcon(host.connected)
                    },
                    enabled = !state.sending && !state.connecting,
                    onClick = {
                        onExpandedChange(false)
                        onSelectHost(host.address)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.help_device_not_found), color = OtpulseColors.Accent) },
                onClick = {
                    onExpandedChange(false)
                    onOpenDiagnostics()
                },
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountCodeView,
    clock: Clock,
    refreshRevision: Long,
    onEdit: (String) -> Unit,
    primaryActionEnabled: Boolean,
    sendsOverBluetooth: Boolean,
    viewportBoundsInRoot: Rect?,
    onCodeBoundsChanged: (Rect) -> Unit,
    onPrimaryAction: () -> Unit,
) {
    val accent = OtpulseColors.Accent
    val displayName = account.issuer.ifBlank { account.accountName }
    val secondaryName = account.accountName.takeIf { account.issuer.isNotBlank() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val actionScope = rememberCoroutineScope()
    var preparingPrimaryAction by remember { mutableStateOf(false) }
    var cardBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    val actionEnabled = primaryActionEnabled && !preparingPrimaryAction
    val clickLabel = stringResource(if (sendsOverBluetooth) Res.string.a11y_send_current_code else Res.string.a11y_copy_current_code)
    val longClickLabel = stringResource(Res.string.a11y_open_account_settings)
    val cardDescription = if (actionEnabled) {
        stringResource(if (sendsOverBluetooth) Res.string.a11y_account_send else Res.string.a11y_account_copy, displayName, secondaryName.orEmpty())
    } else {
        stringResource(Res.string.a11y_account_unavailable, displayName, secondaryName.orEmpty())
    }
    Column(
        Modifier.fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onGloballyPositioned { cardBoundsInRoot = it.unclippedBoundsInRoot() }
            .clip(RoundedCornerShape(16.dp))
            .background(OtpulseColors.Surface.copy(alpha = .94f))
            .border(1.dp, OtpulseColors.Border.copy(alpha = .5f), RoundedCornerShape(16.dp))
            .combinedClickable(
                enabled = actionEnabled,
                onClickLabel = clickLabel,
                onLongClickLabel = longClickLabel,
                onLongClick = { onEdit(account.id) },
                onClick = {
                    val cardBounds = cardBoundsInRoot
                    val viewportBounds = viewportBoundsInRoot
                    val fullyVisible = cardBounds != null && viewportBounds != null &&
                        cardBounds.left >= viewportBounds.left &&
                        cardBounds.top >= viewportBounds.top &&
                        cardBounds.right <= viewportBounds.right &&
                        cardBounds.bottom <= viewportBounds.bottom
                    if (!sendsOverBluetooth || fullyVisible) {
                        onPrimaryAction()
                    } else {
                        preparingPrimaryAction = true
                        actionScope.launch {
                            try {
                                bringIntoViewRequester.bringIntoView()
                                // Bounds callbacks run during scrolling. Wait for the settled layout
                                // before capturing source coordinates and starting the HID transfer.
                                withFrameNanos { }
                                onPrimaryAction()
                            } finally {
                                preparingPrimaryAction = false
                            }
                        }
                    }
                },
            )
            .semantics {
                contentDescription = cardDescription
            }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = .12f))
                    .border(1.dp, accent.copy(alpha = .28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(displayName.take(2).uppercase(), color = accent, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(displayName, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                secondaryName?.let {
                    Text(it, color = OtpulseColors.TextMuted, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountCodeTransition(
                account = account,
                refreshRevision = refreshRevision,
                accent = accent,
                onCurrentCodeBoundsChanged = onCodeBoundsChanged,
                modifier = Modifier.weight(1f),
            )
            CountdownRing(
                periodMilliseconds = account.periodMilliseconds,
                color = accent,
                clock = clock,
                refreshRevision = refreshRevision,
            )
        }
    }
}

@Composable
private fun AccountCodeTransition(
    account: AccountCodeView,
    refreshRevision: Long,
    accent: Color,
    onCurrentCodeBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentCodeDescription = stringResource(
        Res.string.a11y_current_code,
        account.currentCode.toList().joinToString(" "),
    )
    val nextCodeDescription = stringResource(
        Res.string.a11y_next_code,
        account.nextCode.toList().joinToString(" "),
    )
    val nextLabel = stringResource(Res.string.label_next_code)
    val nextColor = OtpulseColors.Text
    val mutedColor = OtpulseColors.TextMuted
    val tracker = remember(account.id, refreshRevision) {
        CodeTransitionTracker(account.counter, account.currentCode, account.nextCode)
    }
    val plan: CodeTransitionPlan? = remember(
        tracker,
        account.counter,
        account.currentCode,
        account.nextCode,
    ) {
        tracker.observe(account.counter, account.currentCode, account.nextCode)
    }
    val progress = remember(account.counter, plan) {
        Animatable(if (plan == null) 1f else 0f)
    }
    LaunchedEffect(progress, plan) {
        if (plan != null) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = LinearEasing),
            )
        }
    }

    val transitionFraction = CodeScaleEasing.transform(progress.value)
    val outgoingCode = plan?.outgoingCurrentCode ?: account.currentCode
    val promotedCode = plan?.promotedNextCode.orEmpty()
    val incomingNextCode = plan?.incomingNextCode ?: account.nextCode

    Layout(
        modifier = modifier,
        content = {
            Text(
                outgoingCode.chunked(3).joinToString(" "),
                color = accent,
                style = OtpulseTypography.Code,
                modifier = Modifier
                    .onGloballyPositioned { onCurrentCodeBoundsChanged(it.unclippedBoundsInRoot()) }
                    .graphicsLayer { alpha = if (plan == null) 1f else 1f - transitionFraction }
                    .semantics { contentDescription = currentCodeDescription },
            )
            Text(nextLabel, color = mutedColor, fontSize = 13.sp)
            Text(
                promotedCode.chunked(3).joinToString(" "),
                color = androidx.compose.ui.graphics.lerp(nextColor, accent, transitionFraction),
                style = OtpulseTypography.Code,
                modifier = Modifier.graphicsLayer {
                    alpha = if (plan == null) 0f else 1f
                    val promotedScale = 0.5f + 0.5f * transitionFraction
                    scaleX = promotedScale
                    scaleY = promotedScale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            )
            Text(
                incomingNextCode.chunked(3).joinToString(" "),
                color = nextColor,
                fontSize = 18.sp,
                modifier = Modifier
                    .graphicsLayer { alpha = if (plan == null) 1f else transitionFraction }
                    .semantics { contentDescription = nextCodeDescription },
            )
        },
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val outgoing = measurables[0].measure(looseConstraints)
        val label = measurables[1].measure(looseConstraints)
        val promoted = measurables[2].measure(looseConstraints)
        val incoming = measurables[3].measure(looseConstraints)
        val nextGap = 24.dp.roundToPx()
        val nextTop = 47.dp.roundToPx()
        val nextStart = label.width + nextGap
        val contentWidth = maxOf(outgoing.width, nextStart + incoming.width, nextStart + promoted.width)
        val width = contentWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = maxOf(42.dp.roundToPx(), nextTop + maxOf(label.height, incoming.height))
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val promotedX = (nextStart * (1f - transitionFraction)).roundToInt()
        val promotedY = (nextTop * (1f - transitionFraction)).roundToInt()
        layout(width, height) {
            outgoing.placeRelative(0, 0)
            label.placeRelative(0, nextTop + (incoming.height - label.height) / 2)
            incoming.placeRelative(nextStart, nextTop)
            promoted.placeRelative(promotedX, promotedY)
        }
    }
}

private data class LaptopGeometry(
    val body: Rect,
    val screen: Rect,
    val targets: List<Offset>,
)

private data class DigitFlightVisual(
    val index: Int,
    val progress: Float,
    val source: Offset,
    val control: Offset,
    val target: Offset,
    val movingPoint: Offset,
)

@Composable
private fun SendTransferOverlay(
    animation: SendAnimationSnapshot,
    code: String,
    hidState: HidTestState,
    onFinished: () -> Unit,
) {
    val density = LocalDensity.current
    val successColor = OtpulseColors.Success
    val latestState = rememberUpdatedState(hidState)
    val latestCode = rememberUpdatedState(code)
    val flightProgressByDigit = remember(animation.id) { mutableStateMapOf<Int, Float>() }
    val completedDigits = remember(animation.id) { mutableStateMapOf<Int, Boolean>() }
    val successProgress = remember(animation.id) { Animatable(0f) }
    val sceneAlpha = remember(animation.id) { Animatable(0f) }
    val geometry = remember(animation.hostBounds, code.length, density) {
        laptopGeometry(animation.hostBounds, code.length, with(density) { 1.dp.toPx() })
    }

    LaunchedEffect(animation.id) {
        sceneAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        )

        suspend fun closeOverlay(durationMillis: Int) {
            sceneAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            )
            onFinished()
        }

        var startWait = 0L
        while (!latestState.value.sending && latestState.value.lastSendOutcome != HidSendOutcome.SUCCESS && startWait < 800L) {
            delay(16L)
            startWait += 16L
        }
        if (!latestState.value.sending && latestState.value.lastSendOutcome != HidSendOutcome.SUCCESS) {
            closeOverlay(180)
            return@LaunchedEffect
        }

        var launchedDigits = 0
        coroutineScope {
            val flightJobs = mutableListOf<kotlinx.coroutines.Job>()
            while (launchedDigits < latestCode.value.length) {
                val transportState = latestState.value
                val availableDigits = transportState.sentKeys.coerceIn(0, latestCode.value.length)
                while (launchedDigits < availableDigits) {
                    val digitIndex = launchedDigits++
                    flightProgressByDigit[digitIndex] = 0f
                    flightJobs += launch {
                        val progress = Animatable(0f)
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
                        ) {
                            flightProgressByDigit[digitIndex] = value
                        }
                        completedDigits[digitIndex] = true
                        flightProgressByDigit.remove(digitIndex)
                    }
                    if (launchedDigits < latestCode.value.length) {
                        delay(MIN_DIGIT_FLIGHT_STAGGER_MILLISECONDS)
                    }
                }
                if (!transportState.sending && !transportState.waitingForFreshCode) break
                delay(8L)
            }
            flightJobs.joinAll()
        }

        while (latestState.value.sending) delay(8L)
        if (
            latestState.value.lastSendOutcome == HidSendOutcome.SUCCESS &&
            completedDigits.size == latestCode.value.length
        ) {
            successProgress.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
            delay(450L)
        }
        closeOverlay(320)
    }

    val activeCode = latestCode.value
    val flights = flightProgressByDigit.mapNotNull { (index, progress) ->
        if (index !in activeCode.indices) return@mapNotNull null
        val source = digitSource(animation.codeBounds, index, activeCode.length.coerceAtLeast(1))
        val target = geometry.targets.getOrElse(index) { geometry.screen.center }
        val control = Offset(
            (source.x + target.x) / 2f + 34.dp.value * density.density,
            target.y + 22.dp.value * density.density,
        )
        DigitFlightVisual(
            index = index,
            progress = progress,
            source = source,
            control = control,
            target = target,
            movingPoint = quadraticPoint(source, control, target, progress),
        )
    }
    val contentAlpha = sceneAlpha.value
    val laptopAlpha = (1f - successProgress.value) * contentAlpha

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD90A111C).copy(alpha = (0xD9 / 255f) * contentAlpha))
            .clickable(onClick = {}),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val lineWidth = 1.5.dp.toPx()
            val corner = 7.dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = .62f * laptopAlpha),
                topLeft = geometry.screen.topLeft,
                size = geometry.screen.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                style = Stroke(lineWidth),
            )
            drawLine(
                color = Color.White.copy(alpha = .66f * laptopAlpha),
                start = Offset(geometry.body.left, geometry.screen.bottom + 3.dp.toPx()),
                end = Offset(geometry.body.right, geometry.screen.bottom + 3.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = .34f * laptopAlpha),
                start = Offset(geometry.body.left + 9.dp.toPx(), geometry.screen.bottom + 6.dp.toPx()),
                end = Offset(geometry.body.right - 9.dp.toPx(), geometry.screen.bottom + 6.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )

            geometry.targets.forEachIndexed { index, original ->
                val center = lerpOffset(original, geometry.screen.center, successProgress.value)
                val filled = completedDigits[index] == true || successProgress.value > 0f
                drawCircle(
                    color = if (filled) successColor.copy(alpha = laptopAlpha) else Color.Transparent,
                    radius = 5.5.dp.toPx(),
                    center = center,
                )
                drawCircle(
                    color = if (filled) successColor.copy(alpha = laptopAlpha) else Color.White.copy(alpha = .72f * laptopAlpha),
                    radius = 5.5.dp.toPx(),
                    center = center,
                    style = Stroke(1.2.dp.toPx()),
                )
            }

            flights.forEach { flight ->
                val headProgress = flight.progress
                val trailHeadProgress = quadraticProgressBehindByDistance(
                    start = flight.source,
                    control = flight.control,
                    end = flight.target,
                    fromProgress = headProgress,
                    distance = 14.dp.toPx(),
                )
                val tailProgress = quadraticProgressBehindByDistance(
                    start = flight.source,
                    control = flight.control,
                    end = flight.target,
                    fromProgress = trailHeadProgress,
                    distance = 72.dp.toPx(),
                )
                val tailPoint = quadraticPoint(flight.source, flight.control, flight.target, tailProgress)
                val trailHeadPoint = quadraticPoint(flight.source, flight.control, flight.target, trailHeadProgress)
                val trail = Path().apply {
                    moveTo(tailPoint.x, tailPoint.y)
                    val samples = 12
                    for (sample in 1..samples) {
                        val progress = tailProgress +
                            (trailHeadProgress - tailProgress) * sample / samples
                        val point = quadraticPoint(flight.source, flight.control, flight.target, progress)
                        lineTo(point.x, point.y)
                    }
                }
                if (trailHeadProgress > tailProgress) {
                    drawPath(
                        path = trail,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = .2f * contentAlpha),
                                successColor.copy(alpha = .9f * contentAlpha),
                            ),
                            start = tailPoint,
                            end = trailHeadPoint,
                        ),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                if (flight.progress > .62f) {
                    drawCircle(
                        color = successColor.copy(
                            alpha = ((flight.progress - .62f) / .38f).coerceIn(0f, 1f) * contentAlpha,
                        ),
                        radius = 8.dp.toPx(),
                        center = flight.movingPoint,
                    )
                }
            }

            if (successProgress.value > 0f) {
                val center = this.center
                val radius = 43.dp.toPx() * successProgress.value
                drawCircle(successColor.copy(alpha = contentAlpha), radius = radius, center = center)
            }
        }

        if (successProgress.value > .55f) {
            val reveal = ((successProgress.value - .55f) / .45f).coerceIn(0f, 1f)
            OtpulseIcon(
                imageVector = OtpulseIcons.Confirmation,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .graphicsLayer {
                        alpha = reveal * contentAlpha
                        scaleX = .82f + .18f * reveal
                        scaleY = .82f + .18f * reveal
                    },
                tint = Color.White,
            )
        }

        Text(
            stringResource(Res.string.send_code),
            color = Color.White.copy(alpha = .88f * laptopAlpha),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.offset {
                IntOffset(
                    (geometry.screen.center.x - 39.dp.toPx()).roundToInt(),
                    (geometry.screen.top + 7.dp.toPx()).roundToInt(),
                )
            },
        )

        Text(
            buildAnnotatedString {
                activeCode.forEachIndexed { index, digit ->
                    withStyle(
                        SpanStyle(
                            color = (if (completedDigits[index] == true) successColor else Color.White)
                                .copy(alpha = contentAlpha),
                        ),
                    ) {
                        append(digit)
                    }
                    if (index == 2 && index < activeCode.lastIndex) append(' ')
                }
            },
            style = OtpulseTypography.Code,
            modifier = Modifier.offset {
                IntOffset(animation.codeBounds.left.roundToInt(), animation.codeBounds.top.roundToInt())
            },
        )

        flights.filter { it.progress < .74f }.forEach { flight ->
            Text(
                activeCode[flight.index].toString(),
                color = (if (flight.progress < .5f) Color.White else successColor)
                    .copy(alpha = contentAlpha),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.offset {
                    IntOffset(
                        (flight.movingPoint.x - 5.dp.toPx()).roundToInt(),
                        (flight.movingPoint.y - 11.dp.toPx()).roundToInt(),
                    )
                },
            )
        }
    }
}

private fun laptopGeometry(host: Rect, digitCount: Int, density: Float): LaptopGeometry {
    val width = min(host.width * .62f, 280f * density)
    val height = min(host.height * .92f, 106f * density)
    val left = host.center.x - width / 2f
    val top = host.center.y - height / 2f
    val body = Rect(left, top, left + width, top + height)
    val screen = Rect(
        left + 20f * density,
        top + 3f * density,
        left + width - 20f * density,
        top + height - 12f * density,
    )
    val count = digitCount.coerceAtLeast(1)
    val available = screen.width * .68f
    val first = screen.center.x - available / 2f
    val step = if (count == 1) 0f else available / (count - 1)
    val targets = List(count) { index -> Offset(first + step * index, screen.top + screen.height * .61f) }
    return LaptopGeometry(body, screen, targets)
}

private fun digitSource(bounds: Rect, index: Int, count: Int): Offset = Offset(
    x = bounds.left + bounds.width * (index + .5f) / count,
    y = bounds.center.y,
)

private fun quadraticPoint(start: Offset, control: Offset, end: Offset, progress: Float): Offset {
    val inverse = 1f - progress
    return Offset(
        x = inverse * inverse * start.x + 2f * inverse * progress * control.x + progress * progress * end.x,
        y = inverse * inverse * start.y + 2f * inverse * progress * control.y + progress * progress * end.y,
    )
}

private fun quadraticProgressBehindByDistance(
    start: Offset,
    control: Offset,
    end: Offset,
    fromProgress: Float,
    distance: Float,
): Float {
    var progress = fromProgress.coerceIn(0f, 1f)
    var previous = quadraticPoint(start, control, end, progress)
    var travelled = 0f
    while (progress > 0f && travelled < distance) {
        val nextProgress = (progress - .01f).coerceAtLeast(0f)
        val next = quadraticPoint(start, control, end, nextProgress)
        val dx = previous.x - next.x
        val dy = previous.y - next.y
        travelled += sqrt(dx * dx + dy * dy)
        progress = nextProgress
        previous = next
    }
    return progress
}

private fun lerpOffset(start: Offset, end: Offset, progress: Float): Offset = Offset(
    x = start.x + (end.x - start.x) * progress,
    y = start.y + (end.y - start.y) * progress,
)

private fun Rect.relativeTo(origin: Offset): Rect = Rect(
    left = left - origin.x,
    top = top - origin.y,
    right = right - origin.x,
    bottom = bottom - origin.y,
)

private fun LayoutCoordinates.unclippedBoundsInRoot(): Rect {
    val topLeft = positionInRoot()
    return Rect(
        offset = topLeft,
        size = Size(size.width.toFloat(), size.height.toFloat()),
    )
}

@Composable
private fun EditScreen(
    store: AuthenticatorStore,
    accountId: String,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val loaded = remember(accountId) { store.editableAccount(accountId) }
    if (loaded is StorageResult.Failure) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(Res.string.account_open_failed), fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBack) { Text(stringResource(Res.string.action_back_to_codes)) }
        }
        return
    }
    val account = (loaded as StorageResult.Success).value
    var issuer by remember(accountId) { mutableStateOf(account.issuer) }
    var accountName by remember(accountId) { mutableStateOf(account.accountName) }
    var algorithm by remember(accountId) { mutableStateOf(account.algorithm) }
    var digits by remember(accountId) { mutableStateOf(account.digits) }
    var period by remember(accountId) { mutableStateOf(account.period) }
    var manualInputOverride by remember(accountId) { mutableStateOf(account.manualInputOverrideMilliseconds) }
    var advanced by remember(accountId) { mutableStateOf(false) }
    var error by remember(accountId) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(accountId) { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.dialog_delete_code)) },
            text = { Text(stringResource(Res.string.dialog_delete_account_body, account.issuer.ifBlank { account.accountName })) },
            confirmButton = {
                Button(
                    onClick = {
                        when (val result = store.deleteAccount(accountId)) {
                            AccountMutationResult.Success -> onChanged()
                            is AccountMutationResult.Failure -> {
                                error = result.message
                                confirmDelete = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC43C54)),
                ) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(stringResource(Res.string.title_edit), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
        OtpField(issuer, { issuer = it; error = null }, stringResource(Res.string.field_service_name))
        Spacer(Modifier.height(8.dp))
        OtpField(accountName, { accountName = it; error = null }, stringResource(Res.string.field_account))
        Text(
            stringResource(if (advanced) Res.string.action_hide_advanced else Res.string.action_show_advanced),
            color = OtpulseColors.Accent,
            fontSize = 13.sp,
            modifier = Modifier.clickable { advanced = !advanced }.padding(vertical = 12.dp),
        )
        if (advanced) {
            OtpField(algorithm, { algorithm = it; error = null }, stringResource(Res.string.field_algorithm))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { OtpField(digits, { digits = it; error = null }, stringResource(Res.string.field_digits)) }
                Box(Modifier.weight(1f)) { OtpField(period, { period = it; error = null }, stringResource(Res.string.field_period_seconds)) }
            }
            Spacer(Modifier.height(8.dp))
            OtpField(
                manualInputOverride,
                { manualInputOverride = it; error = null },
                stringResource(Res.string.field_timeshift_override),
            )
        }
        error?.let { Text(localizedFailure(it), color = Color(0xFFFF6B7A), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                when (val result = store.updateAccount(
                    accountId,
                    issuer,
                    accountName,
                    algorithm,
                    digits,
                    period,
                    manualInputOverride,
                )) {
                    AccountMutationResult.Success -> onChanged()
                    is AccountMutationResult.Failure -> error = result.message
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = OtpulseColors.Accent),
        ) { Text(stringResource(Res.string.action_save)) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.action_delete_code), color = Color(0xFFFF7188))
        }
        Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CountdownRing(
    periodMilliseconds: Long,
    color: Color,
    clock: Clock,
    refreshRevision: Long,
) {
    val drawTime = remember(periodMilliseconds, clock) {
        mutableLongStateOf(clock.nowEpochMilliseconds())
    }
    var seconds by remember(periodMilliseconds, clock) {
        mutableIntStateOf(remainingSeconds(clock.nowEpochMilliseconds(), periodMilliseconds))
    }
    var outgoingArcProgress by remember(periodMilliseconds, clock) { mutableFloatStateOf(0f) }
    val resetFill = remember(periodMilliseconds, clock) { Animatable(1f) }
    LaunchedEffect(periodMilliseconds, clock, refreshRevision) {
        val initialNow = clock.nowEpochMilliseconds()
        val tracker = CountdownArcTransitionTracker(initialNow / periodMilliseconds)
        drawTime.longValue = initialNow
        seconds = remainingSeconds(initialNow, periodMilliseconds)
        resetFill.snapTo(1f)

        while (isActive) {
            val now = withFrameNanos { clock.nowEpochMilliseconds() }
            val previousArcProgress =
                remainingMilliseconds(drawTime.longValue, periodMilliseconds).toFloat() / periodMilliseconds
            val shouldAnimateReset = tracker.observe(now / periodMilliseconds)
            drawTime.longValue = now
            val updatedSeconds = remainingSeconds(now, periodMilliseconds)
            if (updatedSeconds != seconds) seconds = updatedSeconds

            if (shouldAnimateReset) {
                outgoingArcProgress = previousArcProgress
                resetFill.snapTo(0f)
                launch {
                    resetFill.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = CountdownArcTransitionDurationMillis,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
            }
        }
    }
    val trackColor = OtpulseColors.Border
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val remaining = remainingMilliseconds(drawTime.longValue, periodMilliseconds)
            val wallClockProgress = remaining.toFloat() / periodMilliseconds
            drawArc(
                trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                androidx.compose.ui.graphics.lerp(trackColor, color, resetFill.value),
                startAngle = -90f,
                sweepAngle = 360f * wallClockProgress,
                useCenter = false,
                size = Size(size.width, size.height),
                style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
            )
            if (resetFill.value < 1f) {
                drawArc(
                    color.copy(alpha = color.alpha * (1f - resetFill.value)),
                    startAngle = -90f,
                    sweepAngle = 360f * outgoingArcProgress,
                    useCenter = false,
                    size = Size(size.width, size.height),
                    style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Text(seconds.toString(), fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

private fun remainingMilliseconds(now: Long, periodMilliseconds: Long): Long =
    periodMilliseconds - now % periodMilliseconds

private fun remainingSeconds(now: Long, periodMilliseconds: Long): Int =
    ((remainingMilliseconds(now, periodMilliseconds) + 999L) / 1_000L).toInt()

private fun String.formatResourceNumbers(vararg values: Int): String =
    values.foldIndexed(this) { index, text, value ->
        text.replace("%${index + 1}\$d", value.toString())
    }

@Composable
private fun localizedFailure(message: String): String {
    val normalized = message.lowercase()
    val resource = when {
        "сохранён" in normalized || "импортировано" in normalized || "saved" in normalized || "imported" in normalized -> null
        "существ" in normalized || "дублик" in normalized || "duplicate" in normalized -> Res.string.error_duplicate
        "base32" in normalized || "секрет" in normalized && ("повреж" in normalized || "invalid" in normalized) -> Res.string.error_invalid_secret
        "sha1" in normalized || "алгоритм" in normalized || "algorithm" in normalized -> Res.string.error_invalid_algorithm
        "6 или 8" in normalized || "digits" in normalized -> Res.string.error_invalid_digits
        "период" in normalized || "period" in normalized -> Res.string.error_invalid_period
        "имя или аккаунт" in normalized || "issuer и accountname" in normalized -> Res.string.error_missing_account_name
        "otpauth" in normalized -> Res.string.error_invalid_input
        "timeshift" in normalized -> Res.string.error_invalid_timeshift
        "не найден" in normalized || "not found" in normalized -> Res.string.error_account_not_found
        "камер" in normalized && ("доступ" in normalized || "permission" in normalized) -> Res.string.error_camera_permission
        "qr" in normalized || "камер" in normalized -> Res.string.error_qr
        "backup" in normalized || "файл" in normalized || "импорт" in normalized -> Res.string.error_backup
        "хранилищ" in normalized || "данн" in normalized || "storage" in normalized -> Res.string.error_storage
        else -> Res.string.error_generic
    }
    return resource?.let { stringResource(it) } ?: message
}

@Composable
private fun localizedHidStatus(state: HidTestState): String = when {
    state.waitingForFreshCode -> stringResource(Res.string.hid_waiting_fresh)
    state.sending -> stringResource(Res.string.hid_sending)
    state.connecting -> stringResource(Res.string.status_connecting)
    !state.supported -> stringResource(Res.string.hid_unsupported)
    !state.permissionGranted -> stringResource(Res.string.hid_permission_required)
    !state.bluetoothEnabled -> stringResource(Res.string.action_enable_bluetooth)
    state.hosts.any { it.address == state.selectedAddress && it.connected } -> stringResource(Res.string.status_connected)
    state.registered -> stringResource(Res.string.hid_ready)
    else -> stringResource(Res.string.hid_preparing)
}

@Composable
private fun AddScreen(
    initialSecret: String,
    onBack: () -> Unit,
    onAddManual: (String, String, String, String, String, String) -> AddAccountResult,
) {
    var secret by remember(initialSecret) { mutableStateOf(initialSecret) }
    var issuer by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var algorithm by remember { mutableStateOf("SHA1") }
    var digits by remember { mutableStateOf("6") }
    var period by remember { mutableStateOf("30") }
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        AppHeader(stringResource(Res.string.title_add), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
        Text(
            stringResource(Res.string.add_code_hint),
            color = OtpulseColors.TextMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        OtpField(secret, { secret = it; error = null }, stringResource(Res.string.field_secret_base32))
        Spacer(Modifier.height(8.dp))
        OtpField(issuer, { issuer = it; error = null }, stringResource(Res.string.field_service_name))
        Spacer(Modifier.height(8.dp))
        OtpField(account, { account = it; error = null }, stringResource(Res.string.field_account_optional))
        Text(
            stringResource(if (advanced) Res.string.action_hide_advanced else Res.string.action_show_advanced),
            color = OtpulseColors.Accent,
            fontSize = 13.sp,
            modifier = Modifier.clickable { advanced = !advanced }.padding(vertical = 12.dp),
        )
        if (advanced) {
            OtpField(algorithm, { algorithm = it; error = null }, stringResource(Res.string.field_algorithm))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { OtpField(digits, { digits = it; error = null }, stringResource(Res.string.field_digits)) }
                Box(Modifier.weight(1f)) { OtpField(period, { period = it; error = null }, stringResource(Res.string.field_period_seconds)) }
            }
        }
        error?.let { Text(localizedFailure(it), color = Color(0xFFFF6B7A), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                val result = onAddManual(issuer, account, secret, algorithm, digits, period)
                when (result) {
                    AddAccountResult.Success -> onBack()
                    is AddAccountResult.Failure -> error = result.message
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = OtpulseColors.Accent),
        ) { Text(stringResource(Res.string.action_add_code)) }
        Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OtpField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun BluetoothDiagnosticsDialog(
    controller: HidTestController,
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    onRequestDiscoverable: () -> Unit,
) {
    val state by controller.state.collectAsState()
    LaunchedEffect(controller) { controller.initialize() }
    val selectedHost = state.hosts.firstOrNull { it.address == state.selectedAddress }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.bluetooth_diagnostics), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    OtpulseIcon(
                        imageVector = OtpulseIcons.Close,
                        contentDescription = stringResource(Res.string.action_close),
                        modifier = Modifier.size(OtpulseIconSizes.Standard),
                        tint = OtpulseColors.TextMuted,
                    )
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
        Box(
            Modifier.size(82.dp).clip(CircleShape).background(OtpulseColors.SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            OtpulseIcon(
                imageVector = OtpulseIcons.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(OtpulseIconSizes.Large),
                tint = if (state.registered) OtpulseColors.Accent else OtpulseColors.TextMuted,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(Res.string.bluetooth_keyboard), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(Res.string.bluetooth_diagnostics_subtitle), color = OtpulseColors.TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OtpulseColors.Surface).padding(14.dp)
        ) {
            HidStatusRow(stringResource(Res.string.status_hid_api), state.supported)
            HidStatusRow(stringResource(Res.string.status_permissions), state.permissionGranted)
            HidStatusRow(stringResource(Res.string.status_bluetooth_enabled), state.bluetoothEnabled)
            HidStatusRow(stringResource(Res.string.status_hid_profile), state.profileReady)
            HidStatusRow(stringResource(Res.string.status_keyboard_registered), state.registered)
            Spacer(Modifier.height(8.dp))
            Text(localizedHidStatus(state), color = OtpulseColors.Accent, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))

        when {
            !state.supported -> Text(
                stringResource(Res.string.hid_unsupported),
                color = OtpulseColors.TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            !state.permissionGranted -> Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = OtpulseColors.Accent),
            ) { Text(stringResource(Res.string.action_allow_nearby)) }
            !state.bluetoothEnabled -> Button(
                onClick = onRequestEnableBluetooth,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = OtpulseColors.Accent),
            ) { Text(stringResource(Res.string.action_enable_bluetooth)) }
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRequestDiscoverable, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.action_discoverable_120))
                    }
                    OutlinedButton(onClick = controller::refresh, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.action_refresh))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(Res.string.pairing_instructions),
                    color = OtpulseColors.TextMuted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.paired_computers), modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (state.hosts.isEmpty()) {
                    Text(stringResource(Res.string.paired_computers_empty), color = OtpulseColors.TextMuted, fontSize = 13.sp)
                } else {
                    state.hosts.forEach { host ->
                        val selected = host.address == state.selectedAddress
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (selected) OtpulseColors.SurfaceRaised else OtpulseColors.Surface)
                                .clickable(enabled = !state.connecting && !state.sending) { controller.connect(host.address) }
                                .padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HostConnectionIcon(host.connected)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(host.name, fontWeight = FontWeight.Medium)
                                Text(host.address, color = OtpulseColors.TextMuted, fontSize = 11.sp)
                            }
                            Text(if (host.connected) stringResource(Res.string.status_connected) else if (selected && state.connecting) "…" else stringResource(Res.string.action_connect), color = OtpulseColors.TextMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(7.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (selectedHost?.connected == true) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(OtpulseColors.Surface).padding(13.dp)
                    ) {
                        Text(stringResource(Res.string.mac_setup_title), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            stringResource(Res.string.mac_setup_instructions),
                            color = OtpulseColors.TextMuted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { controller.sendMacSetupKey(MacSetupKey.RIGHT_OF_LEFT_SHIFT) },
                                enabled = !state.sending,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(Res.string.mac_setup_send_z)) }
                            OutlinedButton(
                                onClick = { controller.sendMacSetupKey(MacSetupKey.LEFT_OF_RIGHT_SHIFT) },
                                enabled = !state.sending,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(Res.string.mac_setup_send_slash)) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(OtpulseColors.Surface).padding(13.dp)
                    ) {
                        Text(stringResource(Res.string.send_statistics), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            stringResource(Res.string.send_statistics_values, state.successfulSendCount, state.failedSendCount, state.averageSendDurationMilliseconds),
                            color = OtpulseColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = controller::sendTestSequence,
                    enabled = selectedHost?.connected == true && !state.sending,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = OtpulseColors.Accent),
                ) {
                    Text(
                        if (state.sending) stringResource(Res.string.sending_progress, state.sentKeys, state.sendKeysTotal.coerceAtLeast(7))
                        else stringResource(Res.string.diagnostic_send)
                    )
                }
                if (selectedHost?.connected == true) {
                    OutlinedButton(
                        onClick = controller::disconnect,
                        enabled = !state.sending && !state.connecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.disconnect_from, selectedHost.name)) }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(Res.string.diagnostic_disclaimer),
            color = OtpulseColors.TextMuted,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
            }
        }
    }
}

@Composable
private fun HidStatusRow(label: String, ready: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text(if (ready) "PASS" else "—", color = if (ready) OtpulseColors.Success else OtpulseColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun HostConnectionIcon(connected: Boolean) {
    OtpulseIcon(
        imageVector = if (connected) OtpulseIcons.Success else OtpulseIcons.Disconnected,
        contentDescription = null,
        modifier = Modifier.size(OtpulseIconSizes.Small),
        tint = if (connected) OtpulseColors.Success else OtpulseColors.TextMuted,
    )
}

@Composable
private fun AppHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    horizontalPadding: Dp = 18.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    val backDescription = stringResource(Res.string.action_back)
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                OtpulseIcon(
                    imageVector = OtpulseIcons.Back,
                    contentDescription = backDescription,
                    modifier = Modifier.size(OtpulseIconSizes.Standard),
                    tint = OtpulseColors.Text,
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).semantics { contentDescription = title },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            trailing?.invoke()
        }
    }
}

@Composable
private fun SettingsScreen(
    expectedManualInputMilliseconds: Long,
    selectedLanguage: AppLanguage?,
    selectedTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    onSelectLanguage: (AppLanguage?) -> Unit,
    onBack: () -> Unit,
    onOpenTimeShift: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    var showLanguageSelector by remember { mutableStateOf(false) }
    var showThemeSelector by remember { mutableStateOf(false) }
    if (showLanguageSelector) {
        AlertDialog(
            onDismissRequest = { showLanguageSelector = false },
            title = { Text(stringResource(Res.string.dialog_choose_language)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LanguageOptionRow(
                        label = stringResource(Res.string.language_system),
                        selected = selectedLanguage == null,
                        onClick = {
                            onSelectLanguage(null)
                            showLanguageSelector = false
                        },
                    )
                    AppLanguage.entries.forEach { language ->
                        LanguageOptionRow(
                            label = appLanguageName(language),
                            selected = selectedLanguage == language,
                            onClick = {
                                onSelectLanguage(language)
                                showLanguageSelector = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showLanguageSelector = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
    if (showThemeSelector) {
        AlertDialog(
            onDismissRequest = { showThemeSelector = false },
            title = { Text(stringResource(Res.string.dialog_choose_theme)) },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        LanguageOptionRow(
                            label = appThemeName(theme),
                            selected = selectedTheme == theme,
                            onClick = {
                                onSelectTheme(theme)
                                showThemeSelector = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showThemeSelector = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        AppHeader(stringResource(Res.string.title_settings), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            SettingsGroup(
                stringResource(Res.string.settings_general),
                listOf(
                    stringResource(Res.string.settings_manual_timeshift) to stringResource(Res.string.value_seconds, expectedManualInputMilliseconds / 1_000L),
                    stringResource(Res.string.settings_language) to (selectedLanguage?.let { appLanguageName(it) }
                        ?: stringResource(Res.string.language_system)),
                    stringResource(Res.string.settings_theme) to appThemeName(selectedTheme),
                ),
                onRowClick = {
                    when (it) {
                        0 -> onOpenTimeShift()
                        1 -> showLanguageSelector = true
                        2 -> showThemeSelector = true
                    }
                },
            )
            Spacer(Modifier.height(18.dp))
            SettingsGroup(
                stringResource(Res.string.settings_backup),
                listOf(stringResource(Res.string.settings_export_codes) to "", stringResource(Res.string.settings_import_codes) to ""),
                onRowClick = { if (it == 0) onExportBackup() else onImportBackup() },
            )
        }
    }
}

@Composable
private fun appThemeName(theme: AppTheme): String = stringResource(
    when (theme) {
        AppTheme.SYSTEM -> Res.string.theme_system
        AppTheme.LIGHT -> Res.string.theme_light
        AppTheme.DARK -> Res.string.theme_dark
    }
)

@Composable
private fun LanguageOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun appLanguageName(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.ENGLISH -> Res.string.language_english
        AppLanguage.CHINESE_SIMPLIFIED -> Res.string.language_chinese_simplified
        AppLanguage.HINDI -> Res.string.language_hindi
        AppLanguage.SPANISH -> Res.string.language_spanish
        AppLanguage.FRENCH -> Res.string.language_french
        AppLanguage.ARABIC -> Res.string.language_arabic
        AppLanguage.BENGALI -> Res.string.language_bengali
        AppLanguage.PORTUGUESE -> Res.string.language_portuguese
        AppLanguage.INDONESIAN -> Res.string.language_indonesian
        AppLanguage.URDU -> Res.string.language_urdu
        AppLanguage.RUSSIAN -> Res.string.language_russian
        AppLanguage.UKRAINIAN -> Res.string.language_ukrainian
        AppLanguage.KAZAKH -> Res.string.language_kazakh
    }
)

@Composable
private fun TimeShiftSettingsScreen(
    initialMilliseconds: Long,
    onBack: () -> Unit,
    onSave: (Long) -> TimeShiftSettingResult,
) {
    var seconds by remember(initialMilliseconds) {
        mutableFloatStateOf((initialMilliseconds / 1_000f).coerceIn(0f, 5f))
    }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        AppHeader(stringResource(Res.string.title_timeshift), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                stringResource(Res.string.timeshift_explanation),
                color = OtpulseColors.TextMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(Res.string.value_seconds, seconds.roundToInt()),
                color = OtpulseColors.Accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Slider(
                value = seconds,
                onValueChange = {
                    seconds = it.roundToInt().toFloat()
                    error = null
                },
                valueRange = 0f..5f,
                steps = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.value_seconds, 0), color = OtpulseColors.TextMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(stringResource(Res.string.value_seconds, 5), color = OtpulseColors.TextMuted, fontSize = 12.sp)
            }
            error?.let { Text(localizedFailure(it), color = Color(0xFFFF6B7A), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    val milliseconds = seconds.roundToInt() * 1_000L
                    when (val result = onSave(milliseconds)) {
                        TimeShiftSettingResult.Success -> Unit
                        is TimeShiftSettingResult.Failure -> error = result.message
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = OtpulseColors.Accent),
            ) { Text(stringResource(Res.string.action_save)) }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    rows: List<Pair<String, String>>,
    onRowClick: ((Int) -> Unit)? = null,
) {
    Text(title, color = OtpulseColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, bottom = 7.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OtpulseColors.Surface)) {
        rows.forEachIndexed { index, row ->
            Row(
                Modifier.fillMaxWidth()
                    .then(if (onRowClick != null) Modifier.clickable { onRowClick(index) } else Modifier)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.first, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(row.second, color = OtpulseColors.TextMuted, fontSize = 13.sp)
                OtpulseIcon(
                    imageVector = OtpulseIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(OtpulseIconSizes.Standard),
                    tint = OtpulseColors.TextMuted,
                )
            }
            if (index < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(OtpulseColors.Border))
        }
    }
}

private const val MIN_DIGIT_FLIGHT_STAGGER_MILLISECONDS = 55L
