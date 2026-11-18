import Foundation

// Stores the passphrase on this phone when the user turns on Remember. The keychain, not
// UserDefaults, so it is not left in plain text among the app's files.
enum Keychain {
  private static let base: [String: Any] = [
    kSecClass as String: kSecClassGenericPassword,
    kSecAttrService as String: "fi.taitounited.nfc",
    kSecAttrAccount as String: "passphrase",
  ]

  static func load() -> String? {
    var query = base
    query[kSecReturnData as String] = true
    query[kSecMatchLimit as String] = kSecMatchLimitOne
    var item: CFTypeRef?
    guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
          let data = item as? Data,
          let text = String(data: data, encoding: .utf8) else {
      return nil
    }
    return text
  }

  static func save(_ passphrase: String) {
    let data = Data(passphrase.utf8)
    if SecItemUpdate(base as CFDictionary, [kSecValueData as String: data] as CFDictionary) == errSecItemNotFound {
      var item = base
      item[kSecValueData as String] = data
      item[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
      SecItemAdd(item as CFDictionary, nil)
    }
  }

  static func clear() {
    SecItemDelete(base as CFDictionary)
  }
}
