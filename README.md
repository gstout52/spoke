# Murmur

A personal Wispr Flow replacement for Android. Tap any text field and a floating mic appears. Tap it, talk, tap again, and your cleaned-up words are typed into the field.

- **Speech-to-text:** Whisper in the cloud via Groq (free tier) or OpenAI.
- **Cleanup:** Claude Haiku 4.5 removes filler words, applies self-corrections ("Tuesday, no, Wednesday"), fixes punctuation, and formats lists.
- **Vocabulary:** names and jargon you list are passed to both steps so they're spelled right.

## Install / update

Every push to `main` builds a signed APK and publishes it as the latest GitHub release. On the phone, open the repo's **Releases** page (signed in to GitHub), download `murmur.apk`, and install it. New builds install over old ones and keep your settings.

The signing key lives in `~/.murmur/` on Greg's Mac and in the repo secrets `MURMUR_KEYSTORE_B64` / `MURMUR_KEYSTORE_PASSWORD`. If the key is lost, future builds must be installed fresh (uninstall first).

## First-run setup on the phone

1. Open Murmur, allow the microphone and notifications.
2. Tap **Turn on Murmur in Accessibility**, then find Murmur under Installed apps and turn it on. If Samsung says it's a restricted setting, use **Open Murmur app info** → ⋮ → **Allow restricted settings**, then retry.
3. Paste your Groq (or OpenAI) API key and your Anthropic API key, then Save.
4. Try it in the test box at the bottom.

## Using it

- Tap: start / stop dictation.
- Long-press while recording: cancel.
- Drag: move the bubble (position is remembered relative to the keyboard).
