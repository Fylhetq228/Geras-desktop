# GERAS.md — fork handoff (read this before CLAUDE.md)

Geras is a personal desktop fork of SimpMusic (GPL-3.0). `CLAUDE.md` below describes
upstream SimpMusic and stays accurate for the code base. **Where it conflicts, this file wins
for Geras work:**

- Reply to the owner in **Russian**, casual. Code, comments and commits stay in English.
- Builds are allowed and expected: build and run the desktop target after every change
  (`gradlew :desktopApp:run -PisFullBuild=false`). Upstream's "do not build" rule does not apply.
- One sub-item of the spec = one commit. Obvious default → take it and write the assumption
  in the commit message; ask only when a redo would be expensive.
- Design: show visual options (mockups) before building any new screen or animation; the
  owner picks.
- Keep fork changes isolated (`composeApp/.../geras/**`, small hooks in base files) so upstream
  stays mergeable. Do not rename `com.maxrave.*` packages. Keep LICENSE and upstream credit.
- Android part of the base stays building; new features target desktop (Windows first).

## Local setup (owner's PC, Windows 11)

- JDK 21 (Android Studio's `jbr` works: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`).
- `local.properties`: `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk` (AGP needs it even for desktop).
- libmpv once: `gradlew :composeApp:mpvSetupAll -PisFullBuild=false` (pinned prebuilt tarballs;
  `mpvSetupWindowsX64Ci` rebuilds from a dated shinchiro 7z that has been pulled — do not use it).
- Run: `gradlew :desktopApp:run -PisFullBuild=false`.
- Portable app image: `gradlew :desktopApp:createDistributable -PisFullBuild=false`
  → `desktopApp/build/compose/binaries/main/app/Geras/`.
- Data dir: `%USERPROFILE%\.geras` (logs in `.geras\logs`).
- YouTube needs a VPN on the owner's home network.
- CI: `.github/workflows/windows.yml` builds `Geras-windows.zip` on every push to `main` and
  publishes it to the rolling `latest` pre-release.

## Decisions already made

- Base: SimpMusic (Metrolist-KMP was alpha with no desktop when checked, 2026-11-12).
- Audio engine: keep upstream libmpv (already has EQ, rubberband pitch, reverb, 8D, crossfade,
  output devices). No engine swap.
- Name/id: **Geras**, `app.geras.desktop`, data dir `.geras`, warm terracotta "G" icon.
- url-schemes `simpmusic`/`wordbyword` kept (upstream OAuth apps); `geras://` to be added with
  single-instance link handling.
- Layout **B**: icon-rail sidebar (collapsible library), top search strip that is also the
  window caption, floating right panel (Lyrics / Queue / About, 360dp), persistent bottom bar.
- Windows caption: native frame kept, caption removed via WndProc subclass (`WindowsCaption`),
  so DWM shadow, Aero Snap and snap layouts stay native. AWT covers the client area with child
  HWNDs, so each child answers HTTRANSPARENT where the frame owns the point. Mica: later, opt-in.
- Fullscreen player **B**: everything centred, cover ↔ lyrics toggle, audio/video pill,
  up-next marquee, controls card, cover-palette background, shared-element artwork from the bar.
- Spotify: own dev app, OAuth PKCE for the user's OWN playlists; other people's playlists by
  link via the web-player token/embed (official API refuses their items since Feb 2026; max 5
  users, owner must have Premium). Playback is matched to YouTube Music.
- YouTube login on desktop: keep upstream's QR login-sync from phone + manual cookie + browser
  cookie import.
- Voice search: optional downloadable whisper.cpp model, last.
- Packaging: portable zip (CI) now; exe/msix installer at the end.

## Done (commits on `main`)

1. Rebrand (`rebrand:` commits) + `core/common/AppDirs.kt`.
2. Layout B shell — `geras/shell/GerasTopBar.kt`, `GerasPlayerBar.kt`, `GerasRightPanel.kt`.
3. Windows custom caption — `geras/window/WindowCaption.kt`, `jvmMain/.../WindowsCaption.kt`.
4. Fullscreen player B — `geras/shell/GerasFullscreenPlayer.kt`, `GerasShared.kt`.
5. Fixes from the first real run: cover palette fed from the artwork, caption child-HWND
   hit-test, lyrics panel inset, Ctrl+Q quits for real.

6. Animated lyrics, core — `geras/lyrics/GerasLyricsModel.kt`, `GerasLyricsLine.kt`,
   `GerasLyricsView.kt`. Fill style **C** (owner's pick): soft white wipe, glow + swell on held
   words. Drawn in the draw phase from one `TextLayoutResult` per line; ad-libs italic, dots in
   gaps, spring autoscroll (3.5 s pause after manual scroll), click-to-seek, speed-aware
   playhead, line translation. Line-synced sheets get word timings estimated by word length.
   Used by the right panel and the fullscreen player; upstream `LyricsView` is untouched.

Verified on the owner's PC: app starts, playback, fullscreen player, seek-bar hover time, main
shell; caption hit-testing probed with WM_NCHITTEST (strip = HTCAPTION, buttons = MIN/MAX/CLOSE,
search capsule = client); lyrics wipe on a line-synced track in the right panel at 135–144 fps.
**Not yet verified by eye**: caption-button hover, snap layouts flyout, palette background,
Ctrl+Q; lyrics on a RICH_SYNCED track (glow/swell, gap dots) and in the fullscreen player.

## Next, in order

1. Owner eyeballs the "not yet verified" list above.
2. Animated lyrics, remaining: F11 fullscreen + always-on-top karaoke window, share card PNG,
   translation toggle in the panel.
3. Sound: speed chip popover (0.5–2.0x step 0.01, pitch, linked/unlinked), presets
   Slowed+Reverb / Sped Up / Nightcore / Daycore, bass boost, EQ curve editor with per-device
   presets + A/B, per-track memory, exclusive WASAPI. Respect upstream's crossfade lock on
   speed/pitch.
4. Search: `MusicSource` interface, unified search with chips and source badges, keyboard
   navigation, layout/translit typo tolerance (ащку → face), paste-a-link, library tab, DnD.
5. SoundCloud via NewPipeExtractor. 6. Spotify. 7. Playlist transfer. 8. Desktop integration
   (taskbar thumbnail buttons, progress on icon, configurable global hotkeys, single instance
   with `geras://`). 9. Extras (A-B loop, gapless, smart shuffle). 10. Installer.

The original spec the owner wrote is in the Claude project "Плеер" chat history; this file is
the condensed version.
