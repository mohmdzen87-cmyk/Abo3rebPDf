import base64
import os
import re
from typing import Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from google.cloud import texttospeech

app = FastAPI(title="Abo3reb PDF Speech API", version="1.0.0")
client = texttospeech.TextToSpeechClient()

VOICE_NAMES = {
    "male": "ar-XA-Chirp3-HD-Achird",
    "female": "ar-XA-Chirp3-HD-Achernar",
}
MAX_TEXT_CHARS = 4500


class SpeechRequest(BaseModel):
    text: str = Field(min_length=1, max_length=MAX_TEXT_CHARS)
    voice: Literal["male", "female"] = "male"


@app.get("/health")
def health():
    return {"ok": True, "service": "abo3reb-pdf-speech"}


@app.post("/v1/synthesize")
def synthesize(request: SpeechRequest):
    text = re.sub(r"[\t ]+", " ", request.text).strip()
    if not text:
        raise HTTPException(status_code=400, detail="Text is empty")

    try:
        response = client.synthesize_speech(
            input=texttospeech.SynthesisInput(text=text),
            voice=texttospeech.VoiceSelectionParams(
                language_code="ar-XA",
                name=VOICE_NAMES[request.voice],
            ),
            audio_config=texttospeech.AudioConfig(
                audio_encoding=texttospeech.AudioEncoding.MP3,
            ),
        )
    except Exception as exc:
        # Avoid returning provider credentials or internal exception details.
        raise HTTPException(status_code=502, detail="Speech provider request failed") from exc

    return {
        "audio_mime_type": "audio/mpeg",
        "audio_base64": base64.b64encode(response.audio_content).decode("ascii"),
        "voice": VOICE_NAMES[request.voice],
    }
