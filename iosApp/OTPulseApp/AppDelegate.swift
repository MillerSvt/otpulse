import ComposeApp
import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private let lifecycleController = AppLifecycleController()
    private var clockChangeObserver: NSObjectProtocol?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = MainViewControllerKt.MainViewController(
            lifecycleController: lifecycleController
        )
        window.makeKeyAndVisible()
        self.window = window
        clockChangeObserver = NotificationCenter.default.addObserver(
            forName: NSNotification.Name.NSSystemClockDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.lifecycleController.onSystemTimeChanged()
        }
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        lifecycleController.onForeground()
    }

    func applicationWillEnterForeground(_ application: UIApplication) {
        lifecycleController.onForeground()
    }

    func applicationWillResignActive(_ application: UIApplication) {
        lifecycleController.onBackground()
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        lifecycleController.onBackground()
    }

    deinit {
        if let clockChangeObserver {
            NotificationCenter.default.removeObserver(clockChangeObserver)
        }
    }
}
