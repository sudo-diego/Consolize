"""
Música ambiental de la app: tres pistas originales, hechas una vez en el PC (como los sonidos de navegación) y
guardadas en app/src/main/res/raw como OGG. Solo Python estándar para sintetizar; ffmpeg para el eco y el OGG.

  - ajustes: tranquila y jazzera, piano eléctrico con bajo y escobillas (estilo menú de configuración de consola).
  - bolsillo: alegre y saltarina, marimba y pizzicato con swing (estilo menú de portátil).
  - cristal: calma, arpegios de campanitas sobre un colchón suave.

Uso: python tools/music/make_music.py   (necesita ffmpeg en el PATH)
"""
import array, math, os, random, subprocess, sys, tempfile, wave

SR = 32000
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "raw")

NOTE = {n: i for i, n in enumerate(["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"])}


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def chord(root, kind, octave=4):
    """Notas MIDI de un acorde: root como 'D', kind como 'maj7'."""
    base = 12 * (octave + 1) + NOTE[root]
    shapes = {
        "maj7": [0, 4, 7, 11], "m7": [0, 3, 7, 10], "7": [0, 4, 7, 10], "m9": [0, 3, 7, 10, 14],
        "maj9": [0, 4, 7, 11, 14], "9": [0, 4, 7, 10, 14], "sus4": [0, 5, 7, 10], "add9": [0, 4, 7, 14],
        "6": [0, 4, 7, 9], "m6": [0, 3, 7, 9],
    }
    return [base + i for i in shapes[kind]]


class Track:
    def __init__(self, seconds):
        self.n = int(seconds * SR)
        self.buf = [0.0] * self.n

    def add(self, start, samples, gain=1.0):
        """Suma con vuelta al principio: lo que pasa del final cae al inicio (el bucle no se corta)."""
        i0 = int(start * SR)
        n = self.n
        b = self.buf
        for k, v in enumerate(samples):
            b[(i0 + k) % n] += v * gain


def env(t, a, d):
    return (t / a if t < a else 1.0) * math.exp(-max(0.0, t - a) * d)


def epiano(f, dur, vel=1.0):
    out = []
    for k in range(int((dur + 1.2) * SR)):
        t = k / SR
        rel = 1.0 if t < dur else math.exp(-(t - dur) * 6)
        mod = 1.1 * math.exp(-t * 5) * math.sin(2 * math.pi * f * t)
        s = math.sin(2 * math.pi * f * t + mod) + 0.25 * math.sin(2 * math.pi * 2 * f * t) * math.exp(-t * 3)
        trem = 1 + 0.08 * math.sin(2 * math.pi * 4.5 * t)
        out.append(s * env(t, 0.004, 1.6) * rel * trem * vel)
    return out


def bass(f, dur, vel=1.0):
    out = []
    for k in range(int((dur + 0.15) * SR)):
        t = k / SR
        rel = 1.0 if t < dur else math.exp(-(t - dur) * 30)
        s = math.sin(2 * math.pi * f * t) + 0.3 * math.sin(2 * math.pi * 2 * f * t) * math.exp(-t * 6)
        out.append(s * env(t, 0.006, 2.2) * rel * vel)
    return out


def marimba(f, vel=1.0):
    out = []
    for k in range(int(0.9 * SR)):
        t = k / SR
        s = math.sin(2 * math.pi * f * t) * math.exp(-t * 6) + 0.35 * math.sin(2 * math.pi * 3.93 * f * t) * math.exp(-t * 22)
        out.append(s * min(1.0, t / 0.002) * vel)
    return out


def pluck(f, vel=1.0):
    out = []
    for k in range(int(0.5 * SR)):
        t = k / SR
        s = math.sin(2 * math.pi * f * t) + 0.5 * math.sin(2 * math.pi * 2 * f * t) * math.exp(-t * 12)
        out.append(s * math.exp(-t * 11) * min(1.0, t / 0.003) * vel)
    return out


def bell(f, vel=1.0, length=2.4):
    out = []
    for k in range(int(length * SR)):
        t = k / SR
        mod = 2.0 * math.exp(-t * 2.5) * math.sin(2 * math.pi * 3.5 * f * t)
        s = math.sin(2 * math.pi * f * t + mod)
        out.append(s * math.exp(-t * 1.8) * min(1.0, t / 0.003) * vel)
    return out


