# NetMic - Microfono di Rete TCP per Android 24/7

Trasforma uno smartphone Android (anche vecchio o riciclato) in un microfono da tavolo ad alta fedeltà per il tuo PC, sempre attivo 24 ore su 24.

---

## ⚡ NON HAI ANDROID STUDIO? COME OTTENERE L'APK SUBITO

### Metodo 1 (Consigliato, Zero Installazioni): Compilazione Cloud Gratuita con GitHub Actions
Questo progetto include già il file `.github/workflows/build-apk.yml`.
1. Crea un account gratuito su [GitHub](https://github.com) se non ne hai uno.
2. Crea un nuovo repository (può essere anche **Privato**).
3. Carica i file estratti da questo ZIP nel tuo repository su GitHub.
4. GitHub avvierà automaticamente la compilazione nel cloud (impiega circa 2 minuti).
5. Vai nella scheda **Actions** del repository -> clicca sull'ultimo run -> nella sezione **Artifacts** scarica **NetMic-Debug-APK**!
6. Installa il file `app-debug.apk` sul tuo telefono Android.

### Metodo 2: Compilazione da Terminale (senza installare l'intero Android Studio)
Se hai installato solo Java JDK (Java 17):
- **Su Windows**: esegui `gradlew.bat assembleDebug`
- **Su Mac / Linux**: esegui `./gradlew assembleDebug`
L'APK viene generato in: `app/build/outputs/apk/debug/app-debug.apk`

---

## Caratteristiche Tecniche
- **Audio Grezzo 16kHz PCM 16-bit Mono** con `AudioRecord` impostato su `MediaRecorder.AudioSource.VOICE_RECOGNITION`.
- **Foreground Service Continuo**: `foregroundServiceType="microphone"`, notifica a bassa priorità, sopravvive a standby e riavvii.
- **WAKELOCK & WifiLock**: Chip Wi-Fi sempre in `WIFI_MODE_FULL_HIGH_PERF` e `PARTIAL_WAKE_LOCK` per evitare sospensioni della CPU.
- **Riavvio Automatico**: `BootReceiver` intercetta `BOOT_COMPLETED` per riattivare lo streaming dopo un reboot o calo di corrente.
- **Interfaccia Jetpack Compose**: Mostra IP, porta, stato, uptime e VU meter RMS in tempo reale.
- **Multi-interfaccia**: Wi-Fi, Tethering USB (latenza ~15ms!) e Bluetooth.

## Collegamento al PC
Guarda la cartella `PC-Client-Scripts` per lo script Python o i comandi one-liner FFplay/VLC.
