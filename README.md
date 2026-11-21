# Discord Quests for Microcord

Browse quests, watch eligible videos, and claim rewards from a dedicated Quests screen in Microcord’s User Settings.

## What you can do

- Browse **Current** and **Claimed** quests. Quest cards show round icons, deadlines, rewards, requirements, and task progress.
- Tap **Accept quest** to join an available quest. Eligible mobile video quests play inside Microcord with play and pause controls, a progress bar, and elapsed and total time.
- Check your **Orbs** balance next to the Quests heading and see how many Orbs each eligible quest offers. When Discord provides the credited amount, the claim confirmation shows how many Orbs you earned.
- Claim completed rewards and open reward codes from the **Claimed** tab when available.

Complete desktop, streaming, and linked-game tasks on their supported platforms. Tap **Refresh** to see the progress Discord recorded.

## Install from GitHub Releases

1. Open the [latest GitHub release](https://github.com/mmaarij123/microcord-quests/releases/latest) and download **`microcord-quests.apk`** under **Assets**.
2. Install the APK on the Android device where Microcord is installed. If you have v2.3.0 or earlier, uninstall the old Discord Quests plugin first.
3. In **Microcord Settings → Plugins**, enable **Discord Quests** and approve it when prompted.
4. Open **User Settings** and tap the Quests laurel beside **Search**. You can also open **Plugins → Discord Quests → Settings → Quests**.

## Build from source

Install JDK 21 and Android SDK Platform 36.1. Clone this repository and run the Gradle wrapper from its root.

```bash
bash ./gradlew assembleDebug testDebugUnitTest lintDebug
```

On Windows PowerShell, run `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug`.

The APK is generated at `build/outputs/apk/debug/MicrocordQuestHome-debug.apk`. For a signed release build, copy [`keystore.properties.example`](keystore.properties.example) to `keystore.properties`, fill in your signing details, and run `assembleRelease` with the same wrapper.

The plugin is built with Kotlin and Java against Microcord Plugin API `1.0.6`. It uses Microcord’s signed-in REST client for quest requests and ExoPlayer for mobile video playback. The Android package is `io.github.mmaarij123.quests`.

## License

The original Discord Quests plugin code is licensed under MIT. Copyright © 2026 Maarij. You may use, copy, modify, and redistribute the code, including for commercial use. Include the copyright notice and permission notice with copies or substantial portions of the code. The code is provided as is, without warranty. The full terms include a limitation of liability. [Read the MIT license](LICENSE)

See the notices for the Discord Quests icon, the ExoPlayer HLS module included in the APK, and the upstream build tools. [Third-party notices](THIRD_PARTY_NOTICES.md)