def pad(freqs, dur):
    out = []
    for k in range(int(dur * SR)):
        t = k / SR
        a = min(1.0, t / 0.8) * min(1.0, (dur - t) / 0.8)
        s = 0.0
        for i, f in enumerate(freqs):
            s += math.sin(2 * math.pi * f * t + i) + 0.5 * math.sin(2 * math.pi * f * 1.003 * t)
        out.append(s * a / len(freqs))
    return out


def noise_hit(length, decay, seed, hp=True):
    r = random.Random(seed)
    out, prev = [], 0.0
    for k in range(int(length * SR)):
        t = k / SR
        x = r.uniform(-1, 1)
        y = x - prev if hp else x
        prev = x
        out.append(y * math.exp(-t * decay))
    return out


def soft_kick():
    out = []
    for k in range(int(0.3 * SR)):
        t = k / SR
        f = 50 + 70 * math.exp(-t * 30)
        out.append(math.sin(2 * math.pi * f * t) * math.exp(-t * 14))
    return out


def melody(rng, chords, beats_per_chord, beat, pattern, octave_shift, voice, track, gain, start_beat=0):
    """Una línea propia: notas del acorde (y alguna de paso) sobre un ritmo fijo, sin repetir igual cada vez."""
    t_beat = start_beat
    prev = None
    for ch in chords:
        tones = sorted(set(n + 12 * octave_shift for n in ch))
        for onset, length in pattern:
            if rng.random() < 0.18:
                continue  # silencios: que respire
            pool = tones + [tones[0] + 12]
            if prev is not None:
                pool.sort(key=lambda n: abs(n - prev))
                n = pool[0] if rng.random() < 0.55 else rng.choice(pool[:3])
            else:
                n = rng.choice(pool)
            if rng.random() < 0.12:
                n += rng.choice([-2, 2])  # nota de paso
            prev = n
            track.add((t_beat + onset) * beat, voice(hz(n), length * beat), gain)
        t_beat += beats_per_chord


def make_ajustes():
    bpm = 92
    beat = 60 / bpm
    prog = [("D", "maj9"), ("B", "m9"), ("E", "m9"), ("A", "9"), ("F#", "m7"), ("B", "m7"), ("G", "maj7"), ("A", "sus4")]
    bars = len(prog) * 2
    tr = Track(bars * 4 * beat)
    rng = random.Random(7)
    for rep in range(2):
        for i, (root, kind) in enumerate(prog):
            b0 = (rep * len(prog) + i) * 4
            notes = chord(root, kind, 3)
            for n in notes:  # acorde en 1 y en el "y" del 2, como un comping suave
                tr.add(b0 * beat, epiano(hz(n + 12), 1.4 * beat, 0.30), 0.30)
                tr.add((b0 + 2.5) * beat, epiano(hz(n + 12), 1.2 * beat, 0.22), 0.30)
            r = 12 * 3 + NOTE[root]
            for k, off in enumerate([0, 1, 2, 3]):  # bajo caminando
                step = [0, 7, 12, 10][k] if rep == 0 else [0, 4, 7, 9][k]
                tr.add((b0 + off) * beat, bass(hz(r + step), 0.9 * beat, 0.9), 0.45)
            for k in range(8):  # escobillas
                tr.add((b0 + k * 0.5 + (0.08 if k % 2 else 0)) * beat, noise_hit(0.12, 40, 100 + b0 * 8 + k), 0.025 if k % 2 else 0.04)
    mel_chords = [chord(r, k, 4) for r, k in prog] * 2
    melody(rng, mel_chords, 4, beat, [(0, 1.5), (1.5, 0.5), (2, 1), (3, 1)], 1, lambda f, d: epiano(f, d, 0.5), tr, 0.32)
    return tr


