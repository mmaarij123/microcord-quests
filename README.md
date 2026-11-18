# Discord Quests for Microcord

[Download the latest APK](https://github.com/mmaarij123/microcord-quests/releases/latest) · [Microcord](https://github.com/microcord/microcord) · [Plugin template](https://github.com/microcord/plugin-template)

Browse current quests, see requirements and server-recorded progress, accept a quest, play eligible mobile quest videos, claim completed rewards, and view claimed reward codes inside Microcord. The plugin uses the signed-in Microcord session and its existing verification flow.

The Quests header shows the account’s Orbs balance. Cards show their advertised Orbs reward, and claim confirmations show the credited amount when Discord supplies it. The native player prefers the smaller video and can switch to another available stream if playback stalls during startup.

Discord determines eligibility and tracks desktop play, desktop streaming, and linked-game tasks on their supported platforms. The Quests page displays that progress when you refresh. [Discord Quests FAQ](https://support.discord.com/hc/en-us/articles/22225719947543-Discord-Quests-FAQ)

## Install

1. Install the plugin APK on the same Android device as Microcord. It updates the earlier Quests plugin with the same package ID and signer.
2. Open **Microcord Settings → Plugins** and enable **Discord Quests**. Approve the plugin when prompted.
3. Open **User Settings** and tap the Quests laurel beside Search. You can also open **Plugins → Discord Quests → Settings → Quests**.

The toolbar uses the Discord Quests SVG in `assets/quests.svg`, packaged as an Android vector drawable.

## Build

Use JDK 21 and Android SDK Platform 36.1. From the project root, run `bash ./gradlew assembleDebug` (Windows PowerShell: `.\gradlew.bat assembleDebug`). Find the debug APK under `build/outputs/apk/debug/`.

The project uses Microcord Plugin API `1.0.6`. The optional `keystore.properties.example` documents the release-signing configuration.

Run `testDebugUnitTest lintDebug` to check the quest parser, request routing, and playback progress. The transport integration relies on the current Microcord build exposing its Retrofit-based REST service. Discord’s client Quest endpoints may change.

## License

Original plugin code is available under the [MIT License](LICENSE), copyright Maarij. Discord artwork, trademarks, Gradle Wrapper, and dependencies retain their applicable licenses. See [third-party notices](THIRD_PARTY_NOTICES.md).
