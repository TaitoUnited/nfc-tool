import XCTest

// Walks the demo at a pace a viewer can follow and saves a screenshot at each step into
// DEMO_OUT, passed as TEST_RUNNER_DEMO_OUT to xcodebuild. A pretend tap shows its prompt for
// 2.5 s and its result for 2.5 s more.
final class DemoTests: XCTestCase {
  let out = URL(fileURLWithPath: ProcessInfo.processInfo.environment["DEMO_OUT"] ?? NSTemporaryDirectory())
  let app = XCUIApplication()

  func testDemo() throws {
    app.launchArguments = ["-demo"]
    app.launch()
    try mark("started")
    pause(2)
    try shot("1-start")

    app.buttons["Write the Signal link"].tap()
    pause(0.4)
    try shot("2-hold")
    pause(2.6)
    try shot("3-written")
    pause(3)

    type("kumpula26")
    pause(1)
    app.buttons["Protect the Signal tag"].firstMatch.tap()
    pause(1.5)
    try shot("4-confirm")
    app.buttons["confirm-protect"].firstMatch.tap()
    pause(3)
    try shot("5-protected")
    pause(2.6)

    type("wrong")
    pause(1)
    app.buttons["Write the Signal link"].tap()
    pause(3)
    try shot("6-wrong-passphrase")
    pause(2.6)

    type("kumpula26")
    pause(1)
    let remove = app.buttons["Remove the protection"]
    for _ in 0..<4 where !(remove.exists && remove.isHittable) {
      app.swipeUp()
      pause(0.8)
    }
    remove.tap()
    pause(3)
    try shot("7-removed")
    pause(2.6)
  }

  private func type(_ text: String) {
    let field = app.textFields["Passphrase"]
    field.tap()
    let old = field.value as? String ?? ""
    if old != "Passphrase" {
      field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: old.count))
    }
    field.typeText(text + "\n")
  }

  private func pause(_ seconds: TimeInterval) {
    Thread.sleep(forTimeInterval: seconds)
  }

  private func shot(_ name: String) throws {
    try XCUIScreen.main.screenshot().pngRepresentation.write(to: out.appendingPathComponent("\(name).png"))
  }

  private func mark(_ name: String) throws {
    try String(Date().timeIntervalSince1970).write(to: out.appendingPathComponent("\(name).txt"), atomically: true, encoding: .utf8)
  }
}
