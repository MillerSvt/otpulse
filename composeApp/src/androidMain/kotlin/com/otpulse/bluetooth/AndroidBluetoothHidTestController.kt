package com.otpulse.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.otpulse.core.time.PlatformClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SuppressLint("MissingPermission")
class AndroidBluetoothHidTestController private constructor(context: Context) : HidTestController {
    private data class ObservedConnection(val state: Int, val atMilliseconds: Long)

    private val appContext = context.applicationContext
    private val debugLoggingEnabled = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(
        HidTestState(
            supported = adapter != null,
            selectedAddress = preferences.getString(LAST_HOST_KEY, null),
            message = "Подготовка Bluetooth HID…",
        )
    )
    override val state: StateFlow<HidTestState> = mutableState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendMutex = Mutex()
    private val otpSendPipeline = BluetoothOtpSendPipeline(PlatformClock)
    private val observedConnections = mutableMapOf<String, ObservedConnection>()
    private val manuallyDisconnectedHosts = mutableSetOf<String>()
    private var hidDevice: BluetoothHidDevice? = null
    private var proxyRequested = false
    private var reconnectWhenReady = true
    private var pendingConnectAddress: String? = null
    private var autoReconnectAttempts = 0
    private var connectionTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var monitorJob: Job? = null

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            log("profile-service connected")
            hidDevice = proxy as BluetoothHidDevice
            proxyRequested = false
            mutableState.update { it.copy(profileReady = true, message = "HID profile получен; регистрирую клавиатуру…") }
            registerKeyboard()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            log("profile-service disconnected")
            hidDevice = null
            proxyRequested = false
            stopConnectionJobs()
            monitorJob?.cancel()
            monitorJob = null
            mutableState.update {
                it.copy(
                    profileReady = false,
                    registered = false,
                    connecting = false,
                    sending = false,
                    waitingForFreshCode = false,
                    sendingAccountId = null,
                    lastSendOutcome = if (it.sending) HidSendOutcome.FAILURE else it.lastSendOutcome,
                    recoveryError = "Системный HID profile отключился",
                    message = "HID profile отключён",
                )
            }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            log("app-status registered=$registered pluggedHost=${pluggedDevice?.safeId() ?: "none"}")
            mutableState.update {
                it.copy(
                    registered = registered,
                    message = if (registered) "OTPulse зарегистрирован как Bluetooth-клавиатура" else "Регистрация HID-клавиатуры сброшена",
                )
            }
            if (registered) {
                startConnectionMonitor()
                refreshHosts()
                reconnectLastHostAfterActivation()
            } else {
                stopConnectionJobs()
                monitorJob?.cancel()
                monitorJob = null
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, connectionState: Int) {
            log("connection-callback host=${device.safeId()} state=${connectionState.label()}")
            handleConnectionState(device, connectionState)
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            hidDevice?.replyReport(device, type, id, ByteArray(8))
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            log("virtual-cable-unplug host=${device.safeId()}")
            recordConnection(device.address, BluetoothProfile.STATE_DISCONNECTED)
            stopConnectionJobs()
            mutableState.update {
                it.copy(
                    connecting = false,
                    sending = false,
                    waitingForFreshCode = false,
                    sendingAccountId = null,
                    lastSendOutcome = if (it.sending) HidSendOutcome.FAILURE else it.lastSendOutcome,
                    recoveryError = "Компьютер удалил HID virtual cable. Возможно, потребуется повторное pairing.",
                    message = "Host разорвал virtual cable",
                )
            }
            refreshHosts()
        }
    }

    override fun initialize() {
        reconnectWhenReady = true
        if (!state.value.supported) {
            mutableState.update { it.copy(message = "BluetoothHidDevice недоступен на этом устройстве") }
            return
        }
        val permissionGranted = hasBluetoothPermissions()
        val enabled = adapter?.isEnabled == true
        log("initialize permission=$permissionGranted bluetooth=$enabled profile=${hidDevice != null} registered=${state.value.registered}")
        mutableState.update { it.copy(permissionGranted = permissionGranted, bluetoothEnabled = enabled) }
        when {
            !permissionGranted -> mutableState.update { it.copy(message = "Разрешите Nearby devices для Bluetooth HID") }
            !enabled -> mutableState.update { it.copy(message = "Включите Bluetooth") }
            hidDevice != null -> {
                if (!state.value.registered) registerKeyboard() else {
                    refreshHosts()
                    reconnectLastHostAfterActivation()
                }
            }
            !proxyRequested -> {
                proxyRequested = true
                mutableState.update { it.copy(message = "Получаю системный HID profile…") }
                if (adapter?.getProfileProxy(appContext, serviceListener, BluetoothProfile.HID_DEVICE) != true) {
                    proxyRequested = false
                    mutableState.update { it.copy(message = "Android не выдал HID profile") }
                }
            }
        }
    }

    override fun refresh() {
        if (!hasBluetoothPermissions()) {
            mutableState.update { it.copy(permissionGranted = false, hosts = emptyList(), connecting = false, message = "Нет разрешения Nearby devices") }
            return
        }
        val enabled = adapter?.isEnabled == true
        mutableState.update { it.copy(permissionGranted = true, bluetoothEnabled = enabled) }
        if (!enabled || hidDevice == null) initialize() else refreshHosts()
    }

    override fun connect(address: String) {
        log("manual-connect host=${address.safeId()}")
        autoReconnectAttempts = 0
        reconnectJob?.cancel()
        setManualDisconnect(address, false)
        startConnect(address, automatic = false)
    }

    override fun retryConnection() {
        val address = state.value.selectedAddress ?: preferences.getString(LAST_HOST_KEY, null)
        if (address == null) {
            mutableState.update { it.copy(recoveryError = null, message = "Выберите сопряжённое устройство") }
            return
        }
        autoReconnectAttempts = 0
        log("recovery-retry host=${address.safeId()}")
        mutableState.update { it.copy(recoveryError = null) }
        startConnect(address, automatic = false)
    }

    override fun selectDifferentHost() {
        log("recovery-select-different")
        stopConnectionJobs()
        pendingConnectAddress = null
        mutableState.update {
            it.copy(
                selectedAddress = null,
                connecting = false,
                sending = false,
                waitingForFreshCode = false,
                sendingAccountId = null,
                lastSendOutcome = HidSendOutcome.NONE,
                reconnectAttempt = 0,
                recoveryError = null,
                message = "Выберите другое сопряжённое устройство",
            )
        }
    }

    override fun dismissRecovery() {
        mutableState.update { it.copy(connecting = false, recoveryError = null) }
    }

    override fun disconnect() {
        val profile = hidDevice ?: return
        val device = selectedDevice() ?: return
        pendingConnectAddress = null
        setManualDisconnect(device.address, true)
        log("manual-disconnect host=${device.safeId()}")
        stopConnectionJobs()
        recordConnection(device.address, BluetoothProfile.STATE_DISCONNECTING)
        mutableState.update {
            it.copy(
                connecting = true,
                sending = false,
                waitingForFreshCode = false,
                sendingAccountId = null,
                lastSendOutcome = HidSendOutcome.NONE,
                recoveryError = null,
                message = "Отключение…",
            )
        }
        if (!profile.disconnect(device)) {
            setManualDisconnect(device.address, false)
            recordConnection(device.address, profile.getConnectionState(device))
            mutableState.update { it.copy(connecting = false, message = "Не удалось начать отключение") }
            refreshHosts()
            return
        }
        connectionTimeoutJob = scope.launch {
            delay(DISCONNECT_TIMEOUT_MS)
            if (state.value.connecting && state.value.selectedAddress == device.address) {
                val actual = profile.getConnectionState(device)
                log("disconnect-timeout host=${device.safeId()} actual=${actual.label()}")
                recordConnection(device.address, actual)
                mutableState.update {
                    it.copy(
                        connecting = false,
                        recoveryError = if (actual == BluetoothProfile.STATE_DISCONNECTED) null else "Не удалось отключить ${safeName(device)}. Можно сразу выбрать другой компьютер — OTPulse повторит переключение.",
                        message = if (actual == BluetoothProfile.STATE_DISCONNECTED) "Отключено" else "Отключение не завершилось",
                    )
                }
                refreshHosts()
            }
        }
    }

    override fun sendTestSequence() {
        sendReports(
            reports = KeyboardReportEncoder.digitsWithEnter(TEST_DIGITS),
            progressTotal = 7,
            startingMessage = "Отправляю $TEST_DIGITS + Enter…",
            successMessage = "Успешно отправлено: $TEST_DIGITS + Enter",
        )
    }

    override fun sendOtp(
        accountId: String,
        periodMilliseconds: Long,
        snapshotProvider: BluetoothOtpSnapshotProvider,
    ) {
        if (state.value.sending) return
        val profile = hidDevice ?: return fail("HID profile недоступен")
        val device = selectedDevice() ?: return fail("Сначала выберите компьютер")
        if (!isHostConnected(device.address) || profile.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
            mutableState.update { it.copy(recoveryError = "Компьютер не подключён") }
            return fail("Компьютер не подключён")
        }

        val statistics = timingStatistics(device.address)
        mutableState.update {
            it.copy(
                sending = true,
                waitingForFreshCode = false,
                sendingAccountId = accountId,
                sentKeys = 0,
                sendKeysTotal = 0,
                lastSendOutcome = HidSendOutcome.NONE,
                recoveryError = null,
                message = "Подготавливаю действующий код…",
            )
        }
        scope.launch {
            sendMutex.withLock {
                val prepared = try {
                    otpSendPipeline.prepare(
                        accountId = accountId,
                        periodMilliseconds = periodMilliseconds,
                        expectedBluetoothInputMilliseconds = statistics.averageMilliseconds,
                        onWaitingForFreshCurrent = { waitMilliseconds ->
                            log("otp-send-wait boundaryMs=$waitMilliseconds")
                            mutableState.update {
                                it.copy(
                                    waitingForFreshCode = true,
                                    message = "Код скоро истечёт. Жду новый код…",
                                )
                            }
                        },
                        snapshotProvider = snapshotProvider,
                    )
                } catch (error: Throwable) {
                    log("otp-send-prepare-exception type=${error::class.simpleName}")
                    mutableState.update {
                        it.copy(
                            sending = false,
                            waitingForFreshCode = false,
                            sendingAccountId = null,
                            failedSendCount = it.failedSendCount + 1,
                            lastSendOutcome = HidSendOutcome.FAILURE,
                            message = "Не удалось подготовить действующий код",
                        )
                    }
                    return@withLock
                }
                val ready = prepared as? BluetoothOtpPreparation.Ready
                if (ready == null) {
                    val reason = (prepared as BluetoothOtpPreparation.Failure).reason
                    log("otp-send-prepare-failure reason=$reason")
                    mutableState.update {
                        it.copy(
                            sending = false,
                            waitingForFreshCode = false,
                            sendingAccountId = null,
                            failedSendCount = it.failedSendCount + 1,
                            lastSendOutcome = HidSendOutcome.FAILURE,
                            message = reason.userMessage(),
                        )
                    }
                    return@withLock
                }

                val reports = KeyboardReportEncoder.digitsWithEnter(ready.snapshot.code)
                    .map { it.copy(keyDown = it.keyDown.copyOf(), keyUp = it.keyUp.copyOf()) }
                mutableState.update {
                    it.copy(
                        waitingForFreshCode = false,
                        sendKeysTotal = reports.size,
                        message = "Отправляю код + Enter…",
                    )
                }
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    transmitReports(profile, device, reports, reports.size)
                    val duration = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
                    val updatedStatistics = statistics.record(duration)
                    saveTimingStatistics(device.address, updatedStatistics)
                    log("otp-send-success durationMs=$duration reports=${reports.size} counter=${ready.snapshot.counter}")
                    mutableState.update {
                        it.copy(
                            sending = false,
                            waitingForFreshCode = false,
                            sendingAccountId = null,
                            successfulSendCount = it.successfulSendCount + 1,
                            lastSendOutcome = HidSendOutcome.SUCCESS,
                            averageSendDurationMilliseconds = updatedStatistics.averageMilliseconds,
                            message = "Код успешно отправлен",
                        )
                    }
                } catch (error: Throwable) {
                    handleSendFailure(device, error, productionOtp = true)
                }
            }
        }
    }

    override fun sendMacSetupKey(key: MacSetupKey) {
        sendReports(
            reports = listOf(KeyboardReportEncoder.singleUsage(key.label, key.usage)),
            progressTotal = 1,
            startingMessage = "Отправляю клавишу ${key.label} для Keyboard Setup Assistant…",
            successMessage = "Клавиша ${key.label} отправлена",
        )
    }

    override fun close() {
        scope.cancel()
        hidDevice?.unregisterApp()
        hidDevice?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hidDevice = null
        proxyRequested = false
        synchronized(INSTANCE_LOCK) { if (instance === this) instance = null }
    }

    private fun startConnect(address: String, automatic: Boolean) {
        val profile = hidDevice ?: return connectionFailure("HID profile ещё не готов", automatic)
        if (!state.value.registered) return connectionFailure("HID-клавиатура ещё не зарегистрирована", automatic)
        val device = bondedDevices().firstOrNull { it.address == address }
            ?: return connectionFailure("Последнее устройство больше не сопряжено", automatic)
        incompatibleHostReason(device)?.let { reason ->
            log("connect-incompatible host=${device.safeId()} class=${device.bluetoothClass?.deviceClass} reason=$reason")
            stopConnectionJobs()
            mutableState.update {
                it.copy(
                    selectedAddress = null,
                    connecting = false,
                    sending = false,
                    waitingForFreshCode = false,
                    sendingAccountId = null,
                    reconnectAttempt = 0,
                    recoveryError = null,
                    message = "$reason Выберите компьютер.",
                )
            }
            return
        }

        log("connect-start host=${device.safeId()} class=${device.bluetoothClass?.deviceClass} automatic=$automatic attempt=$autoReconnectAttempts")

        val currentState = resolvedConnectionState(device)
        if (currentState == BluetoothProfile.STATE_CONNECTED) {
            onConnected(device)
            return
        }

        val connectedElsewhere = bondedDevices().filter {
            it.address != address && resolvedConnectionState(it) == BluetoothProfile.STATE_CONNECTED
        }
        if (connectedElsewhere.isNotEmpty()) {
            beginHostSwitch(profile, device, connectedElsewhere)
            return
        }

        connectionTimeoutJob?.cancel()
        recordConnection(address, BluetoothProfile.STATE_CONNECTING)
        mutableState.update {
            it.copy(
                selectedAddress = address,
                connecting = true,
                sending = false,
                waitingForFreshCode = false,
                sendingAccountId = null,
                    sentKeys = 0,
                    lastSendOutcome = HidSendOutcome.NONE,
                    recoveryError = null,
                message = if (automatic) "Автоподключение к ${safeName(device)} (${autoReconnectAttempts.coerceAtLeast(1)}/$MAX_AUTO_RECONNECTS)…" else "Подключение к ${safeName(device)}…",
            )
        }
        refreshHosts()
        if (!profile.connect(device)) {
            log("connect-api-rejected host=${device.safeId()} automatic=$automatic")
            recordConnection(address, BluetoothProfile.STATE_DISCONNECTED)
            mutableState.update { it.copy(connecting = false) }
            refreshHosts()
            connectionFailure("Android отклонил запрос подключения к ${safeName(device)}", automatic)
            return
        }

        connectionTimeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (state.value.selectedAddress != address || !state.value.connecting) return@launch
            val actual = profile.getConnectionState(device)
            log("connect-timeout host=${device.safeId()} actual=${actual.label()} automatic=$automatic")
            if (actual == BluetoothProfile.STATE_CONNECTED) {
                handleConnectionState(device, BluetoothProfile.STATE_CONNECTED)
            } else {
                profile.disconnect(device)
                recordConnection(address, BluetoothProfile.STATE_DISCONNECTED)
                mutableState.update { it.copy(connecting = false) }
                refreshHosts()
                connectionFailure("Не удалось подключиться к ${safeName(device)} за ${CONNECTION_TIMEOUT_MS / 1_000} секунд", automatic)
            }
        }
    }

    private fun handleConnectionState(device: BluetoothDevice, connectionState: Int) {
        recordConnection(device.address, connectionState)
        val switchingTo = pendingConnectAddress
        val manuallyDisconnected = isManuallyDisconnected(device.address)
        val isOldSwitchHost = switchingTo != null && device.address != switchingTo && manuallyDisconnected
        when (connectionState) {
            BluetoothProfile.STATE_CONNECTED -> {
                if (manuallyDisconnected && switchingTo != device.address) {
                    log("reject-reconnect host=${device.safeId()} switchingTo=${switchingTo?.safeId() ?: "none"}")
                    hidDevice?.disconnect(device)
                    recordConnection(device.address, BluetoothProfile.STATE_DISCONNECTING)
                    mutableState.update {
                        it.copy(
                            connecting = switchingTo != null,
                            message = if (switchingTo != null) "Переключение на другой компьютер…" else "Отключаю повторное соединение с ${safeName(device)}…",
                        )
                    }
                } else {
                    onConnected(device)
                }
            }
            BluetoothProfile.STATE_CONNECTING -> mutableState.update {
                if (manuallyDisconnected) it else it.copy(selectedAddress = device.address, connecting = true, message = "Подключение к ${safeName(device)}…")
            }
            BluetoothProfile.STATE_DISCONNECTING -> mutableState.update {
                if (isOldSwitchHost) it else it.copy(
                    selectedAddress = device.address,
                    connecting = true,
                    sending = false,
                    waitingForFreshCode = false,
                    message = "Отключение от ${safeName(device)}…",
                )
            }
            else -> onDisconnected(device)
        }
        refreshHosts()
    }

    private fun onConnected(device: BluetoothDevice) {
        log("connected host=${device.safeId()}")
        stopConnectionJobs()
        pendingConnectAddress = null
        setManualDisconnect(device.address, false)
        autoReconnectAttempts = 0
        preferences.edit().putString(LAST_HOST_KEY, device.address).apply()
        mutableState.update {
            it.copy(
                selectedAddress = device.address,
                connecting = false,
                reconnectAttempt = 0,
                recoveryError = null,
                averageSendDurationMilliseconds = timingStatistics(device.address).averageMilliseconds,
                message = "Подключено к ${safeName(device)}",
            )
        }
    }

    private fun onDisconnected(device: BluetoothDevice) {
        val wasIntentional = isManuallyDisconnected(device.address)
        val switchingTo = pendingConnectAddress
        if (state.value.selectedAddress == device.address || (switchingTo != null && device.address != switchingTo)) {
            connectionTimeoutJob?.cancel()
        }
        val interruptedSend = state.value.sending
        log("disconnected host=${device.safeId()} intentional=$wasIntentional interruptedSend=$interruptedSend")
        if (switchingTo != null && device.address != switchingTo) {
            mutableState.update {
                it.copy(
                    selectedAddress = switchingTo,
                    connecting = true,
                    sending = false,
                    waitingForFreshCode = false,
                    sendingAccountId = null,
                    lastSendOutcome = if (interruptedSend) HidSendOutcome.FAILURE else it.lastSendOutcome,
                    message = "Подключение к выбранному компьютеру…",
                )
            }
            val anotherConnectedHost = bondedDevices().any {
                it.address != switchingTo && resolvedConnectionState(it) == BluetoothProfile.STATE_CONNECTED
            }
            if (!anotherConnectedHost) {
                pendingConnectAddress = null
                startConnect(switchingTo, automatic = false)
            }
            return
        }
        if (wasIntentional && state.value.selectedAddress != device.address) {
            log("ignore-background-disconnect host=${device.safeId()}")
            return
        }
        mutableState.update {
            it.copy(
                selectedAddress = device.address,
                connecting = false,
                sending = false,
                waitingForFreshCode = false,
                sendingAccountId = null,
                lastSendOutcome = if (interruptedSend) HidSendOutcome.FAILURE else it.lastSendOutcome,
                message = if (wasIntentional) "${safeName(device)} отключён" else if (interruptedSend) "Соединение потеряно во время отправки" else "Соединение с ${safeName(device)} потеряно",
            )
        }
        if (!wasIntentional && state.value.registered) scheduleAutoReconnect(device.address)
    }

    private fun beginHostSwitch(
        profile: BluetoothHidDevice,
        target: BluetoothDevice,
        connectedElsewhere: List<BluetoothDevice>,
    ) {
        stopConnectionJobs()
        pendingConnectAddress = target.address
        connectedElsewhere.forEach { setManualDisconnect(it.address, true) }
        log("switch-start target=${target.safeId()} current=${connectedElsewhere.joinToString { it.safeId() }}")
        mutableState.update {
            it.copy(
                selectedAddress = target.address,
                connecting = true,
                sending = false,
                waitingForFreshCode = false,
                sendingAccountId = null,
                sentKeys = 0,
                lastSendOutcome = HidSendOutcome.NONE,
                recoveryError = null,
                message = "Отключаю текущий компьютер и подключаюсь к ${safeName(target)}…",
            )
        }
        connectedElsewhere.forEach { current ->
            recordConnection(current.address, BluetoothProfile.STATE_DISCONNECTING)
            if (!profile.disconnect(current)) {
                log("switch-disconnect-rejected host=${current.safeId()}")
            }
        }
        refreshHosts()
        connectionTimeoutJob = scope.launch {
            delay(DISCONNECT_TIMEOUT_MS)
            if (pendingConnectAddress != target.address) return@launch
            val stillConnected = connectedElsewhere.any {
                profile.getConnectionState(it) != BluetoothProfile.STATE_DISCONNECTED
            }
            if (stillConnected) {
                pendingConnectAddress = null
                mutableState.update {
                    it.copy(
                        connecting = false,
                        recoveryError = "Не удалось отключить текущий компьютер. Выключите на нём Bluetooth и повторите переключение.",
                        message = "Переключение не удалось",
                    )
                }
            } else {
                pendingConnectAddress = null
                startConnect(target.address, automatic = false)
            }
        }
    }

    private fun scheduleAutoReconnect(address: String) {
        if (reconnectJob?.isActive == true || state.value.connecting) return
        if (autoReconnectAttempts >= MAX_AUTO_RECONNECTS) {
            mutableState.update {
                it.copy(
                    connecting = false,
                    reconnectAttempt = autoReconnectAttempts,
                    recoveryError = "Не удалось восстановить соединение с выбранным устройством после $MAX_AUTO_RECONNECTS попыток.",
                    message = "Автоподключение не удалось",
                )
            }
            return
        }
        autoReconnectAttempts++
        log("auto-reconnect-scheduled host=${address.safeId()} attempt=$autoReconnectAttempts/$MAX_AUTO_RECONNECTS")
        mutableState.update { it.copy(reconnectAttempt = autoReconnectAttempts, message = "Повторное подключение через ${AUTO_RECONNECT_DELAY_MS / 1_000} с…") }
        reconnectJob = scope.launch {
            delay(AUTO_RECONNECT_DELAY_MS * autoReconnectAttempts)
            reconnectJob = null
            startConnect(address, automatic = true)
        }
    }

    private fun connectionFailure(message: String, automatic: Boolean) {
        log("connection-failure automatic=$automatic reason=$message")
        mutableState.update {
            it.copy(
                connecting = false,
                sending = false,
                waitingForFreshCode = false,
                sendingAccountId = null,
                message = message,
            )
        }
        if (automatic && autoReconnectAttempts < MAX_AUTO_RECONNECTS) {
            scheduleAutoReconnect(state.value.selectedAddress ?: return)
        } else {
            mutableState.update { it.copy(recoveryError = message) }
        }
    }

    private fun reconnectLastHostAfterActivation() {
        if (!reconnectWhenReady || !state.value.registered || state.value.connecting) return
        reconnectWhenReady = false
        val address = preferences.getString(LAST_HOST_KEY, null) ?: return
        log("activation-reconnect host=${address.safeId()}")
        val host = bondedDevices().firstOrNull { it.address == address }
        if (host == null) {
            mutableState.update { it.copy(recoveryError = "Последнее устройство больше не сопряжено", message = "Выберите новый host") }
            return
        }
        if (resolvedConnectionState(host) == BluetoothProfile.STATE_CONNECTED) {
            onConnected(host)
        } else {
            autoReconnectAttempts = 1
            startConnect(address, automatic = true)
        }
    }

    private fun sendReports(
        reports: List<KeyboardReport>,
        progressTotal: Int,
        startingMessage: String,
        successMessage: String,
    ) {
        if (state.value.sending) return
        val profile = hidDevice ?: return fail("HID profile недоступен")
        val device = selectedDevice() ?: return fail("Сначала выберите компьютер")
        if (!isHostConnected(device.address) || profile.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
            mutableState.update { it.copy(recoveryError = "Компьютер не подключён") }
            return fail("Компьютер не подключён")
        }

        val immutableReports = reports.map { it.copy(keyDown = it.keyDown.copyOf(), keyUp = it.keyUp.copyOf()) }
        log("send-start reports=${immutableReports.size}")
        mutableState.update {
            it.copy(
                sending = true,
                waitingForFreshCode = false,
                sendingAccountId = null,
                sentKeys = 0,
                sendKeysTotal = progressTotal,
                lastSendOutcome = HidSendOutcome.NONE,
                recoveryError = null,
                message = startingMessage,
            )
        }
        scope.launch {
            sendMutex.withLock {
                try {
                    transmitReports(profile, device, immutableReports, progressTotal)
                    log("send-success reports=${immutableReports.size}")
                    mutableState.update {
                        it.copy(
                            sending = false,
                            waitingForFreshCode = false,
                            sendingAccountId = null,
                            lastSendOutcome = HidSendOutcome.SUCCESS,
                            message = successMessage,
                        )
                    }
                } catch (error: Throwable) {
                    handleSendFailure(device, error, productionOtp = false)
                }
            }
        }
    }

    private suspend fun transmitReports(
        profile: BluetoothHidDevice,
        device: BluetoothDevice,
        reports: List<KeyboardReport>,
        progressTotal: Int,
    ) {
        reports.forEachIndexed { index, report ->
            if (!isHostConnected(device.address) || profile.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
                error("соединение потеряно на отчёте ${index + 1}")
            }
            if (!profile.sendReport(device, KeyboardReportEncoder.REPORT_ID, report.keyDown.copyOf())) {
                error("keyDown отчёта ${index + 1} отклонён")
            }
            delay(REPORT_DELAY_MS)
            if (!profile.sendReport(device, KeyboardReportEncoder.REPORT_ID, report.keyUp.copyOf())) {
                error("keyUp отчёта ${index + 1} отклонён")
            }
            delay(REPORT_DELAY_MS)
            mutableState.update {
                it.copy(sentKeys = ((index + 1) * progressTotal / reports.size).coerceAtMost(progressTotal))
            }
            log("send-report-complete index=${index + 1}/${reports.size}")
        }
    }

    private fun handleSendFailure(device: BluetoothDevice, error: Throwable, productionOtp: Boolean) {
        val message = "Ошибка HID: ${error.message ?: "неизвестная ошибка"}"
        log("send-failure reason=${error.message ?: "unknown"} production=$productionOtp")
        // A disconnect callback normally arrives before this path and may already
        // have started bounded recovery. Never retry an interrupted OTP payload:
        // reconnection only restores the transport, and the user starts a new send.
        mutableState.update {
            it.copy(
                sending = false,
                waitingForFreshCode = false,
                sendingAccountId = null,
                failedSendCount = it.failedSendCount + if (productionOtp) 1 else 0,
                lastSendOutcome = HidSendOutcome.FAILURE,
                recoveryError = null,
                message = "$message. Восстанавливаю соединение…",
            )
        }
        scheduleAutoReconnect(device.address)
    }

    private fun registerKeyboard() {
        val profile = hidDevice ?: return
        if (state.value.registered) return
        val settings = BluetoothHidDeviceAppSdpSettings(
            "OTPulse Keyboard",
            "OTPulse Bluetooth HID feasibility keyboard",
            "OTPulse",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            KEYBOARD_DESCRIPTOR,
        )
        val accepted = profile.registerApp(settings, null, null, appContext.mainExecutor, callback)
        log("register-app accepted=$accepted")
        if (!accepted) mutableState.update { it.copy(message = "Android отклонил регистрацию HID app") }
    }

    private fun startConnectionMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            var pendingAddress: String? = null
            var pendingSince = 0L
            while (isActive) {
                delay(MONITOR_INTERVAL_MS)
                val beforeRefresh = state.value
                val selected = beforeRefresh.selectedAddress ?: continue
                val wasConnected = beforeRefresh.hosts.firstOrNull { it.address == selected }?.connected == true
                refreshHosts()
                val current = state.value
                val device = bondedDevices().firstOrNull { it.address == selected } ?: continue
                val actual = hidDevice?.getConnectionState(device) ?: BluetoothProfile.STATE_DISCONNECTED
                if (current.connecting && actual != BluetoothProfile.STATE_CONNECTED) {
                    if (pendingAddress != selected) {
                        pendingAddress = selected
                        pendingSince = SystemClock.elapsedRealtime()
                    } else if (SystemClock.elapsedRealtime() - pendingSince >= CONNECTION_TIMEOUT_MS &&
                        connectionTimeoutJob?.isActive != true
                    ) {
                        log("monitor-connect-timeout host=${device.safeId()} actual=${actual.label()}")
                        hidDevice?.disconnect(device)
                        handleConnectionState(device, BluetoothProfile.STATE_DISCONNECTED)
                        pendingAddress = null
                        continue
                    }
                } else {
                    pendingAddress = null
                }
                if (wasConnected && actual == BluetoothProfile.STATE_DISCONNECTED) {
                    log("monitor-detected-disconnect host=${device.safeId()}")
                    handleConnectionState(device, BluetoothProfile.STATE_DISCONNECTED)
                } else if (current.connecting && actual == BluetoothProfile.STATE_CONNECTED) {
                    log("monitor-detected-connect host=${device.safeId()}")
                    handleConnectionState(device, BluetoothProfile.STATE_CONNECTED)
                }
            }
        }
    }

    private fun refreshHosts() {
        if (!hasBluetoothPermissions()) return
        val profile = hidDevice
        val now = SystemClock.elapsedRealtime()
        val devices = bondedDevices().filter { incompatibleHostReason(it) == null }.map { device ->
            val observed = synchronized(observedConnections) { observedConnections[device.address] }
            val actual = profile?.getConnectionState(device) ?: BluetoothProfile.STATE_DISCONNECTED
            val resolved = if (observed != null && now - observed.atMilliseconds <= CALLBACK_TRUST_MS) observed.state else actual
            HidHost(
                address = device.address,
                name = safeName(device),
                connected = resolved == BluetoothProfile.STATE_CONNECTED,
            )
        }.sortedWith(compareByDescending<HidHost> { it.connected }.thenBy { it.name.lowercase() })
        mutableState.update { current ->
            current.copy(
                hosts = devices,
                selectedAddress = current.selectedAddress?.takeIf { selected -> devices.any { it.address == selected } },
            )
        }
    }

    private fun resolvedConnectionState(device: BluetoothDevice): Int {
        val observed = synchronized(observedConnections) { observedConnections[device.address] }
        return if (observed != null && SystemClock.elapsedRealtime() - observed.atMilliseconds <= CALLBACK_TRUST_MS) {
            observed.state
        } else {
            hidDevice?.getConnectionState(device) ?: BluetoothProfile.STATE_DISCONNECTED
        }
    }

    private fun recordConnection(address: String, connectionState: Int) {
        synchronized(observedConnections) {
            observedConnections[address] = ObservedConnection(connectionState, SystemClock.elapsedRealtime())
        }
    }

    private fun setManualDisconnect(address: String, disconnected: Boolean) {
        synchronized(manuallyDisconnectedHosts) {
            if (disconnected) manuallyDisconnectedHosts += address else manuallyDisconnectedHosts -= address
        }
    }

    private fun isManuallyDisconnected(address: String): Boolean = synchronized(manuallyDisconnectedHosts) {
        address in manuallyDisconnectedHosts
    }

    private fun isHostConnected(address: String): Boolean = state.value.hosts.firstOrNull { it.address == address }?.connected == true

    private fun stopConnectionJobs() {
        connectionTimeoutJob?.cancel()
        reconnectJob?.cancel()
        connectionTimeoutJob = null
        reconnectJob = null
    }

    private fun bondedDevices(): Set<BluetoothDevice> = try {
        adapter?.bondedDevices.orEmpty()
    } catch (_: SecurityException) {
        emptySet()
    }

    private fun selectedDevice(): BluetoothDevice? = state.value.selectedAddress?.let { address ->
        bondedDevices().firstOrNull { it.address == address }
    }

    /**
     * Bluetooth HID Device connects to a host (a computer), not to another input,
     * audio, wearable, or phone device. Unknown classes remain visible because a
     * few desktop adapters do not advertise a reliable class of device.
     */
    private fun incompatibleHostReason(device: BluetoothDevice): String? {
        val major = try {
            device.bluetoothClass?.majorDeviceClass
        } catch (_: SecurityException) {
            null
        } ?: return null
        return when (major) {
            BluetoothClass.Device.Major.COMPUTER,
            BluetoothClass.Device.Major.UNCATEGORIZED -> null
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "Это аудиоустройство, оно не может принимать ввод Bluetooth-клавиатуры."
            BluetoothClass.Device.Major.PERIPHERAL -> "Это устройство ввода, а не компьютер для приёма клавиш."
            BluetoothClass.Device.Major.PHONE -> "Телефон не поддерживается как получатель Bluetooth-клавиатуры в этом тесте."
            else -> "Тип этого Bluetooth-устройства несовместим с режимом HID Keyboard."
        }
    }

    private fun safeName(device: BluetoothDevice): String = try {
        device.name ?: "Bluetooth ${device.address.takeLast(5)}"
    } catch (_: SecurityException) {
        "Bluetooth device"
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return REQUIRED_PERMISSIONS.all { appContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun timingStatistics(address: String): BluetoothTimingStatistics = BluetoothTimingStatistics(
        averageMilliseconds = preferences.getLong(
            "$TIMING_AVERAGE_KEY_PREFIX$address",
            DEFAULT_EXPECTED_BLUETOOTH_INPUT_MILLISECONDS,
        ).coerceIn(0L, MAX_RECORDED_BLUETOOTH_INPUT_MILLISECONDS),
        sampleCount = preferences.getInt("$TIMING_COUNT_KEY_PREFIX$address", 0).coerceIn(0, MAX_TIMING_SAMPLES),
    )

    private fun saveTimingStatistics(address: String, statistics: BluetoothTimingStatistics) {
        preferences.edit()
            .putLong("$TIMING_AVERAGE_KEY_PREFIX$address", statistics.averageMilliseconds)
            .putInt("$TIMING_COUNT_KEY_PREFIX$address", statistics.sampleCount)
            .apply()
    }

    private fun BluetoothOtpPreparationError.userMessage(): String = when (this) {
        BluetoothOtpPreparationError.SNAPSHOT_UNAVAILABLE -> "Не удалось получить действующий код"
        BluetoothOtpPreparationError.INVALID_CODE -> "Код содержит неподдерживаемые символы"
        BluetoothOtpPreparationError.STALE_SNAPSHOT -> "Время изменилось. Повторите отправку"
    }

    private fun fail(message: String) {
        mutableState.update {
            it.copy(
                sending = false,
                waitingForFreshCode = false,
                sendingAccountId = null,
                lastSendOutcome = HidSendOutcome.FAILURE,
                message = message,
            )
        }
    }

    private fun BluetoothDevice.safeId(): String = address.safeId()

    private fun String.safeId(): String = takeLast(5)

    private fun Int.label(): String = when (this) {
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        else -> "UNKNOWN($this)"
    }

    private fun log(message: String) {
        if (debugLoggingEnabled) Log.d(LOG_TAG, message)
    }

    companion object {
        const val TEST_DIGITS = "123456"
        const val DISCOVERABLE_SECONDS = 120
        val REQUIRED_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyArray()
        }

        @Volatile
        private var instance: AndroidBluetoothHidTestController? = null
        private val INSTANCE_LOCK = Any()

        fun getInstance(context: Context): AndroidBluetoothHidTestController = instance ?: synchronized(INSTANCE_LOCK) {
            instance ?: AndroidBluetoothHidTestController(context).also { instance = it }
        }

        private const val PREFERENCES_NAME = "otpulse_hid_test"
        private const val LOG_TAG = "OTPulseHID"
        private const val LAST_HOST_KEY = "last_host_address"
        private const val TIMING_AVERAGE_KEY_PREFIX = "timing_average_"
        private const val TIMING_COUNT_KEY_PREFIX = "timing_count_"
        // One key is a press + release pair. 12 ms between reports keeps a safe
        // interval for common desktop Bluetooth stacks while making 7 keys ~4x
        // faster than the deliberately conservative 45 ms feasibility setting.
        private const val REPORT_DELAY_MS = 12L
        private const val CONNECTION_TIMEOUT_MS = 10_000L
        private const val DISCONNECT_TIMEOUT_MS = 5_000L
        private const val AUTO_RECONNECT_DELAY_MS = 1_500L
        private const val MAX_AUTO_RECONNECTS = 3
        private const val MONITOR_INTERVAL_MS = 1_000L
        private const val CALLBACK_TRUST_MS = 2_000L

        private val KEYBOARD_DESCRIPTOR = byteArrayOf(
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), KeyboardReportEncoder.REPORT_ID.toByte(),
            0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00,
            0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02,
            0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x01,
            0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
            0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00,
            0x95.toByte(), 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01,
            0x29, 0x05, 0x91.toByte(), 0x02, 0x95.toByte(), 0x01, 0x75, 0x03,
            0x91.toByte(), 0x01, 0xC0.toByte(),
        )
    }
}
