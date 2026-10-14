import XCTest

final class PreviewFlowTests: XCTestCase {
    func testDraftAndHistoryNeverClaimTransmission() {
        let app = XCUIApplication()
        app.launch()
        openGroup(app)
        app.tabBars.buttons["Chat"].tap()
        let input = app.descendants(matching: .any)["demo-message-input"]
        input.tap()
        input.typeText("Hello trail")
        app.buttons["Save unsent draft"].tap()
        XCTAssertTrue(app.staticTexts["Not sent · demo only"].waitForExistence(timeout: 5))
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["Draft saved in memory"].waitForExistence(timeout: 5))
        keepScreenshot(app, name: "iPhone - History")
    }

    func testSosIsAnExplicitNonTransmittingPreview() {
        let app = XCUIApplication()
        app.launch()
        openGroup(app)
        app.swipeUp()
        app.buttons["Preview SOS"].tap()
        app.alerts.buttons["Open preview"].tap()
        XCTAssertTrue(app.staticTexts["No one has been alerted."].waitForExistence(timeout: 5))
        keepScreenshot(app, name: "iPhone - SOS preview")
        app.buttons["Close preview"].firstMatch.tap()
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["SOS preview closed"].waitForExistence(timeout: 5))
    }

    private func openGroup(_ app: XCUIApplication) {
        XCTAssertTrue(app.staticTexts["DEMO MODE · Bluetooth not connected"].waitForExistence(timeout: 10))
        app.buttons["Explore the demo"].tap()
        app.textFields["Your name"].tap()
        app.textFields["Your name"].typeText("Alex")
        app.textFields["Group name"].tap()
        app.textFields["Group name"].typeText("Trail")
        app.swipeUp()
        app.buttons["Open demo group"].tap()
        XCTAssertTrue(app.staticTexts["direct peers"].waitForExistence(timeout: 5))
        keepScreenshot(app, name: "iPhone - Group")
    }

    private func keepScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
