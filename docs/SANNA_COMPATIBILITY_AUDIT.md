# Sanna compatibility audit

Sanna is React Native/TypeScript with Porcupine and other native modules; these are not imported. Its README says MIT but canonical root LICENSE was not found, so no Sanna source is copied. Jarvis remains native Kotlin and Voicute-only.

This independent slice adds bounded app-specific generic guidance using Android SharedPreferences and a privacy filter. It rejects OTPs, credentials, phone/email, long numeric identifiers, personal messages, contacts, clipboard text and UI node/resource IDs. Only a successful generic flow may be recorded. Retrieval is not yet wired into AgentCore; that requires separate review so hints cannot override user intent or safety policy.