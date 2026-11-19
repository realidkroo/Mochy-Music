# turns the mit kemar compact set (sound.media.mit.edu/resources/KEMAR/compact.zip, unzipped next to this as kemar/) into app/src/main/assets/stage_hrtf.bin
import os
import re
import struct
import sys

import numpy as np
from scipy.io import wavfile

ROOT = sys.argv[1] if len(sys.argv) > 1 else 'kemar'
OUT = sys.argv[2] if len(sys.argv) > 2 else 'stage_hrtf.bin'
NFFT = 1024
TAPS = 160

# every file holds the left ear in column 0 and the right in column 1 for a source on the right side (azimuth 0..180)
entries = []
for folder in sorted(os.listdir(ROOT)):
    for name in sorted(os.listdir(os.path.join(ROOT, folder))):
        match = re.match(r'H(-?\d+)e(\d+)a\.wav', name)
        if not match:
            continue
        rate, data = wavfile.read(os.path.join(ROOT, folder, name))
        assert rate == 44100 and data.shape == (128, 2)
        entries.append((int(match.group(1)), int(match.group(2)), data[:, 0].astype(np.float64), data[:, 1].astype(np.float64)))

# how much of each direction's own tone stays: 0 every direction sounds as even as the song, 1 the dummy head's ears as measured
KEEP = 0.2
freqs = np.fft.rfftfreq(NFFT, 1 / 44100)
low, high = np.searchsorted(freqs, 100), np.searchsorted(freqs, 16000)


# third octave smoothing, so a correction never chases single wiggles
def smoothed(power):
    out = np.copy(power)
    for i, f in enumerate(freqs):
        if f > 0:
            out[i] = np.mean(power[(freqs >= f / 2 ** (1 / 6)) & (freqs <= f * 2 ** (1 / 6))])
    out[0] = out[1]
    # below 100 hz and above 16 khz the measurement is the speaker's limits, not the ear's, so hold the edge values
    out[:low] = out[low]
    out[high:] = out[high]
    return out


# minimum phase through the real cepstrum, so a correction doesn't move any onset and the ear delays stay true
def minimum_phase(gain):
    cepstrum = np.fft.ifft(np.log(np.concatenate([gain, gain[-2:0:-1]]))).real
    folded = np.zeros(NFFT)
    folded[0] = cepstrum[0]
    folded[1:NFFT // 2] = 2 * cepstrum[1:NFFT // 2]
    folded[NFFT // 2] = cepstrum[NFFT // 2]
    return np.exp(np.fft.fft(folded))


# each direction evened out by both ears' average tone, so where a sound is changes how it sounds only a little; the difference between the ears is untouched
fade = np.ones(TAPS)
fade[-32:] = 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, 32))
corrected = []
for elevation, az, left, right in entries:
    power = smoothed((np.abs(np.fft.rfft(left, NFFT)) ** 2 + np.abs(np.fft.rfft(right, NFFT)) ** 2) / 2)
    correction_db = np.clip(-10 * np.log10(power / power[np.searchsorted(freqs, 1000)]) * (1 - KEEP), -12, 12)
    spectrum = minimum_phase(10 ** (correction_db / 20))
    pair = [np.fft.ifft(np.fft.fft(x, NFFT) * spectrum).real[:TAPS] * fade for x in (left, right)]
    corrected.append((elevation, az, pair[0], pair[1]))

# a sound in front plays at the song's own level in each ear, judged where music's energy is (250 hz to 1 khz), not across every hertz
level_db = []
band = (freqs >= 250) & (freqs <= 1000)
for elevation, az, left, right in corrected:
    if az > 45 or abs(elevation) > 20:
        continue
    power = (np.abs(np.fft.rfft(left, NFFT)) ** 2 + np.abs(np.fft.rfft(right, NFFT)) ** 2) / 2
    level_db.append(np.mean(10 * np.log10(power[band])))
gain = 10 ** (-np.mean(level_db) / 20)
scale = max(max(np.abs(l).max(), np.abs(r).max()) for _, _, l, r in corrected) * gain / 32767

with open(OUT, 'wb') as out:
    out.write(b'MHRT')
    out.write(struct.pack('<iiiif', 1, 44100, TAPS, len(corrected), scale))
    for elevation, az, left, right in corrected:
        out.write(struct.pack('<hh', elevation, az))
        out.write(np.round(left * gain / scale).astype('<i2').tobytes())
        out.write(np.round(right * gain / scale).astype('<i2').tobytes())
print(OUT, os.path.getsize(OUT), 'bytes,', len(corrected), 'directions')
# the straight ahead response as it was written, so a correction that did nothing shows up here
front = next((l * gain) for e, a, l, _ in corrected if e == 0 and a == 0)
response = np.abs(np.fft.rfft(front, 4096)) ** 2
octave_freqs = np.fft.rfftfreq(4096, 1 / 44100)
print('straight ahead:', ' '.join(
    f'{c}:{10 * np.log10(response[(octave_freqs > c / 1.41) & (octave_freqs < c * 1.41)].mean()):+.1f}'
    for c in (125, 250, 500, 1000, 2000, 4000, 8000, 12000, 16000)))
