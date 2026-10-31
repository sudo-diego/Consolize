"""
Juegos de sonidos de navegación de Consolize (moverse, elegir, volver, cambiar de sección, abrir el panel, salir).
Sintetizados desde cero (sin muestras de otras consolas). Uso:  python make_nav_sounds.py <carpeta res/raw>
Cada juego deja nav_<juego>_<sonido>.wav (mono, 44,1 kHz, 16 bits). "clasico" son los de siempre (nav_<sonido>.wav).
"""
import math, random, struct, sys, wave, os

SR = 44100

def env(n, attack=0.002, decay=0.06, curve=1.0):
    """Ataque corto (sin clic) y caída exponencial; los últimos 5 ms se apagan del todo."""
    a = max(1, int(attack * SR)); out = []
    for i in range(n):
        t = i / SR
        e = (i / a) if i < a else math.exp(-(t - attack) / decay) ** curve
        tail = min(1.0, (n - i) / (0.005 * SR))
        out.append(e * tail)
    return out

def tone(dur, freqs, decay, partials=((1, 1.0),), glide=0.0, wave_fn=math.sin, attack=0.002, vib=0.0):
    """Notas (una o varias seguidas en [freqs] como (inicio_s, hz)), con parciales y deslizamiento de tono."""
    n = int(dur * SR); buf = [0.0] * n
    for start, f in freqs:
        s0 = int(start * SR); m = n - s0
        e = env(m, attack, decay)
        ph = [0.0] * len(partials)
        for i in range(m):
            t = i / SR
            ff = f * (1 + glide * min(1.0, t / 0.06)) * (1 + vib * math.sin(2 * math.pi * 6 * t))
            v = 0.0
            for k, (mult, amp) in enumerate(partials):
                ph[k] += 2 * math.pi * ff * mult / SR
                v += amp * wave_fn(ph[k])
            buf[s0 + i] += v * e[i]
    return buf

def noise_tick(dur, decay, cutoff=0.25, seed=1):
    random.seed(seed); n = int(dur * SR); e = env(n, 0.0005, decay); y = 0.0; out = []
    for i in range(n):
        y += cutoff * (random.uniform(-1, 1) - y)   # paso bajo simple: más suave que el ruido blanco
        out.append(y * e[i] * 3)
    return out

def mix(*bufs):
    n = max(len(b) for b in bufs); out = [0.0] * n
    for b in bufs:
        for i, v in enumerate(b): out[i] += v
    return out

def soft_square(ph):   # cuadrada redondeada (sin el zumbido áspero de la cuadrada pura)
    return math.tanh(3 * math.sin(ph))

def save(path, buf, peak_db=-7.0):
    m = max(1e-9, max(abs(v) for v in buf)); g = 10 ** (peak_db / 20) / m
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, v * g)) * 32767)) for v in buf))

# Cada juego: (sonido -> buffer). Los de mover son los más cortos y bajos (se oyen mucho).
def packs():
    P = {}
    # Burbuja: gotas redondas que suben un poco de tono.
    bub = ((1, 1.0), (2, 0.15))
    P['burbuja'] = {
        'move':   (tone(0.06, [(0, 880)], 0.018, bub, glide=0.25), -13),
        'select': (tone(0.16, [(0, 660), (0.055, 990)], 0.04, bub, glide=0.2), -8),
        'back':   (tone(0.15, [(0, 880), (0.05, 587)], 0.04, bub, glide=-0.15), -9),
        'tab':    (tone(0.09, [(0, 740)], 0.03, bub, glide=0.35), -10),
        'open':   (tone(0.24, [(0, 523), (0.06, 784), (0.12, 1046)], 0.05, bub, glide=0.15), -8),
        'exit':   (tone(0.3, [(0, 1046), (0.08, 784), (0.16, 523)], 0.06, bub, glide=-0.1), -8),
    }
    # Cristal: campanitas finas (parciales inarmónicos, cola corta).
    gl = ((1, 1.0), (2.76, 0.35), (5.4, 0.12))
    P['cristal'] = {
        'move':   (tone(0.07, [(0, 2093)], 0.02, gl), -16),
        'select': (tone(0.2, [(0, 1568), (0.05, 2349)], 0.06, gl), -10),
        'back':   (tone(0.18, [(0, 2093), (0.05, 1397)], 0.05, gl), -11),
        'tab':    (tone(0.12, [(0, 1760)], 0.045, gl), -12),
        'open':   (tone(0.32, [(0, 1319), (0.06, 1760), (0.12, 2637)], 0.08, gl), -10),
        'exit':   (tone(0.36, [(0, 2637), (0.08, 1760), (0.16, 1319)], 0.09, gl), -10),
    }
    # Madera: marimba cálida (fundamental y el parcial 4x que se apaga rápido).
    mar = ((1, 1.0), (4, 0.25), (10, 0.05))
    P['madera'] = {
        'move':   (tone(0.07, [(0, 523)], 0.025, mar), -11),
        'select': (tone(0.2, [(0, 392), (0.06, 587)], 0.06, mar), -7),
        'back':   (tone(0.18, [(0, 587), (0.06, 392)], 0.055, mar), -8),
        'tab':    (tone(0.11, [(0, 440)], 0.04, mar), -9),
        'open':   (tone(0.3, [(0, 392), (0.07, 494), (0.14, 659)], 0.07, mar), -7),
        'exit':   (tone(0.34, [(0, 659), (0.08, 494), (0.16, 330)], 0.08, mar), -7),
    }
    # Suave: casi solo un toque (un clic filtrado con un tono bajo debajo), para quien quiere que se note poco.
    P['suave'] = {
        'move':   (mix(noise_tick(0.035, 0.006, 0.18, 2), tone(0.035, [(0, 330)], 0.01)), -18),
        'select': (mix(noise_tick(0.08, 0.012, 0.2, 3), tone(0.12, [(0, 440), (0.04, 554)], 0.03)), -12),
        'back':   (mix(noise_tick(0.08, 0.012, 0.15, 4), tone(0.12, [(0, 440), (0.04, 349)], 0.03)), -13),
        'tab':    (mix(noise_tick(0.05, 0.01, 0.22, 5), tone(0.07, [(0, 392)], 0.02)), -14),
        'open':   (tone(0.22, [(0, 349), (0.06, 440), (0.12, 523)], 0.05, ((1, 1.0), (2, 0.08))), -12),
        'exit':   (tone(0.26, [(0, 523), (0.07, 440), (0.14, 349)], 0.06, ((1, 1.0), (2, 0.08))), -12),
    }
    # Digital: blips de videojuego retro, pero redondeados y bajitos.
    P['digital'] = {
        'move':   (tone(0.045, [(0, 1175)], 0.012, wave_fn=soft_square), -20),
        'select': (tone(0.13, [(0, 880), (0.04, 1319)], 0.03, wave_fn=soft_square), -15),
        'back':   (tone(0.13, [(0, 1319), (0.04, 880)], 0.03, wave_fn=soft_square), -16),
        'tab':    (tone(0.08, [(0, 988)], 0.02, glide=0.2, wave_fn=soft_square), -17),
        'open':   (tone(0.22, [(0, 659), (0.05, 880), (0.1, 1319)], 0.04, wave_fn=soft_square), -15),
        'exit':   (tone(0.26, [(0, 1319), (0.06, 988), (0.12, 659)], 0.05, wave_fn=soft_square), -15),
    }
    return P

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    for pack, sounds in packs().items():
        for kind, (buf, db) in sounds.items():
            save(os.path.join(out, f'nav_{pack}_{kind}.wav'), buf, db)
    print('listo')
