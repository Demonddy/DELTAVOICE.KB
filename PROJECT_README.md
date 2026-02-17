# Modern Android Keyboard App

A feature-rich Android keyboard (IME) application built with Kotlin, featuring voice input, text-to-speech, emoji support, and video recording capabilities.

## Features

### ✅ Implemented Features

1. **Voice-to-Text (Speech Recognition)**

   - Tap the microphone icon to start voice input
   - Uses Android `SpeechRecognizer` API
   - Converts speech to text and inserts it into the input field
   - Visual feedback with active microphone icon

2. **Text-to-Speech (TTS)**

   - Tap the speaker icon to read aloud the current input text
   - Uses Android `TextToSpeech` API
   - Supports system default language

3. **Emoji Grid**

   - Tappable grid of 12 emojis (expandable)
   - 6x2 grid layout
   - One-tap emoji insertion into input field

4. **Video Recording**

   - Button launches video recording activity
   - Records up to 15 seconds of video
   - Saves video to device storage
   - Returns video URI (can be extended for integration)

5. **Mode Toggle**

   - Switch between Voice Input Mode and Text-to-Speech Mode
   - Visual indicator shows current mode
   - Buttons are context-aware based on mode

6. **Permissions Handling**
   - Separate activity for requesting permissions
   - Handles microphone and camera permissions
   - Clear status display

## Project Structure

```
app/
├── src/
│   └── main/
│       ├── java/com/keyboard/app/
│       │   ├── MainKeyboardService.kt      # Main IME service
│       │   ├── PermissionsActivity.kt      # Permissions handler
│       │   └── VideoRecordingActivity.kt   # Video recording
│       ├── res/
│       │   ├── layout/
│       │   │   ├── keyboard_layout.xml
│       │   │   ├── activity_permissions.xml
│       │   │   └── activity_video_recording.xml
│       │   ├── drawable/                   # Vector icons
│       │   ├── xml/
│       │   │   └── method.xml              # IME configuration
│       │   └── values/
│       │       └── strings.xml
│       └── AndroidManifest.xml
└── build.gradle
```

## Setup Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 21+ (Android 5.0 Lollipop)
- Kotlin 1.9.0+

### Installation

1. **Open in Android Studio**

   ```bash
   # Clone or download the project
   # Open Android Studio
   # File > Open > Select the project directory
   ```

2. **Sync Gradle**

   - Android Studio will automatically sync Gradle dependencies
   - Wait for sync to complete

3. **Build the Project**

   - Build > Make Project (Ctrl+F9 / Cmd+F9)
   - Ensure there are no build errors

4. **Install on Device/Emulator**

   - Connect an Android device or start an emulator
   - Run > Run 'app' (Shift+F10 / Ctrl+R)

5. **Enable the Keyboard**
   - Go to Settings > System > Languages & Input > Virtual Keyboard
   - Select "Modern Keyboard"
   - Enable it in your keyboard settings
   - Switch to it when typing (long-press spacebar or use keyboard switcher)

## Usage

### Voice Input

1. Tap the microphone icon
2. Speak your message
3. Text will be automatically inserted

### Text-to-Speech

1. Switch to TTS mode using the mode toggle button
2. Type or have text in the input field
3. Tap the speaker icon to hear the text read aloud

### Emoji Insertion

1. Tap any emoji in the grid
2. It will be inserted at the cursor position

### Video Recording

1. Tap the video icon
2. Grant camera permission if prompted
3. Tap "Start Recording"
4. Video will auto-stop after 15 seconds or tap "Stop Recording"
5. Video is saved to device storage

## Permissions

The app requires the following permissions:

- **RECORD_AUDIO**: For voice input functionality
- **CAMERA**: For video recording
- **WRITE_EXTERNAL_STORAGE**: For saving videos (Android 9 and below)
- **READ_EXTERNAL_STORAGE**: For accessing saved videos (Android 9 and below)
- **READ_MEDIA_VIDEO**: For accessing videos (Android 13+)

Permissions are requested through the `PermissionsActivity` when needed.

## Customization

### Adding More Emojis

Edit `MainKeyboardService.kt`:

```kotlin
private val emojis = listOf(
    "😀", "😂", "❤️", "👍", "🎉", "🔥",
    "😊", "😍", "🙏", "💯", "✨", "🌟",
    // Add more emojis here
    "🎮", "🚀", "💡"
)
```

### Changing Grid Layout

Modify the grid configuration in `setupEmojiGrid()`:

```kotlin
emojiGrid.columnCount = 6  // Change number of columns
emojiGrid.rowCount = 2     // Change number of rows
```

### Customizing Colors

Edit the vector drawable files in `res/drawable/` to change icon colors, or modify the layout XML files to change background colors.

## Technical Details

### IME Service

- Extends `InputMethodService`
- Handles input connection via `currentInputConnection`
- Uses `commitText()` to insert text/emojis

### Speech Recognition

- Uses `SpeechRecognizer` API
- Implements `RecognitionListener` for callbacks
- Handles various error states

### Text-to-Speech

- Uses `TextToSpeech` API
- Initializes with system default locale
- Supports queue management

### Video Recording

- Uses `MediaRecorder` API
- Records in MP4 format (H.264 codec)
- 1280x720 resolution
- 15-second maximum duration
- Saves to app-specific or public Movies directory

## Compatibility

- **Minimum SDK**: 21 (Android 5.0 Lollipop)
- **Target SDK**: 34 (Android 14)
- **Kotlin**: 1.9.0+

## Future Enhancements

Potential features to add:

- [ ] AI-powered text suggestions/corrections
- [ ] Custom emoji packs
- [ ] Theme customization
- [ ] Multiple language support
- [ ] Keyboard height adjustment
- [ ] Swipe gestures
- [ ] Clipboard history
- [ ] Video preview before saving
- [ ] Share video directly from keyboard

## Troubleshooting

### Keyboard Not Appearing

- Ensure the keyboard is enabled in system settings
- Check that the IME service is properly declared in AndroidManifest.xml
- Restart the device if needed

### Voice Input Not Working

- Check microphone permissions
- Ensure device has a working microphone
- Try restarting the app

### Video Recording Fails

- Grant camera and storage permissions
- Ensure device has sufficient storage space
- Check that camera is not being used by another app

## License

This project is provided as-is for educational and development purposes.

## Contributing

Feel free to submit issues, fork the repository, and create pull requests for any improvements.

---

**Note**: This keyboard app is ready to use in Android Studio. Simply open the project, sync Gradle, and run it on a device or emulator.
