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

    // A vertical TextField shows up as a text view, so it is found by its identifier alone.
    let content = app.descendants(matching: .any).matching(identifier: "tag-content").firstMatch
    let clear = app.buttons["clear-content"]
    if clear.exists { clear.tap() }
    content.tap()
    content.typeText("https://t.me/addstickers/taitounited")
    app.buttons["keyboard-done"].firstMatch.tap()
    pause(1)

    app.buttons["write"].tap()
    pause(0.4)
    try shot("2-hold")
    pause(2.6)
    try shot("3-written")
    pause(3)

    type("kumpula26")
    pause(1)
    try shot("3b-ready")
    tap("protect")
    pause(1.5)
    try shot("4-confirm")
    app.buttons["confirm-protect"].firstMatch.tap()
    pause(3)
    try shot("5-protected")
    pause(2.6)

    type("wrong")
    pause(1)
    app.buttons["write"].tap()
    pause(3)
    try shot("6-wrong-passphrase")
    pause(2.6)

    type("kumpula26")
    pause(1)
    tap("unprotect")
    pause(3)
    try shot("7-removed")
    pause(2.6)
  }

  private func tap(_ id: String) {
    let button = app.buttons[id]
    for _ in 0..<4 where !(button.exists && button.isHittable) {
      app.swipeUp()
      pause(0.8)
    }
    button.firstMatch.tap()
  }

  private func type(_ text: String) {
    let field = app.textFields["passphrase"]
    field.tap()
    let old = field.value as? String ?? ""
    if old != field.placeholderValue {
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
