# YouTube Clipper & Uploader

An Android application for preparing short video clips and uploading them to a YouTube channel.

## Planned features
- Select a local video using Android's document picker.
- Create a 30-second or 60-second clip, or choose custom start/end times.
- Export in the original 16:9 format or vertical 9:16 Shorts format.
- Choose YouTube visibility: Private, Unlisted, or Public.
- Connect a YouTube account using Google OAuth 2.0 (never collect a Google password).
- Upload videos using the YouTube Data API resumable-upload flow.
- Share exported clips and copy their titles/paths.

## Technology
- Kotlin
- Jetpack Compose
- Android Storage Access Framework
- YouTube Data API v3 + OAuth 2.0
- Video-processing engine: to be selected and validated before integration, including licensing and Android compatibility.

## Project status
The repository currently contains the initial Android application foundation and UI prototype. Video processing, OAuth, and upload are not implemented yet.

## Build
Open this repository in the latest stable Android Studio, allow Gradle sync, then run the `app` configuration on an Android device (or emulator).

## Security and compliance
- OAuth is used for Google account authorization. The app must never ask users to type their Google password into the app.
- API credentials and signing secrets must not be committed to the repository.
- Users must have the rights and permissions required to process and upload their videos.
