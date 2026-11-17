import CoreNFC
import CryptoKit

struct Pack: Identifiable {
  let id: String
  let url: URL
}

let packs = [
  Pack(id: "Signal", url: URL(string: "https://signal.art/addstickers/#pack_id=1b601d8c2945a9e8dc10874112903887&pack_key=53b95b66cb5d040123a385cfb74cfd4ca308621ccf7574d93a2a3050a2b8e750")!),
  Pack(id: "Telegram", url: URL(string: "https://t.me/addstickers/taitounited")!),
]

enum Say {
  static func hold(_ job: TagWriter.Job) -> String {
    switch job {
    case .write(let pack): return "Hold the top of the iPhone on the \(pack.id) tag."
    case .protect(let pack): return "Hold the top of the iPhone on the \(pack.id) tag to protect it."
    case .unprotect: return "Hold the top of the iPhone on the tag to remove its protection."
    }
  }
  static func wrote(_ pack: Pack) -> String { "Wrote the \(pack.id) link. Test it before you protect the tag." }
  static func protected(_ pack: Pack) -> String { "Protected the \(pack.id) tag. Phones still read it." }
  static func notHolding(_ pack: Pack) -> String { "This tag does not hold the \(pack.id) link, so it stays unprotected." }
  static let removed = "Removed the protection. Anyone can rewrite the tag now."
  static let needsPassphrase = "This tag is protected. Type its passphrase first."
  static let wrongKeepsTag = "Wrong passphrase. The tag stays as it was."
  static let wrongKeepsProtection = "Wrong passphrase. The tag stays protected."
  static let alreadyProtected = "This tag is protected already."
  static let notProtected = "This tag has no protection to remove."
}

// The tag stores a 4-byte password and a 2-byte acknowledge. Mixing in the UID gives each tag its
// own password from the same passphrase, as NXP's data sheet advises.
struct Key {
  let pwd: [UInt8]
  let pack: [UInt8]

  init(passphrase: String, uid: Data) {
    let hash = Array(SHA256.hash(data: uid + Data(passphrase.utf8)))
    pwd = Array(hash[0..<4])
    pack = Array(hash[4..<6])
  }
}

// Commands and configuration pages from NXP's NTAG213/215/216 data sheet, rev. 3.2.
struct Ntag {
  let tag: NFCMiFareTag
  let config: UInt8

  var lastConfigPage: UInt8 { config + 3 }

  static func open(_ tag: NFCMiFareTag, done: @escaping (Ntag?) -> Void) {
    let configPage: [UInt8: UInt8] = [0x0F: 0x29, 0x11: 0x83, 0x13: 0xE3]
    tag.sendMiFareCommand(commandPacket: Data([0x60])) { version, error in
      guard error == nil, version.count == 8, version[2] == 0x04, let config = configPage[version[6]] else {
        return done(nil)
      }
      done(Ntag(tag: tag, config: config))
    }
  }

  func readConfig(done: @escaping ([UInt8]?) -> Void) {
    tag.sendMiFareCommand(commandPacket: Data([0x30, config])) { data, error in
      done(error == nil && data.count == 16 ? Array(data) : nil)
    }
  }

  func write(page: UInt8, _ bytes: [UInt8], done: @escaping (Bool) -> Void) {
    tag.sendMiFareCommand(commandPacket: Data([0xA2, page] + bytes)) { data, error in
      done(error == nil && (data.isEmpty || data.first == 0x0A))
    }
  }

  func authenticate(_ key: Key, done: @escaping (Bool) -> Void) {
    tag.sendMiFareCommand(commandPacket: Data([0x1B] + key.pwd)) { data, error in
      done(error == nil && Array(data) == key.pack)
    }
  }
}

final class TagWriter: NSObject, ObservableObject, NFCTagReaderSessionDelegate {
  enum Job { case write(Pack), protect(Pack), unprotect }

  @Published var status = ""
  @Published var demoTap: DemoTap?
  private(set) var passphrase = ""
  var demoTag = DemoTag()
  private var job: Job?
  private var session: NFCTagReaderSession?

  let isDemo = ProcessInfo.processInfo.arguments.contains("-demo")
  var available: Bool { isDemo || NFCTagReaderSession.readingAvailable }

  func start(_ job: Job, passphrase: String) {
    self.job = job
    self.passphrase = passphrase
    if isDemo { return simulate(job) }
    session = NFCTagReaderSession(pollingOption: .iso14443, delegate: self, queue: .main)
    session?.alertMessage = Say.hold(job)
    session?.begin()
  }

  func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

