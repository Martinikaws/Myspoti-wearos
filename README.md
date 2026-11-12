# Myspoti for Wear OS

A Spotify-styled remote for Wear OS, built for the Galaxy Watch6 Classic. The watch shows what
is playing on your phone and controls it. Turning the bezel changes the volume.

## How it fits together

```
 Phone                                               Watch (Galaxy Watch6 Classic)
┌──────────────────────────────┐                    ┌───────────────────────────────┐
│ Spotify / patched Spotify    │                    │ Myspoti (wear module)         │
│   └─ publishes MediaSession  │                    │   NowPlayingScreen            │
│                              │   DataItem         │   ├─ art, title, artist       │
│ Myspoti Bridge (mobile)      │ ─ now_playing ───► │   ├─ progress ring            │
│   MediaBridgeService         │   + album art      │   └─ bezel → volume ring      │
│   (notification listener)    │                    │                               │
│   ├─ reads the session       │ ◄── Messages ───── │   PhoneLink                   │
│   └─ play/pause/skip/volume  │   cmd/play_pause…  │                               │
└──────────────────────────────┘                    └───────────────────────────────┘
                 Wearable Data Layer (Bluetooth / Wi-Fi, via Google Play services)
```

- **`shared/`**: the protocol (paths, commands, the `NowPlaying` model). Both apps use it, so
  the two sides cannot fall out of sync.
- **`mobile/`**: a small phone app, *Myspoti Bridge*. It finds the active media session
  (Spotify first, any player otherwise), sends its state to the watch and runs the watch's commands.
- **`wear/`**: the watch app (Compose for Wear OS).

### Why a bridge app instead of the Spotify SDK

The Spotify App Remote SDK only works with the official `com.spotify.music` package. It also
needs a developer client ID and stops working if a patch renames the package. Every player
publishes an Android MediaSession, so reading that works with stock Spotify, a Morphe-patched
Spotify and any other player, and it needs no Spotify account setup. The only permission is
**notification access**, because that is how Android lets an app control other apps'
media sessions. The bridge never reads your notifications.

### The bezel

On Wear OS the Watch6 Classic's rotating bezel produces the same rotary input as a
crown. The watch app reads it with `Modifier.onRotaryScrollEvent`: each detent is one volume
step, with a haptic tick, and the edge ring switches from track progress to volume while you
turn. To tune the sensitivity, change `PIXELS_PER_VOLUME_STEP` in `NowPlayingScreen.kt`.

Volume goes to the right place on its own: the phone's music stream when Spotify plays on
the phone, or the Connect device's volume when it plays on a speaker or PC.

## Running it in Android Studio

1. Open the project folder. Android Studio downloads the SDK and Gradle dependencies.
2. **Both apps must have the same `applicationId` and signing key**, or the Data Layer will not
   connect them. Both use `com.martinikaws.myspoti`, and debug builds from the same machine
   share a debug key, so this works out of the box.
3. Emulators: create a phone AVD (with Google Play) and a **Wear OS Large Round** AVD.
   Pair them with *Tools → Device Manager → (watch) ⋮ → Pair Wearable*.
   In the Wear emulator, use the crown control in the extended-controls panel or the mouse
   wheel to simulate the bezel.
4. Run the `mobile` configuration on the phone, open *Myspoti Bridge* and grant notification
   access. Start playing something in Spotify.
5. Run the `wear` configuration on the watch.

### On the real Galaxy Watch6 Classic

Turn on *Developer options → ADB debugging* and *Wireless debugging* on the watch, then pair it:
`adb pair <ip>:<port>` and `adb connect <ip>:<port>`. It then shows up as a run target. The
phone must be the one paired with the watch through the Galaxy Wearable app.

## Status / next steps

This is a first scaffold. It was written without building it, because this environment has no
Android SDK, so expect the first sync in Android Studio to need small fixes.

- [ ] First build and test on emulators
- [ ] Ambient (always-on) mode
- [ ] Like / add to library (needs a custom MediaSession action; Spotify exposes some)
- [ ] Library or queue browsing (needs the Spotify Web API, or `MediaBrowser` on the phone)
- [ ] Media tile and complication
- [ ] Ongoing activity, so the app is one tap from the watch face while music plays
