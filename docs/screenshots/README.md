# Native UI screenshots

Current captures exported unchanged from passing UI tests on 2026-10-15,
not generated mockups or proof of physical radio interoperability.

- Android: Medium Phone arm64 emulator, Android 16/API 36.1. `android-group.png`
  and `android-chat.png`, captured by `RadioFlowTest` in the normal native-radio
  app. No physical connected peer; the message is explicitly not sent.
- iPhone: iPhone 17 arm64 simulator, iOS 26.1. `ios-group.png`, `ios-history.png`
  and `ios-sos-unavailable.png`, from `RadioFlowTests`. Simulator explicitly has
  no physical Bluetooth. Result bundle: `Test-Murphy-2026.10.15_09-25-32--0300.xcresult`.

Normal apps now use KMP `RadioSession` with real native adapters. Zero connected
peers here is an actual empty state, not sample data. No ACK, encryption,
background reliability or physical accessibility certification is implied.

`ios-sos-preview.png` is retained as a **historical track-2 demo capture from
2026-10-14**, not a current screen. The current SOS button reports unavailable;
it does not open a simulated successful alert.
