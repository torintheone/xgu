"""Synthesises the sound effects of Petri: soft bells and bubbles in the key of the background music (D major).

Every sound is built here from sine waves and a little noise, no samples:
- win: a rising arpeggio of glassy bells that settles into a shimmering chord;
- lose: two soft, darker tones falling a third;
- achievement: two bright bells and a sparkle of tiny high ones;
- plant: a bubble that pops up in pitch;
- tap: a short, quiet tick for buttons;
- deny: a low, soft knock when the synthesizer may not plant.

Usage: python3 effects.py <folder>   (needs numpy; writes one WAV per sound, then encode, see build.sh)
"""
import os
import sys
import wave

import numpy as np

RATE = 32000
SEED = 20261023


def hz(note):
    return 440.0 * 2.0 ** ((note - 69) / 12.0)


def silence(seconds):
    return np.zeros(int(seconds * RATE))


def bell(note, seconds, decay, brightness=1.0):
    """A glassy bell: a few slightly inharmonic partials, each dying away on its own."""
    t = np.arange(int(seconds * RATE)) / RATE
    f = hz(note)
    out = np.zeros_like(t)
    for ratio, level, speed in ((1.0, 1.0, 1.0), (2.0, 0.42, 1.6), (2.76, 0.22 * brightness, 2.4), (5.4, 0.08 * brightness, 3.5)):
        out += level * np.sin(2.0 * np.pi * f * ratio * t) * np.exp(-t * speed / decay)
    attack = np.clip(t / 0.004, 0.0, 1.0)
    return out * attack * release(len(t))


def release(frames, share=0.3):
    """Ones, then a raised-cosine fall to zero over the last [share] of the sound: no click where it ends."""
    out = np.ones(frames)
    n = max(1, int(frames * share))
    out[-n:] = 0.5 + 0.5 * np.cos(np.linspace(0.0, np.pi, n))
    return out


def soft_tone(note, seconds, decay):
    """A round, darker tone: the fundamental and a quiet octave."""
    t = np.arange(int(seconds * RATE)) / RATE
    f = hz(note)
    out = np.sin(2.0 * np.pi * f * t) + 0.25 * np.sin(4.0 * np.pi * f * t) * np.exp(-t * 3.0)
    attack = np.clip(t / 0.012, 0.0, 1.0)
    return out * attack * np.exp(-t / decay) * release(len(t))


def place(out, start, signal, gain=1.0):
    """Adds [signal] into [out] from [start] seconds, as far as it fits."""
    i = int(start * RATE)
    n = min(len(signal), len(out) - i)
    out[i:i + n] += gain * signal[:n]


def lowpass(signal, cutoff):
    spectrum = np.fft.rfft(signal)
    freqs = np.fft.rfftfreq(len(signal), 1.0 / RATE)
    spectrum *= 1.0 / (1.0 + (freqs / cutoff) ** 4)
    return np.fft.irfft(spectrum, len(signal))


def room(signal, seconds, wet):
    """A small, soft room: decaying noise as the impulse response."""
    rng = np.random.default_rng(SEED)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    ir = rng.standard_normal(n) * np.exp(-t * 6.0 / seconds)
    ir = lowpass(ir, 3500.0)
    ir /= np.sqrt(np.sum(ir ** 2))
    tail = np.convolve(signal, ir)[:len(signal)]
    return signal + wet * tail


def fade_out(signal, seconds):
    n = min(len(signal), int(seconds * RATE))
    signal[-n:] *= np.linspace(1.0, 0.0, n) ** 2
    return signal


def finish(signal, peak_db, tail=0.05):
    signal = fade_out(signal, tail)
    return signal / np.abs(signal).max() * 10.0 ** (peak_db / 20.0)


