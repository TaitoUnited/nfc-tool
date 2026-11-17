import Foundation

// The -demo launch argument plays each tap on a pretend tag, so the app can be shown on a
// simulator, which has no NFC. The pretend tag follows the same rules and says the same things.
struct DemoTag {
  var url: URL?
  var passphrase: String?
}

struct DemoTap: Identifiable {
  let id = UUID()
  let prompt: String
  var result: String?
  var ok = true
}

extension TagWriter {
  func simulate(_ job: Job) {
    demoTap = DemoTap(prompt: Say.hold(job))
    DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
      let (ok, text) = self.demoOutcome(job)
      self.status = text
      self.demoTap?.result = text
      self.demoTap?.ok = ok
      DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { self.demoTap = nil }
    }
  }

  private func demoOutcome(_ job: Job) -> (Bool, String) {
    switch job {
    case .write(let pack):
      if let key = demoTag.passphrase {
        if passphrase.isEmpty { return (false, Say.needsPassphrase) }
        if passphrase != key { return (false, Say.wrongKeepsTag) }
      }
      demoTag.url = pack.url
      return (true, Say.wrote(pack))
    case .protect(let pack):
      if demoTag.passphrase != nil { return (false, Say.alreadyProtected) }
      guard demoTag.url == pack.url else { return (false, Say.notHolding(pack)) }
      demoTag.passphrase = passphrase
      return (true, Say.protected(pack))
    case .unprotect:
      guard let key = demoTag.passphrase else { return (false, Say.notProtected) }
      guard passphrase == key else { return (false, Say.wrongKeepsProtection) }
      demoTag.passphrase = nil
      return (true, Say.removed)
    }
  }
}
