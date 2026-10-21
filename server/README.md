# Abo3reb PDF — Cloud speech backend

This service generates Modern Standard Arabic audio using Google Cloud Text-to-Speech Chirp 3 HD. It is designed for Cloud Run and uses Application Default Credentials; **never put a service-account key in the Android APK or commit one to GitHub**.

## API

- `GET /health`
- `POST /v1/synthesize` with JSON `{"text":"النص المراد قراءته","voice":"male"}`. Set `voice` to `female` to use the female voice. Response contains `audio_mime_type` and Base64 MP3 in `audio_base64`.
- Each request is limited to 4,500 characters so the Android client can split long PDF text into sensible chunks.

## Cloud Run deployment checklist

1. Create/select a Google Cloud project with billing configured by the account owner, enable the Cloud Text-to-Speech API and Cloud Run.
2. Deploy this directory to Cloud Run using a dedicated runtime service account that has only the permissions needed to call Text-to-Speech. Prefer the service account attached to the service; do not upload a JSON key.
3. Before making the endpoint available to app users, put authentication and abuse protection in front of synthesis (for example, an authenticated gateway with per-user quotas/rate limits). Do not treat an embedded app-wide secret as secure.
4. Configure the Android app with the deployed HTTPS endpoint, then test male/female samples, long documents, network failures, and costs/quotas before enabling cloud playback by default.

## Local development

Use Google Cloud Application Default Credentials in your development environment, then run `pip install -r requirements.txt` and `uvicorn main:app --reload`. Do not commit credential files.

Chirp 3 HD Arabic uses locale `ar-XA` and supports the `Achird` male and `Achernar` female voices. Voice quality should be judged with real Arabic PDF samples before choosing the default.