def win():
    out = silence(2.2)
    for k, note in enumerate((74, 78, 81, 86)):  # D5 F#5 A5 D6
        place(out, 0.11 * k, bell(note, 1.8, decay=0.9 + 0.15 * k), gain=0.8)
    # The chord that stays: a soft shimmer over D, F# and A.
    t = np.arange(int(1.6 * RATE)) / RATE
    swell = np.sin(np.pi * np.clip(t / 1.6, 0.0, 1.0)) ** 2
    chord = sum(np.sin(2.0 * np.pi * hz(n) * t + k) for k, n in enumerate((74, 78, 81, 86)))
    shimmer = 1.0 + 0.25 * np.sin(2.0 * np.pi * 6.0 * t)
    place(out, 0.4, chord * swell * shimmer * release(len(t)), gain=0.12)
    return finish(room(out, 1.2, 0.35), -3.0, tail=0.4)


def lose():
    out = silence(1.5)
    place(out, 0.0, soft_tone(69, 1.2, decay=0.55))  # A4
    place(out, 0.28, soft_tone(65, 1.2, decay=0.6), gain=0.9)  # F4
    place(out, 0.28, soft_tone(62, 1.2, decay=0.6), gain=0.5)  # D4
    return finish(room(lowpass(out, 2200.0), 1.0, 0.3), -5.0, tail=0.4)


def achievement():
    out = silence(1.7)
    place(out, 0.0, bell(81, 1.5, decay=0.8))  # A5
    place(out, 0.09, bell(86, 1.5, decay=0.9))  # D6
    rng = np.random.default_rng(SEED + 1)
    sparkle = (88, 90, 93, 95, 98)  # E6 F#6 A6 B6 D7
    for k in range(7):
        note = sparkle[rng.integers(len(sparkle))]
        place(out, 0.16 + 0.055 * k, bell(note, 0.6, decay=0.25, brightness=0.6), gain=0.22 * (1.0 - 0.08 * k))
    return finish(room(out, 1.0, 0.3), -3.0, tail=0.3)


def plant():
    seconds = 0.2
    t = np.arange(int(seconds * RATE)) / RATE
    # The pitch rises quickly, like a bubble rising and popping.
    freq = 480.0 + 380.0 * (1.0 - np.exp(-t / 0.035))
    phase = 2.0 * np.pi * np.cumsum(freq) / RATE
    body = np.sin(phase) + 0.18 * np.sin(2.0 * phase)
    envelope = np.clip(t / 0.003, 0.0, 1.0) * np.exp(-t / 0.05)
    return finish(body * envelope, -8.0, tail=0.03)


def tap():
    seconds = 0.06
    t = np.arange(int(seconds * RATE)) / RATE
    rng = np.random.default_rng(SEED + 2)
    click = np.sin(2.0 * np.pi * 1750.0 * t) * np.exp(-t / 0.012)
    noise = lowpass(rng.standard_normal(len(t)), 4000.0) * np.exp(-t / 0.004) * 0.3
    envelope = np.clip(t / 0.001, 0.0, 1.0)
    return finish((click + noise) * envelope, -14.0, tail=0.01)


def deny():
    seconds = 0.22
    t = np.arange(int(seconds * RATE)) / RATE
    freq = 260.0 - 60.0 * np.clip(t / 0.12, 0.0, 1.0)
    phase = 2.0 * np.pi * np.cumsum(freq) / RATE
    body = np.sin(phase) + 0.3 * np.sin(2.0 * phase) * np.exp(-t / 0.03)
    envelope = np.clip(t / 0.004, 0.0, 1.0) * np.exp(-t / 0.07)
    return finish(body * envelope, -9.0, tail=0.04)


SOUNDS = {
    "sfx_win": win,
    "sfx_lose": lose,
    "sfx_achievement": achievement,
    "sfx_plant": plant,
    "sfx_tap": tap,
    "sfx_deny": deny,
}


def write_wav(path, signal):
    data = (np.clip(signal, -1.0, 1.0) * 32767.0).astype("<i2")
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(data.tobytes())


def main():
    folder = sys.argv[1]
    os.makedirs(folder, exist_ok=True)
    for name, make in SOUNDS.items():
        signal = make()
        write_wav(os.path.join(folder, name + ".wav"), signal)
        rms = 20.0 * np.log10(np.sqrt(np.mean(signal ** 2)) + 1e-12)
        print(f"{name}: {len(signal) / RATE:.2f} s, peak {20 * np.log10(np.abs(signal).max()):.1f} dBFS, rms {rms:.1f} dBFS")


if __name__ == "__main__":
    main()
