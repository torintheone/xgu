"""Composes the calm background music of Petri and writes it as a seamless loop.

Everything is synthesised here from sine waves and noise, no samples: soft pads on a slow chord progression in
D major, sparse glassy bells from the pentatonic scale, a quiet low drone and a breath of filtered noise, all in
a long reverb. Every sound that rings past the end of the loop is wrapped round to its start (the reverb is a
circular convolution), so the loop plays without a seam.

Usage: python3 ambient.py <out.wav>   (needs numpy; then encode, see build.sh)
"""
import sys
import wave

import numpy as np

RATE = 32000
CHORD_SECONDS = 20.0
SEED = 20261022

# Chords as MIDI notes: the low root for the drone, then the pad voicing (close, soft, in the middle register).
CHORDS = [
    (38, [57, 62, 66, 69, 76]),  # Dmaj9:   A3 D4 F#4 A4 E5
    (35, [57, 62, 66, 71, 74]),  # Bm7:     A3 D4 F#4 B4 D5
    (31, [55, 62, 66, 71, 73]),  # Gmaj7#11: G3 D4 F#4 B4 C#5
    (33, [57, 64, 69, 71, 76]),  # Asus2:   A3 E4 A4 B4 E5
    (40, [55, 62, 66, 71, 74]),  # Em9/G:   G3 D4 F#4 B4 D5
    (42, [57, 61, 64, 69, 73]),  # F#m7:    A3 C#4 E4 A4 C#5
    (31, [59, 62, 66, 69, 74]),  # Gmaj9:   B3 D4 F#4 A4 D5
    (33, [57, 64, 66, 71, 74]),  # A6sus4:  A3 E4 F#4 B4 D5, back to Dmaj9
]
PENTATONIC = [62, 64, 66, 69, 71]  # D E F# A B


def hz(note):
    return 440.0 * 2.0 ** ((note - 69) / 12.0)


def table(harmonics, rolloff):
    """One cycle of a soft waveform: the given number of harmonics falling off as 1/k^rolloff."""
    phase = np.linspace(0.0, 2.0 * np.pi, 4096, endpoint=False)
    wave_ = sum(np.sin(k * phase) / k**rolloff for k in range(1, harmonics + 1))
    return wave_ / np.abs(wave_).max()


def add_wrapped(out, start, signal):
    """Adds [signal] (frames x 2) into the loop [out] from frame [start], wrapping round its end."""
    n = len(out)
    start %= n
    first = min(len(signal), n - start)
    out[start:start + first] += signal[:first]
    rest = signal[first:]
    while len(rest):
        part = rest[:n]
        out[:len(part)] += part
        rest = rest[n:]


def envelope(frames, attack, release):
    t = np.arange(frames) / RATE
    rise = np.clip(t / attack, 0.0, 1.0)
    fall = np.clip((frames / RATE - t) / release, 0.0, 1.0)
    # Raised-cosine shapes: no clicks, gentle swells.
    return (0.5 - 0.5 * np.cos(np.pi * rise)) * (0.5 - 0.5 * np.cos(np.pi * fall))


def pad_voice(freq, seconds, rng, bright, dark):
    frames = int(seconds * RATE)
    t = np.arange(frames) / RATE
    out = np.zeros((frames, 2))
    for side, detune in ((0, -0.0035), (1, 0.0035)):
        vibrato = 1.0 + 0.0012 * np.sin(2.0 * np.pi * rng.uniform(0.11, 0.19) * t + rng.uniform(0, 6.3))
        phase = np.cumsum(freq * (1.0 + detune) * vibrato / RATE) + rng.uniform()
        index = ((phase % 1.0) * len(bright)).astype(np.int64)
        # The tone slowly opens and closes, like a filter breathing.
        mix = 0.5 + 0.5 * np.sin(2.0 * np.pi * rng.uniform(0.03, 0.06) * t + rng.uniform(0, 6.3))
        out[:, side] = bright[index] * (0.35 + 0.3 * mix) + dark[index] * (0.65 - 0.3 * mix)
    return out * envelope(frames, attack=5.0, release=6.5)[:, None]


def bell(freq, seconds, pan):
    frames = int(seconds * RATE)
    t = np.arange(frames) / RATE
    tone = (np.sin(2.0 * np.pi * freq * t) * np.exp(-t / 2.2)
            + 0.35 * np.sin(2.0 * np.pi * freq * 2.0 * t) * np.exp(-t / 1.1)
            + 0.12 * np.sin(2.0 * np.pi * freq * 2.76 * t) * np.exp(-t / 0.6))
    tone *= np.clip(t / 0.012, 0.0, 1.0)  # a soft strike, no click
    tone *= np.clip((seconds - t) / 0.5, 0.0, 1.0)
    return np.stack([tone * np.cos(pan * np.pi / 2), tone * np.sin(pan * np.pi / 2)], axis=1)


def shaped_noise(frames, rng, low, high):
    """Noise between [low] and [high] Hz with a soft 1/f slope."""
    spectrum = np.fft.rfft(rng.standard_normal(frames))
    f = np.fft.rfftfreq(frames, 1.0 / RATE)
    gain = np.where((f > low) & (f < high), 1.0 / np.sqrt(np.maximum(f, 1.0)), 0.0)
    noise = np.fft.irfft(spectrum * gain, frames)
    return noise / np.abs(noise).max()


