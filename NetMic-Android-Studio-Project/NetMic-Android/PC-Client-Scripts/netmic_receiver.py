#!/usr/bin/env python3
"""
NetMic PC Receiver
Riceve lo stream PCM grezzo 16kHz mono a 16-bit dal telefono Android via TCP
e lo instrada a bassissima latenza (<50ms) alla scheda audio o al Virtual Audio Cable.

Installazione requisiti:
    pip install sounddevice numpy
"""

import sys
import socket
import argparse
import numpy as np
import sounddevice as sd

SAMPLE_RATE = 16000
CHANNELS = 1
BLOCK_SIZE = 1024  # 64ms di buffer per latenza minima senza glitch

def main():
    parser = argparse.ArgumentParser(description="NetMic PC Audio Receiver")
    parser.add_argument("--ip", default="192.168.1.100", help="Indirizzo IP del telefono mostrato nell'app")
    parser.add_argument("--port", type=int, default=8888, help="Porta TCP (default: 8888)")
    parser.add_argument("--device", type=int, default=None, help="Indice del dispositivo audio di output (vedi --list-devices)")
    parser.add_argument("--list-devices", action="store_true", help="Elenca i dispositivi audio disponibili ed esce")
    args = parser.parse_args()

    if args.list_devices:
        print(sd.query_devices())
        return

    print(f"[*] Connessione a NetMic ({args.ip}:{args.port})...")

    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    sock.connect((args.ip, args.port))
    print("[+] Connesso con successo! Streaming audio attivo in tempo reale.")
    print("[*] Premi Ctrl+C per fermare.")

    # Apri stream di riproduzione audio locale con sounddevice
    stream = sd.OutputStream(
        samplerate=SAMPLE_RATE,
        channels=CHANNELS,
        dtype='int16',
        blocksize=BLOCK_SIZE,
        device=args.device,
        latency='low'
    )
    stream.start()

    bytes_to_read = BLOCK_SIZE * 2  # 2 byte per campione int16
    try:
        while True:
            # Ricezione blocco esatto di campioni
            data = bytearray()
            while len(data) < bytes_to_read:
                packet = sock.recv(bytes_to_read - len(data))
                if not packet:
                    raise ConnectionResetError("Server Android ha chiuso la connessione.")
                data.extend(packet)

            # Converti in array int16 numpy e scrivi nello stream audio
            samples = np.frombuffer(data, dtype=np.int16)
            stream.write(samples)

    except KeyboardInterrupt:
        print("
[*] Arresto su richiesta dell'utente.")
    except Exception as e:
        print(f"
[-] Errore: {e}")
    finally:
        stream.stop()
        stream.close()
        sock.close()
        print("[*] Connessione chiusa.")

if __name__ == "__main__":
    main()