def make_bolsillo():
    bpm = 120
    beat = 60 / bpm
    prog = [("C", "6"), ("A", "m7"), ("D", "m7"), ("G", "7"), ("E", "m7"), ("A", "7"), ("D", "m7"), ("G", "sus4")]
    reps = 2
    tr = Track(len(prog) * reps * 4 * beat)
    rng = random.Random(21)
    swing = 0.16
    for rep in range(reps):
        for i, (root, kind) in enumerate(prog):
            b0 = (rep * len(prog) + i) * 4
            notes = chord(root, kind, 4)
            for k in range(4):  # acordes cortos en los tiempos 2 y 4, pizzicato
                if k in (1, 3):
                    for n in notes[:3]:
                        tr.add((b0 + k) * beat, pluck(hz(n), 0.5), 0.22)
            r = 12 * 3 + NOTE[root]
            for k, step in enumerate([0, 7, 0, 7]):
                tr.add((b0 + k) * beat, bass(hz(r + step), 0.45 * beat, 0.9), 0.4)
            tr.add(b0 * beat, soft_kick(), 0.35)
            tr.add((b0 + 2) * beat, soft_kick(), 0.25)
            for k in range(8):
                tr.add((b0 + k * 0.5 + (swing if k % 2 else 0)) * beat, noise_hit(0.05, 90, 300 + b0 * 8 + k), 0.022)
    mel = [chord(r, k, 5) for r, k in prog] * reps
    pattern = [(0, 0.5), (0.5 + swing, 0.5), (1, 0.5), (2, 0.5), (2.5 + swing, 0.5), (3, 1)]
    melody(rng, mel, 4, beat, pattern, 0, lambda f, d: marimba(f, 0.6), tr, 0.45)
    return tr


def make_cristal():
    bpm = 84
    beat = 60 / bpm
    prog = [("F", "maj9"), ("C", "add9"), ("D", "m9"), ("A#", "maj7"), ("F", "maj7"), ("A", "m7"), ("A#", "maj9"), ("C", "sus4")]
    tr = Track(len(prog) * 2 * 4 * beat)
    rng = random.Random(3)
    for rep in range(2):
        for i, (root, kind) in enumerate(prog):
            b0 = (rep * len(prog) + i) * 4
            notes = chord(root, kind, 3)
            tr.add(b0 * beat, pad([hz(n) for n in notes], 4 * beat + 0.6), 0.16)
            r = 12 * 2 + NOTE[root]
            tr.add(b0 * beat, bass(hz(r + 12), 3.5 * beat, 0.7), 0.35)
            arp = sorted(chord(root, kind, 5))
            order = [0, 2, 1, 3, 2, 1] if rep == 0 else [0, 1, 2, 3, 1, 2]
            for k, idx in enumerate(order):  # arpegio de campanitas en corcheas con espacio
                tr.add((b0 + k * 0.5 + 0.5) * beat, bell(hz(arp[idx % len(arp)]), 0.5), 0.16)
    mel = [chord(r, k, 5) for r, k in prog] * 2
    melody(rng, mel, 4, beat, [(0, 2), (2.5, 1.5)], 1, lambda f, d: bell(f, 0.7, 3.0), tr, 0.18)
    return tr


def write_wav(path, tr, loops=2):
    peak = max(1e-6, max(abs(v) for v in tr.buf))
    g = 0.85 / peak
    data = array.array("h", (int(max(-1, min(1, v * g)) * 32767) for v in tr.buf * loops))
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def encode(name, tr):
    seconds = tr.n / SR
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, name + ".wav")
        write_wav(wav, tr)  # dos vueltas: el eco de la primera cae en la segunda, que es la que queda (bucle sin corte)
        out = os.path.abspath(os.path.join(OUT, "music_" + name + ".ogg"))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-af",
                        f"aecho=0.8:0.6:70|130:0.25|0.15,atrim=start={seconds:.4f}:end={2 * seconds:.4f},asetpts=PTS-STARTPTS,"
                        "lowpass=f=9000,volume=0.9",
                        "-c:a", "libvorbis", "-q:a", "3", out], check=True)
        print(name, f"{seconds:.1f} s", os.path.getsize(out) // 1024, "KB")


if __name__ == "__main__":
    which = sys.argv[1:] or ["ajustes", "bolsillo", "cristal"]
    makers = {"ajustes": make_ajustes, "bolsillo": make_bolsillo, "cristal": make_cristal}
    for name in which:
        encode(name, makers[name]())