def reverb_ir(rng, seconds=5.0):
    """A stereo hall: noise that dies away, the highs sooner than the lows, after a short pre-delay."""
    frames = int(seconds * RATE)
    t = np.arange(frames) / RATE
    ir = np.zeros((frames, 2))
    bands = [(20, 400, 1.15), (400, 1600, 0.95), (1600, 5000, 0.65), (5000, 14000, 0.35)]
    for side in range(2):
        for low, high, tau in bands:
            ir[:, side] += shaped_noise(frames, rng, low, high) * np.exp(-t / tau)
    pre = int(0.035 * RATE)
    ir = np.roll(ir, pre, axis=0)
    ir[:pre] = 0.0
    return ir / np.sqrt((ir**2).sum(axis=0))


def circular_convolve(signal, ir):
    n = len(signal)
    out = np.zeros_like(signal)
    for side in range(2):
        kernel = np.zeros(n)
        kernel[:len(ir)] = ir[:, side]
        out[:, side] = np.fft.irfft(np.fft.rfft(signal[:, side]) * np.fft.rfft(kernel), n)
    return out


def compose():
    rng = np.random.default_rng(SEED)
    total = int(len(CHORDS) * CHORD_SECONDS * RATE)
    pads = np.zeros((total, 2))
    drone = np.zeros((total, 2))
    bells = np.zeros((total, 2))
    bright = table(8, 2.2)
    dark = table(4, 3.0)

    for i, (root, voicing) in enumerate(CHORDS):
        start = int((i * CHORD_SECONDS - 3.5) * RATE)
        seconds = CHORD_SECONDS + 7.0  # overlaps the neighbours by 3.5 s each side
        for note in voicing:
            gain = 0.16 if note < 60 else 0.12
            add_wrapped(pads, start, pad_voice(hz(note), seconds, rng, bright, dark) * gain)
        frames = int(seconds * RATE)
        t = np.arange(frames) / RATE
        low = np.sin(2.0 * np.pi * hz(root + 12) * t) * 0.6 + np.sin(2.0 * np.pi * hz(root) * t) * 0.4
        low *= envelope(frames, attack=6.0, release=7.0) * 0.11
        add_wrapped(drone, start, np.stack([low, low], axis=1))

        # Sparse bells: chord tones and the pentatonic scale, an octave or two up.
        moment = i * CHORD_SECONDS + rng.uniform(1.0, 3.0)
        end = (i + 1) * CHORD_SECONDS
        while moment < end:
            pool = [n for n in voicing if n >= 62] + PENTATONIC
            note = rng.choice(pool) + 12 * rng.choice([1, 1, 2])
            velocity = rng.uniform(0.25, 0.6)
            add_wrapped(bells, int(moment * RATE), bell(hz(note), 7.0, rng.uniform(0.15, 0.85)) * velocity * 0.32)
            # Now and then a second, softer note answers a fifth or an octave away.
            if rng.uniform() < 0.3:
                answer = note + rng.choice([-5, 7, 12])
                add_wrapped(bells, int((moment + rng.uniform(0.6, 1.4)) * RATE),
                            bell(hz(answer), 7.0, rng.uniform(0.15, 0.85)) * velocity * 0.2)
            moment += rng.uniform(2.6, 6.5)

    air = np.stack([shaped_noise(total, rng, 300, 2600), shaped_noise(total, rng, 300, 2600)], axis=1)
    t = np.arange(total) / RATE
    air *= (0.5 + 0.5 * np.sin(2.0 * np.pi * t / (CHORD_SECONDS * 4)))[:, None] * 0.045

    pads = lowpass(pads, 1800.0)
    dry = pads + drone + bells + air
    wet = circular_convolve(pads * 0.6 + bells + air, reverb_ir(rng))
    mix = dry * 0.62 + wet * 0.55
    # Background music stays quiet: about −18 dBFS on average, peaks rounded off below −1 dBFS.
    mix *= 10 ** (-18 / 20) / np.sqrt((mix**2).mean())
    mix = 0.89 * np.tanh(mix / 0.89)
    return mix


def lowpass(signal, cutoff):
    """A gentle 12 dB/octave low-pass, applied round the loop so the seam stays clean."""
    n = len(signal)
    f = np.fft.rfftfreq(n, 1.0 / RATE)
    gain = 1.0 / (1.0 + (f / cutoff) ** 4) ** 0.5
    return np.stack([np.fft.irfft(np.fft.rfft(signal[:, side]) * gain, n) for side in range(2)], axis=1)


def write_wav(path, audio):
    data = (np.clip(audio, -1.0, 1.0) * 32767.0).astype("<i2")
    with wave.open(path, "wb") as f:
        f.setnchannels(2)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(data.tobytes())


if __name__ == "__main__":
    audio = compose()
    write_wav(sys.argv[1], audio)
    rms = np.sqrt((audio**2).mean())
    jump = np.abs(np.diff(np.concatenate([audio, audio[:1]]), axis=0)).max()
    print(f"{len(audio) / RATE:.0f} s, peak {20 * np.log10(np.abs(audio).max()):.1f} dBFS, "
          f"rms {20 * np.log10(rms):.1f} dBFS, largest step {jump:.3f} (seam included)")
