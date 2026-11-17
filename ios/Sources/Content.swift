import CoreNFC
import Foundation

// What a tag holds: the one record a phone acts on when it touches the tag. A text record's
// language code is not part of it here, so a tag compares by its text alone. The rules follow
// Content.kt in the Android app.
enum Content: Equatable {
  case link(String)
  case text(String)

  private static let linkSchemes: Set<String> = ["mailto", "tel", "sms"]

  // A web address, with or without https://, an email address and a phone number become a link a
  // phone opens; anything else is text.
  init?(typed: String) {
    let text = typed.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !text.isEmpty else { return nil }
    if Self.matches(text, "[A-Za-z][A-Za-z0-9+.-]*:\\S+"), let colon = text.firstIndex(of: ":") {
      let scheme = text[..<colon].lowercased()
      let rest = text[text.index(after: colon)...]
      let slashes = rest.hasPrefix("//") && rest.count > 2
      if scheme == "http" || scheme == "https" {
        if slashes && Self.host(of: rest.dropFirst(2)).contains(".") { self = .link(text); return }
      } else if slashes || Self.linkSchemes.contains(scheme) {
        self = .link(text); return
      }
    }
    if Self.matches(text, "([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(:[0-9]+)?([/?#]\\S*)?") {
      self = .link("https://" + text)
    } else if Self.matches(text, "[^@\\s/:]+@([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}") {
      self = .link("mailto:" + text)
    } else if Self.matches(text, "[+0][0-9 ()-]+"), text.filter(\.isNumber).count >= 7 {
      self = .link("tel:" + text.filter { $0.isNumber || $0 == "+" })
    } else {
      self = .text(text)
    }
  }

  private static func matches(_ text: String, _ pattern: String) -> Bool {
    text.range(of: "^(?:\(pattern))$", options: .regularExpression) != nil
  }

  private static func host(of rest: Substring) -> Substring {
    var host = rest.prefix { !"/?#".contains($0) }
    if let at = host.lastIndex(of: "@") { host = host[host.index(after: at)...] }
    return host.prefix { $0 != ":" }
  }

  // The status byte holds the language code's length; bit 7 clear means UTF-8.
  func record(language: String) -> NFCNDEFPayload? {
    switch self {
    case .link(let uri):
      return NFCNDEFPayload.wellKnownTypeURIPayload(string: uri)
    case .text(let text):
      let lang = Data(language.utf8)
      return NFCNDEFPayload(
        format: .nfcWellKnown, type: Data("T".utf8), identifier: Data(),
        payload: Data([UInt8(lang.count)]) + lang + Data(text.utf8))
    }
  }

  func message(language: String = Content.language) -> NFCNDEFMessage? {
    record(language: language).map { NFCNDEFMessage(records: [$0]) }
  }

  static var language: String { Locale.current.language.languageCode?.identifier ?? "en" }

  func isHeld(in message: NFCNDEFMessage?) -> Bool {
    guard let record = message?.records.first, record.typeNameFormat == .nfcWellKnown else { return false }
    switch self {
    case .link(let uri):
      return record.wellKnownTypeURIPayload()?.absoluteString == URL(string: uri)?.absoluteString
    case .text(let text):
      return record.type == Data("T".utf8) && record.wellKnownTypeTextPayload().0 == text
    }
  }

  var description: String {
    switch self {
    case .text:
      return String(localized: "Writes this as text. A phone shows it only in an app that reads NFC tags.")
    case .link(let uri) where uri.hasPrefix("mailto:"):
      return String(localized: "Phones offer to email \(String(uri.dropFirst(7))).")
    case .link(let uri) where uri.hasPrefix("tel:"):
      return String(localized: "Phones offer to call \(String(uri.dropFirst(4))).")
    case .link(let uri):
      return String(localized: "Phones open \(uri).")
    }
  }

  // The data areas hold 144, 496 and 872 bytes. An NTAG213 keeps a 5-byte lock control TLV before
  // the message, and a message of 255 bytes or more takes a 4-byte TLV header, not 2.
  static func smallestTag(_ size: Int) -> Int? {
    switch size {
    case ...(144 - 5 - 2): return 213
    case ...(496 - 4): return 215
    case ...(872 - 4): return 216
    default: return nil
    }
  }

  static func sizeNote(_ size: Int) -> String {
    switch smallestTag(size) {
    case 213: return String(localized: "\(size) bytes, fits an NTAG213, 215 or 216.")
    case 215: return String(localized: "\(size) bytes, needs an NTAG215 or 216.")
    case 216: return String(localized: "\(size) bytes, needs an NTAG216.")
    default: return String(localized: "\(size) bytes, too long for an NTAG216, which takes 868.")
    }
  }
}
