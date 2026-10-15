import XCTest

final class RadioFlowTests: XCTestCase {
    func testNoPeerAndHistoryNeverClaimTransmission() {
        let app = XCUIApplication()
        app.launch()
        openGroup(app)
        app.tabBars.buttons["Chat"].tap()
        let input = app.descendants(matching: .any)["message-input"]
        input.tap()
        input.typeText("Hello trail")
        app.buttons["Send message"].tap()
        XCTAssertTrue(app.staticTexts["Not sent · no connected peers"].waitForExistence(timeout: 5))
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["Message not sent"].waitForExistence(timeout: 5))
        keepScreenshot(app, name: "iPhone - History")
    }

    func testSosIsUnavailableAndLeavingClosesGroup() {
        let app = XCUIApplication()
        app.launch()
        openGroup(app)
        app.swipeUp()
        app.buttons["SOS not available yet"].tap()
        XCTAssertTrue(app.alerts["SOS is not available yet"].waitForExistence(timeout: 5))
        keepScreenshot(app, name: "iPhone - SOS unavailable")
        app.alerts.buttons["Got it"].tap()
        app.buttons["Open settings"].tap()
        app.buttons["Leave group"].tap()
        app.buttons["Leave group"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Create group"].waitForExistence(timeout: 5))
    }

    private func openGroup(_ app: XCUIApplication) {
        XCTAssertTrue(app.buttons["Find nearby groups"].waitForExistence(timeout: 10))
        app.textFields["Your name"].tap()
        app.textFields["Your name"].typeText("Alex")
        app.textFields["Group name"].tap()
        app.textFields["Group name"].typeText("Trail")
        app.swipeUp()
        app.buttons["Create group"].tap()
        XCTAssertTrue(app.staticTexts["direct peers"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["No physical Bluetooth in Simulator · use an iPhone"].exists)
        keepScreenshot(app, name: "iPhone - Group")
    }

    private func keepScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
