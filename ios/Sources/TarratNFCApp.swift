import SwiftUI

@main
struct TarratNFCApp: App {
  var body: some Scene {
    WindowGroup { ContentView() }
  }
}

struct ContentView: View {
  @StateObject private var writer = TagWriter()
  @State private var passphrase = ""
  @State private var protectTarget: Pack?

  var body: some View {
    NavigationStack {
      List {
        if writer.isDemo {
          Section { Label("Demo: tag taps are simulated, no real tag.", systemImage: "play.rectangle").foregroundStyle(.orange) }
        } else if !writer.available {
          Section { Text("This device cannot write NFC tags.").foregroundStyle(.red) }
        }
        Section {
          TextField("Passphrase", text: $passphrase)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
        } footer: {
          Text("Protecting a tag, removing its protection and rewriting a protected tag need it. Phones read a protected tag as usual. The app does not save it.")
        }
        if !writer.status.isEmpty {
          Section("Last result") { Text(writer.status) }
        }
        ForEach(packs) { pack in
          Section(pack.id) {
            Text(pack.url.absoluteString).font(.caption).foregroundStyle(.secondary).lineLimit(2)
            Button("Write the \(pack.id) link") { writer.start(.write(pack), passphrase: passphrase) }
            Button("Protect the \(pack.id) tag") { protectTarget = pack }
              .disabled(passphrase.isEmpty)
          }
          .disabled(!writer.available)
        }
        Section("Any tag") {
          Button("Remove the protection") { writer.start(.unprotect, passphrase: passphrase) }
            .disabled(passphrase.isEmpty)
        }
        .disabled(!writer.available)
      }
      .navigationTitle("Tarrat NFC")
      .confirmationDialog(
        "Remember the passphrase \"\(passphrase)\". Without it, nobody can change the tag again, you included.",
        isPresented: Binding(get: { protectTarget != nil }, set: { if !$0 { protectTarget = nil } }),
        titleVisibility: .visible,
        presenting: protectTarget
      ) { pack in
        Button("Protect the \(pack.id) tag") { writer.start(.protect(pack), passphrase: passphrase) }
          .accessibilityIdentifier("confirm-protect")
      }
      .sheet(item: $writer.demoTap) { tap in
        DemoTapView(tap: tap).presentationDetents([.height(320)])
      }
    }
  }
}

struct DemoTapView: View {
  let tap: DemoTap

  var body: some View {
    VStack(spacing: 18) {
      Text("Simulated tag tap").font(.headline).foregroundStyle(.orange)
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
