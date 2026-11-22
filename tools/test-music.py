"""Bibliothèque de musique de test pour l'émulateur Android.

Génère de courts morceaux FLAC (une note douce, sans aucune musique protégée) avec leurs tags
(titre, artiste, album, artiste de l'album, année, genre, piste, disque). Le jeu couvre les albums,
un artiste avec « feat. », un album à deux disques, plusieurs genres dans un tag, les années, les
sous-dossiers et un morceau sans tags.

Python 3 seul, sans rien à installer : le FLAC est écrit à la main, sans compression (blocs
« verbatim »), ce que tout lecteur FLAC sait lire. Usage :

    python tools/test-music.py <dossier de sortie>

Puis, pour l'émulateur : adb -s emulator-5554 push <dossier de sortie>/. /sdcard/Music/
"""

import argparse
import math
import os
import struct

# dossier, fichier, titre, artiste, album, artiste de l'album, année, genre, piste, disque
TRACKS = [
    ("Rock", "01 Aube.flac", "Aube", "Les Essais", "Premier Jour", "Les Essais", 1994, "Rock", 1, 1),
    ("Rock", "02 Midi.flac", "Midi", "Les Essais", "Premier Jour", "Les Essais", 1994, "Rock", 2, 1),
    ("Rock", "03 Crepuscule.flac", "Crépuscule", "Les Essais feat. Invité", "Premier Jour", "Les Essais", 1994, "Rock; Pop", 3, 1),
    ("Rock/Live", "01 Concert.flac", "Concert", "Les Essais", "En Direct", "Les Essais", 2005, "Rock", 1, 1),
    ("Jazz", "01 Nuit.flac", "Nuit", "Trio Témoin", "Double Album", "Trio Témoin", 2015, "Jazz", 1, 1),
    ("Jazz", "02 Pluie.flac", "Pluie", "Trio Témoin", "Double Album", "Trio Témoin", 2015, "Jazz", 2, 1),
    ("Jazz", "03 Matin.flac", "Matin", "Trio Témoin", "Double Album", "Trio Témoin", 2015, "Jazz", 1, 2),
    ("Electro", "Signal.flac", "Signal", "Onde Courte", "Fréquences", "Onde Courte", 2023, "Électro", 1, 1),
    ("Electro", "Bruit Blanc.flac", "Bruit blanc", "Onde Courte", "Fréquences", "Onde Courte", 2023, "Électro", 2, 1),
    ("Divers", "Sans tags.flac", None, None, None, None, 0, None, 0, 0),
]

SECONDS = 40          # assez pour qu'une écoute compte (30 s) et pour tester le fondu enchaîné
RATE = 22050          # largement assez pour une note ; fichiers deux fois plus petits
BLOCK = 4096          # échantillons par trame FLAC
VOLUME = 0.15         # note douce : pas de bruit fort dans les écouteurs


def samples(frequency):
    total = SECONDS * RATE
    for i in range(total):
        # Montée et descente d'une demi-seconde : pas de clic au début ni à la fin.
        fade = min(1.0, i / (RATE / 2), (total - i) / (RATE / 2))
        yield int(VOLUME * fade * 32767 * math.sin(2 * math.pi * frequency * i / RATE))


def crc8(data):
    crc = 0
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = ((crc << 1) ^ 0x07) & 0xFF if crc & 0x80 else (crc << 1) & 0xFF
    return crc


def crc16(data):
    crc = 0
    for byte in data:
        crc ^= byte << 8
        for _ in range(8):
            crc = ((crc << 1) ^ 0x8005) & 0xFFFF if crc & 0x8000 else (crc << 1) & 0xFFFF
    return crc


def utf8_number(value):
    """Numéro de trame en « UTF-8 étendu », comme le veut l'en-tête FLAC."""
    if value < 0x80:
        return bytes([value])
    encoded = []
    while True:
        encoded.insert(0, 0x80 | (value & 0x3F))
        value >>= 6
        if value < (0x40 >> len(encoded)):
            break
    lead = (0xFF00 >> (len(encoded) + 1)) & 0xFF
    return bytes([lead | value] + encoded)


def frame(number, block):
    """Une trame mono 16 bits, sous-trame « verbatim » (échantillons tels quels)."""
    size = len(block)
    # Taille de bloc 0b0111 : sur 16 bits à la fin de l'en-tête ; fréquence 0b0000 : celle de
    # STREAMINFO ; canaux 0b0000 : mono ; échantillons 0b100 : 16 bits.
    header = bytes([0xFF, 0xF8, 0x70, 0x08]) + utf8_number(number) + struct.pack(">H", size - 1)
    header += bytes([crc8(header)])
    body = bytes([0x02]) + b"".join(struct.pack(">h", sample) for sample in block)
    data = header + body
    return data + struct.pack(">H", crc16(data))


def vorbis_comment(tags):
    vendor = b"ongaku test-music"
    entries = [("%s=%s" % (key, value)).encode("utf-8") for key, value in tags if value]
    block = struct.pack("<I", len(vendor)) + vendor + struct.pack("<I", len(entries))
    for entry in entries:
        block += struct.pack("<I", len(entry)) + entry
    return block


def write_flac(path, frequency, tags):
    pcm = list(samples(frequency))
    frames = [frame(n, pcm[i:i + BLOCK]) for n, i in enumerate(range(0, len(pcm), BLOCK))]
    smallest = min(len(f) for f in frames)
    largest = max(len(f) for f in frames)
    # STREAMINFO : blocs min/max, trames min/max, fréquence (20 bits), canaux-1 (3), bits-1 (5),
    # nombre d'échantillons (36), empreinte MD5 laissée à zéro (« inconnue », admis par la norme).
    info = struct.pack(">HH", BLOCK, BLOCK) + smallest.to_bytes(3, "big") + largest.to_bytes(3, "big")
    packed = (RATE << 44) | (0 << 41) | (15 << 36) | len(pcm)
    info += packed.to_bytes(8, "big") + bytes(16)
    comment = vorbis_comment(tags)
    with open(path, "wb") as out:
        out.write(b"fLaC")
        out.write(bytes([0x00]) + len(info).to_bytes(3, "big") + info)
        out.write(bytes([0x84]) + len(comment).to_bytes(3, "big") + comment)  # 0x80 : dernier bloc
        for data in frames:
            out.write(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("output")
    args = parser.parse_args()

    for index, (folder, name, title, artist, album, album_artist, year, genre, track, disc) in enumerate(TRACKS):
        directory = os.path.join(args.output, folder)
        os.makedirs(directory, exist_ok=True)
        tags = [
            ("TITLE", title),
            ("ARTIST", artist),
            ("ALBUM", album),
            ("ALBUMARTIST", album_artist),
            # Android lit l'année dans YEAR ; DATE est le nom habituel des autres lecteurs.
            ("YEAR", year or None),
            ("DATE", year or None),
            ("GENRE", genre),
            ("TRACKNUMBER", track or None),
            ("DISCNUMBER", disc or None),
        ]
        target = os.path.join(directory, name)
        # Une note différente par morceau, pour les reconnaître à l'oreille.
        write_flac(target, 220 * 2 ** (index / 12), tags)
        print(target)


if __name__ == "__main__":
    main()
