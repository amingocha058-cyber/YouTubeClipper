# YouTube Clipper & Uploader

Android application (Kotlin + Jetpack Compose) for editing local videos and uploading the finished clip to the user's own YouTube channel.

## Implemented in the current development commit

- Select a local video through Android's Storage Access Framework.
- Read the selected video's duration.
- Set custom clip start/end times in seconds.
- Quick selection for the last 30 or 60 seconds.
- Export MP4 clips using AndroidX Media3 Transformer.
- Export a vertical 9:16 crop for Shorts or retain the source aspect ratio.
- Generate an editable suggested title from the file name and clip start time.
- Set YouTube visibility to Private, Unlisted, or Public.
- Share the exported MP4 through Android's secure FileProvider.
- Request the YouTube upload scope using Google Identity AuthorizationClient.
- Start a YouTube Data API v3 resumable upload session and upload the exported MP4.
- Build a debug APK using GitHub Actions.

## Technology

- Kotlin, Jetpack Compose, Android Storage Access Framework.
- AndroidX Media3 Transformer 1.11.1 for local video editing.
- Google Play services Identity AuthorizationClient for OAuth.
- YouTube Data API v3 for upload.
- GitHub Actions + Gradle 8.11.1 + JDK 17 for CI.

## Google Cloud setup required for YouTube upload

1. Create or select a project in Google Cloud Console.
2. Enable **YouTube Data API v3**.
3. Configure the OAuth consent screen and add your Google account as a test user while the app is in Testing.
4. Create an **Android OAuth client** using package name `dz.amingocha058.youtubeclipper` and the SHA-1 certificate fingerprint for the build you install.
5. Grant the app the YouTube upload scope: `https://www.googleapis.com/auth/youtube.upload`.
6. For public distribution, complete Google's applicable OAuth verification and YouTube API compliance requirements.

The app never asks for or collects a Google password. OAuth approval is performed by Google Play services. Do not commit private credentials, access tokens, refresh tokens, or signing keys to this repository.

## Build APK without Android Studio

1. Open the repository's **Actions** tab on GitHub.
2. Select **Android APK**.
3. If no run started automatically, press **Run workflow** on the `main` branch.
4. Open the completed run and download the `YouTubeClipper-debug` artifact.

The workflow uses Gradle 8.11.1 directly because this repository does not currently include a Gradle Wrapper.

## Current limitations / next engineering tasks

- Background blur, preview timeline, more title templates, and a robust chunked upload with retry/resume are not yet implemented.
- The current upload requires Google Cloud OAuth configuration and an internet connection.
- The app edits local files selected by the user. It does not download or extract arbitrary YouTube streams.
- Hardware codec support and export time vary by Android device and source video.
- A successful CI build is not a substitute for testing exports and uploads on a physical Android phone.

## Rights and safety

Only process and upload videos for which you have the necessary rights and permissions. YouTube may apply API quota, account, and content restrictions.
