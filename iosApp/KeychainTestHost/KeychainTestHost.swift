import ComposeApp
import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let status = IosPersistenceKt.runIosKeychainSelfTest()
        let value = status == 0 ? "PASS" : "FAIL:\(status)"
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        try? value.write(
            to: documents.appendingPathComponent("keychain-self-test.txt"),
            atomically: true,
            encoding: .utf8
        )

        let window = UIWindow(frame: UIScreen.main.bounds)
        let controller = UIViewController()
        controller.view.backgroundColor = .systemBackground
        let label = UILabel(frame: controller.view.bounds)
        label.text = "OTPulse Keychain: \(value)"
        label.textAlignment = .center
        label.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        controller.view.addSubview(label)
        window.rootViewController = controller
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
