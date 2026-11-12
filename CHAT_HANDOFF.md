# Handoff: Myspoti/Orpheon for Wear OS (continue from here)

Paste this into a new chat to pick up where the last one stopped.

## Goal

Build a Wear OS version of Myspoti (now named **Orpheon**) for a **Galaxy Watch6 Classic**:
- Spotify-style look (near-black, green `#1DB954` accent, grey secondary text).
- The **rotating bezel** controls volume.
- Phone and watch talk to each other.
- Later: continue in Claude Code with **Android Studio** to run it on emulators and the real watch.

## Repositories

- **This project:** `Martinikaws/Myspoti-wearos`, branch `ccr-ba68cb27-flqqkm` (one commit, pushed).
  It is the only branch in the repo, so no pull request was opened (nothing to merge into).
- **The app it's based on:** `kota-zo/Orpheon` (formerly Myspoti, renamed at 1.0.0). GPL-3.0.
  Native Android client for Spotify with Jetpack Compose. Package `app.orpheon` (beta `app.orpheon.beta`).
- **Related, mine:** `Martinikaws/spotify-merged-patches`: Morphe patch bundle for the official
  Spotify app (9.1.80.2221, ARM64). Not the same thing as Orpheon.

## What was built in Myspoti-wearos (not compiled yet)

Written without an Android SDK, so the first Gradle sync in Android Studio will likely need small fixes.

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, Gradle wrapper 8.14.3
  AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, Wear Compose 1.4.0, play-services-wearable 19.0.0
shared/  Protocol.kt: Data Layer paths, Command enum, NowPlaying model (DataMap read/write)
mobile/  "Myspoti Bridge" phone app
  MediaBridgeService.kt: NotificationListenerService. Picks the active MediaSession
    (prefers packages containing "spotify", then whatever is playing), publishes NowPlaying +
    album art (320px JPEG Asset) as a DataItem at /myspoti/now_playing, and runs watch commands
    (play/pause, next, previous, seek, set volume). Volume goes to STREAM_MUSIC, or to
    MediaController.setVolumeTo when playback is REMOTE (Spotify Connect).
  MainActivity.kt: setup screen asking for notification access.
  res/values/wear.xml: capability "myspoti_phone_bridge".
wear/    Watch app
  PhoneLink.kt: listens for the DataItem, loads art, sends Messages to the phone node.
  PlayerViewModel.kt: optimistic play state; local volume held 1.2 s while the bezel turns.
  ui/NowPlayingScreen.kt: blurred art backdrop, green progress ring around the edge,
    title (marquee) + artist, prev / green play / next, volume button.
    Bezel via Modifier.onRotaryScrollEvent: one step per PIXELS_PER_VOLUME_STEP (40 px),
    haptic tick, ring switches to volume + percentage overlay for 1.5 s.
  ui/Theme.kt: Spotify-style palette.
README.md: architecture, Android Studio + emulator pairing, wireless ADB for the Watch6 Classic.
```

Both apps use `applicationId = "com.martinikaws.myspoti"`. The Data Layer only links apps with
the **same application ID and signing key**.

Why a MediaSession bridge instead of the Spotify App Remote SDK: the SDK only works with the
official `com.spotify.music`, needs a client ID, and breaks if a patch renames the package.
MediaSession works with stock Spotify, patched Spotify, and likely Orpheon (it supports Android Auto).

## What Orpheon already has (from reading kota-zo/Orpheon)

Orpheon already ships a Wear OS app: module `base/wear` (~1,300 lines, package `app.myspoti.wear`).
- It's a **standalone player on the watch** (own engine + ExoPlayer, watch speaker or earbuds), not a remote.
- The phone app (`base/app/.../wear/WearLinkService.kt`) gives the watch: session/login
  (`/orpheon/session`), playlists (`/orpheon/library`), songs (`/orpheon/tracks`),
  extensions (`/orpheon/extensions`), listen reports (`/orpheon/listen`). Falls back to Wi-Fi (`pass` module).
- Bezel/crown volume is already done with Horologist (`volumeRotaryBehavior`, `VolumeLevelIndicator`).
- Uses Wear Compose Material 3 1.7.1, Horologist 0.8.4-alpha, compileSdk 37.
- **Missing:** controlling music that's already playing on the phone (remote mode).

**License:** GPL-3.0. Copying or adapting Orpheon code into a distributed project requires crediting
Myspoti/Orpheon (https://github.com/kota-zo/myspoti) and releasing that project under GPL-3.0 with
full source. The code in Myspoti-wearos was written independently, before reading Orpheon.

## Open decision

1. **Contribute to Orpheon:** add a "control the phone" remote mode to its watch app (GPL work).
2. **Keep Myspoti-wearos separate** (recommended for now): independent remote; its bridge should
   control Orpheon as well as Spotify.
3. **Copy Orpheon's watch app here:** needs GPL-3.0 + credit, and it depends on Orpheon's
   engine, extensions and pass modules.

## Next steps (whichever way)

- [ ] Open in Android Studio, fix the first sync/build errors.
- [ ] Pair a phone AVD (with Google Play) and a Wear OS Large Round AVD; test the bridge.
- [ ] Test on the real Watch6 Classic (wireless debugging); tune `PIXELS_PER_VOLUME_STEP`.
- [ ] Ambient mode, like button, library browsing, media tile, ongoing activity.