  func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
    if (error as? NFCReaderError)?.code != .readerSessionInvalidationErrorUserCanceled {
      status = error.localizedDescription
    }
  }

  func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
    guard let job else { return }
    guard tags.count == 1, let found = tags.first else {
      session.alertMessage = "More than one tag found. Hold just one."
      DispatchQueue.main.asyncAfter(deadline: .now() + 1) { session.restartPolling() }
      return
    }
    guard case .miFare(let tag) = found, tag.mifareFamily == .ultralight else {
      return fail(session, "This is not an NTAG213, 215 or 216 tag.")
    }
    session.connect(to: found) { error in
      if let error { return self.fail(session, error.localizedDescription) }
      Ntag.open(tag) { ntag in
        guard let ntag else { return self.fail(session, "This is not an NTAG213, 215 or 216 tag.") }
        ntag.readConfig { cfg in
          guard let cfg else { return self.fail(session, "Could not read the tag's settings.") }
          let isProtected = cfg[3] <= ntag.lastConfigPage
          let key = Key(passphrase: self.passphrase, uid: tag.identifier)
          switch job {
          case .write(let pack):
            self.write(pack, to: ntag, key: isProtected ? key : nil, in: session)
          case .protect(let pack):
            if isProtected { return self.fail(session, Say.alreadyProtected) }
            self.protect(ntag, holding: pack, key: key, cfg: cfg, in: session)
          case .unprotect:
            if !isProtected { return self.fail(session, Say.notProtected) }
            self.unprotect(ntag, key: key, cfg: cfg, in: session)
          }
        }
      }
    }
  }

  private func write(_ pack: Pack, to ntag: Ntag, key: Key?, in session: NFCTagReaderSession) {
    let message = NFCNDEFMessage(records: [NFCNDEFPayload.wellKnownTypeURIPayload(url: pack.url)!])
    let tag = ntag.tag
    let writeLink = {
      tag.queryNDEFStatus { ndef, capacity, error in
        if let error { return self.fail(session, error.localizedDescription) }
        guard ndef != .notSupported else { return self.fail(session, "This tag cannot hold a link.") }
        guard ndef != .readOnly else { return self.fail(session, "This tag is locked for good.") }
        guard message.length <= capacity else {
          return self.fail(session, "The link takes \(message.length) bytes, the tag holds \(capacity).")
        }
        tag.writeNDEF(message) { error in
          if let error { return self.fail(session, error.localizedDescription) }
          self.succeed(session, Say.wrote(pack))
        }
      }
    }
    guard let key else { return writeLink() }
    guard !passphrase.isEmpty else { return fail(session, Say.needsPassphrase) }
    ntag.authenticate(key) { ok in
      ok ? writeLink() : self.fail(session, Say.wrongKeepsTag)
    }
  }

  // Protection goes only on a tag that holds the right link. AUTH0 goes last, as the data sheet
  // advises, and AUTHLIM stays 0: with a limit, wrong guesses would freeze the tag for good.
  private func protect(_ ntag: Ntag, holding pack: Pack, key: Key, cfg: [UInt8], in session: NFCTagReaderSession) {
    ntag.tag.readNDEF { message, _ in
      let url = message?.records.first?.wellKnownTypeURIPayload()
      guard url?.absoluteString == pack.url.absoluteString else {
        return self.fail(session, Say.notHolding(pack))
      }
      let access = cfg[4] & 0x58
      ntag.write(page: ntag.config + 2, key.pwd) { ok in
        guard ok else { return self.fail(session, "Could not set the password.") }
        ntag.write(page: ntag.config + 3, key.pack + [0, 0]) { ok in
          guard ok else { return self.fail(session, "Could not set the password.") }
          ntag.write(page: ntag.config + 1, [access, cfg[5], cfg[6], cfg[7]]) { ok in
            guard ok else { return self.fail(session, "Could not set the protection.") }
            ntag.write(page: ntag.config, [cfg[0], cfg[1], cfg[2], 0x00]) { ok in
              guard ok else { return self.fail(session, "Could not turn the protection on.") }
              ntag.readConfig { check in
                guard check?[3] == 0x00 else { return self.fail(session, "The protection did not take.") }
                self.succeed(session, Say.protected(pack))
              }
            }
          }
        }
      }
    }
  }

  private func unprotect(_ ntag: Ntag, key: Key, cfg: [UInt8], in session: NFCTagReaderSession) {
    ntag.authenticate(key) { ok in
      guard ok else { return self.fail(session, Say.wrongKeepsProtection) }
      ntag.write(page: ntag.config, [cfg[0], cfg[1], cfg[2], 0xFF]) { ok in
        guard ok else { return self.fail(session, "Could not remove the protection.") }
        ntag.readConfig { check in
          guard check?[3] == 0xFF else { return self.fail(session, "The protection is still on.") }
          self.succeed(session, Say.removed)
        }
      }
    }
  }

  private func succeed(_ session: NFCTagReaderSession, _ text: String) {
    status = text
    session.alertMessage = text
    session.invalidate()
  }

  private func fail(_ session: NFCTagReaderSession, _ text: String) {
    status = text
    session.invalidate(errorMessage: text)
  }
}
