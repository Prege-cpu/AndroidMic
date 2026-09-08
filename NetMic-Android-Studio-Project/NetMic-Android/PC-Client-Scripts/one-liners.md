# Comandi Rapidi per Ricevere Audio da NetMic

Il telefono trasmette **PCM grezzo little-endian a 16000 Hz, mono, 16-bit** sulla porta TCP 8888.

---

### 1. Con FFplay (Latenza bassissima ~40ms)
Richiede FFmpeg installato sul PC:

```bash
# Sostituisci 192.168.1.100 con l'IP mostrato nell'app NetMic
ffplay -nodisp -f s16le -ar 16000 -ac 1 -probesize 32 -analyzeduration 0 tcp://192.168.1.100:8888
```

---

### 2. Con VLC Media Player
Apri VLC -> Media -> Apri flusso di rete:
```
tcp://192.168.1.100:8888
```
oppure da riga di comando:
```bash
vlc --demux=rawaud --rawaud-channels=1 --rawaud-samplerate=16000 tcp://192.168.1.100:8888
```

---

### 3. Usare il Telefono come Microfono Reale in Discord / Zoom / Teams

#### Su Windows (con VB-Audio Virtual Cable):
1. Installa [VB-Cable (gratuito)](https://vb-audio.com/Cable/) che crea una periferica "CABLE Input"
2. Esegui lo script Python impostando CABLE Input come dispositivo di output:
   ```bash
   python netmic_receiver.py --ip 192.168.1.100 --device 3
   ```
3. In Discord / Teams / Windows Settings, seleziona **"CABLE Output"** come microfono!

#### Su Linux (con PipeWire / PulseAudio):
Crea un microfono virtuale in un secondo:
```bash
# 1. Carica il modulo sink virtuale
pactl load-module module-null-sink sink_name=NetMic sink_properties=device.description="NetMic_Virtual_Sink"

# 2. Reindirizza il flusso TCP al sink
nc 192.168.1.100 8888 | pacat --playback -d NetMic --rate=16000 --channels=1 --format=s16le --latency-msec=30

# 3. Nelle impostazioni audio del sistema seleziona "Monitor of NetMic_Virtual_Sink" come microfono di input!
```
