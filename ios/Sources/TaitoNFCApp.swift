import SwiftUI
import UIKit

@main
struct TaitoNFCApp: App {
  var body: some Scene {
    WindowGroup { ContentView() }
  }
}

struct ContentView: View {
  @StateObject private var writer = TagWriter()
  @AppStorage("linkURL") private var typed = "taitounited.fi"
  @State private var passphrase = ""
  @AppStorage("rememberPassphrase") private var remember = true
  @State private var confirmingProtect = false

  // The home screen name, from fi.lproj/InfoPlist.strings on a phone set to Finnish.
  private static let appName = Bundle.main.localizedInfoDictionary?["CFBundleDisplayName"] as? String
    ?? Bundle.main.infoDictionary?["CFBundleDisplayName"] as? String ?? "Taito NFC"

  private var content: Content? { Content(typed: typed) }

  private var contentFooter: String {
    guard let content else {
      return String(localized: "A phone that touches the tag opens a link, offers to call or email, or gets the text. The app keeps what you type on this phone.")
    }
    let size = content.message()?.length ?? 0
    return content.description + " " + Content.sizeNote(size)
  }

  // Saves the passphrase in this phone's keychain while Remember is on. It guards company tags at a
  // stand, not a bank, so having it ready beats hiding it from a phone its owner has unlocked.
  private func persist() { remember ? Keychain.save(passphrase) : Keychain.clear() }

  private func dismissKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
  }

  var body: some View {
    NavigationStack {
      List {
        if writer.isDemo {
          if writer.showsDemoNote {
            Section { Label("Demo: tag taps are simulated, no real tag.", systemImage: "play.rectangle").foregroundStyle(.orange) }
          }
        } else if !writer.available {
          Section { Text("This device cannot write NFC tags.").foregroundStyle(.red) }
        }
        Section {
          HStack {
            TextField("A link, text, phone number or email", text: $typed, axis: .vertical)
              .lineLimit(1...5)
              .textInputAutocapitalization(.never)
              .autocorrectionDisabled()
              .accessibilityIdentifier("tag-content")
            ClearButton(text: $typed).accessibilityIdentifier("clear-content")
          }
        } header: {
          Text("Content")
        } footer: {
          Text(contentFooter)
        }
        .disabled(!writer.available)
        if !writer.status.isEmpty {
          Section("Last result") { Text(writer.status) }
        }
        Section {
          HStack {
            TextField("Passphrase", text: $passphrase)
              .textInputAutocapitalization(.never)
              .autocorrectionDisabled()
              .accessibilityIdentifier("passphrase")
            ClearButton(text: $passphrase).accessibilityIdentifier("clear-passphrase")
          }
          Toggle("Remember on this phone", isOn: $remember)
        } header: {
          Text("Passphrase")
        } footer: {
          remember
            ? Text("Saved in this phone's keychain. Protecting a tag, removing its protection and rewriting a protected tag need the passphrase.")
            : Text("Protecting a tag, removing its protection and rewriting a protected tag need it. The app saves it only while Remember is on.")
        }
        // One row for both buttons: as two rows, the list drew a separator between them.
        Section {
          VStack(spacing: 12) {
            Button { confirmingProtect = true } label: {
              Text("Protect the tag").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(content == nil || passphrase.isEmpty)
            .accessibilityIdentifier("protect")
            Button { writer.start(.unprotect, passphrase: passphrase) } label: {
              Text("Remove the protection").frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(passphrase.isEmpty)
            .accessibilityIdentifier("unprotect")
          }
          .controlSize(.large)
          .padding(.vertical, 4)
        } header: {
          Text("Protect")
        } footer: {
          Text("Protect locks the tag so only this passphrase can rewrite it. Phones still read it. Remove the protection to make it writable again.")
        }
        .disabled(!writer.available)
      }
      .navigationTitle(Self.appName)
      .safeAreaInset(edge: .bottom) {
        Button {
          if let content { writer.start(.write(content), passphrase: passphrase) }
        } label: {
          Text("Write to the tag").frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .padding(.horizontal)
        .padding(.top, 8)
        .padding(.bottom, 4)
        .background(.bar)
        .disabled(content == nil || !writer.available)
        .accessibilityIdentifier("write")
      }
      .scrollDismissesKeyboard(.immediately)
      .toolbar {
        ToolbarItemGroup(placement: .keyboard) {
          Spacer()
          Button("Done") { dismissKeyboard() }.accessibilityIdentifier("keyboard-done")
        }
      }
      .confirmationDialog(
        "Remember the passphrase \"\(passphrase)\". Without it, nobody can change the tag again, you included.",
        isPresented: $confirmingProtect,
        titleVisibility: .visible
      ) {
        Button("Protect the tag") {
          if let content { writer.start(.protect(content), passphrase: passphrase) }
        }
        .accessibilityIdentifier("confirm-protect")
      }
      .sheet(item: $writer.demoTap) { tap in
        DemoTapView(tap: tap, showsNote: writer.showsDemoNote).presentationDetents([.height(320)])
      }
      .onAppear {
        if let saved = Keychain.load() { passphrase = saved }
      }
      .onChange(of: passphrase) { persist() }
      .onChange(of: remember) { persist() }
    }
  }
}

struct ClearButton: View {
  @Binding var text: String

  var body: some View {
    if !text.isEmpty {
      // Borderless, so a tap elsewhere in the list row does not clear the field.
      Button { text = "" } label: {
        Image(systemName: "xmark.circle.fill").foregroundStyle(Color(uiColor: .tertiaryLabel))
      }
      .buttonStyle(.borderless)
      .accessibilityLabel("Clear")
    }
  }
}

struct DemoTapView: View {
  let tap: DemoTap
  let showsNote: Bool

  var body: some View {
    VStack(spacing: 18) {
      if showsNote { Text("Simulated tag tap").font(.headline).foregroundStyle(.orange) }
      if let result = tap.result {
        Image(systemName: tap.ok ? "checkmark.circle.fill" : "xmark.circle.fill")
          .font(.system(size: 56)).foregroundStyle(tap.ok ? .green : .red)
        Text(result).multilineTextAlignment(.center)
      } else {
        Image(systemName: "wave.3.right.circle").font(.system(size: 56)).foregroundStyle(.blue)
        Text(tap.prompt).multilineTextAlignment(.center)
      }
    }
    .padding(28)
  }
}
