package com.otpulse.qr

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIApplication
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIViewController
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.sel_registerName

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosQrCodeScanner : QrCodeScanner {
    private var activeController: ScannerViewController? = null

    override fun scan(onResult: (QrScanResult) -> Unit) {
        if (activeController != null) return
        val presenter = topViewController()
        if (presenter == null) {
            onResult(QrScanResult.Failure("Не удалось открыть камеру"))
            return
        }
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> {
                onResult(QrScanResult.Failure("Разрешите доступ к камере в настройках iOS"))
            }
            AVAuthorizationStatusAuthorized -> present(presenter, onResult)
            else -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    if (granted) present(presenter, onResult)
                    else onResult(QrScanResult.Failure("Доступ к камере не предоставлен"))
                }
            }
        }
    }

    private fun present(presenter: UIViewController, onResult: (QrScanResult) -> Unit) {
        val controller = ScannerViewController { result ->
            activeController = null
            onResult(result)
        }
        activeController = controller
        presenter.presentViewController(controller, animated = true, completion = null)
    }

    private fun topViewController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class ScannerViewController(
    private val finish: (QrScanResult) -> Unit,
) : UIViewController(null, null), AVCaptureMetadataOutputObjectsDelegateProtocol, NSObjectProtocol {
    private val session = AVCaptureSession()
    private var finished = false

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device == null) {
            complete(QrScanResult.Failure("Камера недоступна"))
            return
        }
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null)
        val output = AVCaptureMetadataOutput()
        if (input == null || !session.canAddInput(input) || !session.canAddOutput(output)) {
            complete(QrScanResult.Failure("Не удалось настроить камеру"))
            return
        }
        session.addInput(input)
        session.addOutput(output)
        output.setMetadataObjectsDelegate(this, dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        val preview = AVCaptureVideoPreviewLayer.layerWithSession(session)
        preview.setVideoGravity("AVLayerVideoGravityResizeAspectFill")
        preview.setFrame(view.bounds)
        view.layer.addSublayer(preview)

        val cancel = UIButton.buttonWithType(UIButtonTypeSystem)
        cancel.setTitle("Отмена", forState = 0u)
        cancel.setTitleColor(UIColor.whiteColor, forState = 0u)
        cancel.setBackgroundColor(UIColor.colorWithWhite(0.0, alpha = 0.55))
        cancel.setFrame(CGRectMake(20.0, 54.0, 96.0, 44.0))
        cancel.addTarget(this, action = sel_registerName("cancelScan"), forControlEvents = platform.UIKit.UIControlEventTouchUpInside)
        view.addSubview(cancel)

        val manual = UIButton.buttonWithType(UIButtonTypeSystem)
        manual.setTitle("Ввести вручную", forState = 0u)
        manual.setTitleColor(UIColor.whiteColor, forState = 0u)
        manual.setBackgroundColor(UIColor.colorWithWhite(0.0, alpha = 0.55))
        manual.setFrame(CGRectMake(130.0, 54.0, 180.0, 44.0))
        manual.addTarget(this, action = sel_registerName("manualEntry"), forControlEvents = platform.UIKit.UIControlEventTouchUpInside)
        view.addSubview(manual)
        session.startRunning()
    }

    override fun captureOutput(
        output: platform.AVFoundation.AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: platform.AVFoundation.AVCaptureConnection,
    ) {
        val value = (didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject)?.stringValue
        if (!value.isNullOrBlank()) complete(QrScanResult.Success(value))
    }

    @kotlinx.cinterop.ObjCAction
    fun cancelScan() = complete(QrScanResult.Cancelled)

    @kotlinx.cinterop.ObjCAction
    fun manualEntry() = complete(QrScanResult.ManualRequested)

    private fun complete(result: QrScanResult) {
        if (finished) return
        finished = true
        session.stopRunning()
        dismissViewControllerAnimated(true) { finish(result) }
    }
}
