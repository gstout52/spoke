# Murmur

Voice dictation for Android that types clean text into any app. Tap a text field, tap the floating mic, talk, and tap again. Your words show up already tidied: filler words gone, punctuation fixed, and mid-sentence corrections applied.

It's an open-source take on the tap-and-talk experience of apps like Wispr Flow, using API keys you bring yourself.

## What it does

- **Floating mic everywhere.** A small button appears whenever the keyboard is up in a text field. Tap to start, tap to stop, long-press to cancel, drag to move it.
- **Accurate transcription.** Whisper via [Groq](https://console.groq.com) (free tier available) or OpenAI.
- **Cleanup that keeps your voice.** Claude Haiku removes "um / uh / like / you know", keeps only the final version when you correct yourself ("Tuesday, no, Wednesday"), fixes punctuation, and puts dictated lists on separate lines. It doesn't rewrite your wording, and it never answers your dictation as if it were a question.
- **Your vocabulary.** List names and jargon once and both steps spell them correctly.

## Install

1. Download `murmur.apk` from the [latest release](../../releases/latest) on your Android phone and open it. Allow your browser to install apps if asked.
2. Open Murmur and allow the microphone and notifications.
3. Tap **Turn on Murmur in Accessibility** and enable it. Android may call this a "restricted setting" for apps installed outside the Play Store. If so, tap **Open Murmur app info**, open the ⋮ menu, choose **Allow restricted settings**, and try again.
4. Paste your API keys:
   - Speech-to-text: a [Groq](https://console.groq.com) key (free tier) or an [OpenAI](https://platform.openai.com) key.
   - Cleanup (optional): an [Anthropic](https://console.anthropic.com) key. Without one, you get the raw transcript.
5. Try it in the test box at the bottom of the app.

Requires Android 10 or newer.

## Privacy

- Audio goes directly from your phone to the speech-to-text provider you choose. Transcripts go to Anthropic only if cleanup is on. There's no Murmur server, analytics or tracking.
- API keys are stored only on your phone, in app-private storage.
- The accessibility permission is used only to find the focused text field and type into it. Murmur doesn't read or store other screen content.
- It doesn't work in password fields, and it never appears in them.

## Costs

You pay the providers directly with your own keys. Groq's free tier covers typical personal use. Cleanup with Claude Haiku costs a fraction of a cent per dictation.

## Building

Every push to `main` builds a signed APK with GitHub Actions and publishes it as the latest release. Pull requests get a debug build with no secrets.

To build locally you need JDK 17 and the Android SDK:

```bash
gradle :app:assembleDebug
```

The code is small:

| File | Role |
|---|---|
| `DictationAccessibilityService.kt` | Shows the bubble, runs the dictation flow, and types the result into the field |
| `RecordingService.kt` | Microphone foreground service |
| `Transcriber.kt` | Whisper speech-to-text (Groq or OpenAI) |
| `Cleaner.kt` | Claude cleanup prompt and call |
| `MainActivity.kt` | Setup and settings screen |

## Contributing

Issues and pull requests are welcome. Because this app runs with accessibility access on people's phones, every change is reviewed by hand before merging. Changes that send data anywhere new, or read more of the screen than the focused text field, need a clear reason.

Please don't report security problems in public issues. Use GitHub's **Report a vulnerability** button under the Security tab instead.

## Driving

Murmur needs a tap to start and stop, so it isn't fully hands-free. Follow your local laws about using a phone while driving.

## License

[MIT](LICENSE)
