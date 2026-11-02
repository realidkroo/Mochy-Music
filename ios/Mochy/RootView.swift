import SwiftUI
import UIKit

// a placeholder screen to check the icon, the liquid glass and the ios version on a real phone
struct RootView: View {
    private let systemVersion = UIDevice.current.systemVersion

    var body: some View {
        ZStack {
            Color(red: 0.1, green: 0.1, blue: 0.1).ignoresSafeArea()

            VStack(spacing: 16) {
                Text("Mochy")
                    .font(.largeTitle.bold())
                Text("It works! The real app will grow from here.")
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                Text("Running on iOS \(systemVersion)")
                    .font(.footnote.monospacedDigit())
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .glassEffect()
            }
            .padding(24)
        }
        .preferredColorScheme(.dark)
    }
}
