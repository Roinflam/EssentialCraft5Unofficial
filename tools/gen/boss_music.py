#!/usr/bin/env python3
"""天灾之心的首领音乐（第十三批 13e，batch13.md §5.3.5）：numpy 现算一段 30 秒的无缝循环，输出双声道 OGG。

用法：python tools/gen/boss_music.py [--check]
  生成到 src/main/resources/assets/essentialcraft5/sounds/boss/calamity_heart.ogg；声音事件 sound.music.calamity_heart
  定义在 tools/sounds/eccalamityheart.json（stream: true，客户端循环播放）。
  --check  不写文件，只做数值检查（峰值、时长、首尾接缝）。

无缝的做法（同 machine_sounds.py 的环形片段，只是整段就是一个周期）：
  - 持续的声部（低音嗡鸣、风声噪声）只用在 30 秒里转整数圈的频率 / 调制，整段首尾自然接上；
  - 一个个音符（心跳、钟声、低音拨弦）按拍子放进 30 秒的环形缓冲区，尾音超出末尾的部分绕回开头叠上去，
    所以从末尾跳回开头时听到的正是上一圈留下的尾音。
  96 拍 / 分、4/4 拍，30 秒正好 12 小节；和弦走 Am - F - Dm - E 三遍。
--check 量两件事：接缝两侧一个采样的跳变不比段内最大的跳变大；把整段首尾相接、在接缝前后各 50 毫秒里量响度，和段内其余小节线前后同样长的窗口比，差不超过 3 dB。
所有随机数都用固定种子，重跑结果一致（Vorbis 编码的字节每次会不同）。
"""
import argparse
import os
import sys

import numpy as np
import soundfile as sf

SR = 44100
SECONDS = 30.0
N = int(SR * SECONDS)
BPM = 96
BEAT = 60.0 / BPM
BARS = 12
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources",
                   "assets", "essentialcraft5", "sounds", "boss", "calamity_heart.ogg")

A1 = 55.0
CHORDS = [  # 每个和弦的根音（赫兹）和三和弦的半音
    (110.0, (0, 3, 7)),   # Am
    (87.31, (0, 4, 7)),   # F
    (73.42, (0, 3, 7)),   # Dm
    (82.41, (0, 4, 7)),   # E
]


def t_axis(n=N):
    return np.arange(n) / SR


def periodic_freq(freq):
    """把频率挪到 30 秒里转整数圈的最近值（首尾相位接得上）"""
    return round(freq * SECONDS) / SECONDS


def place(buf, clip, start):
    """把一个音符放进环形缓冲区：超出末尾的部分绕回开头"""
    i = int(round(start * SR)) % N
    n = len(clip)
    end = i + n
    if end <= N:
        buf[i:end] += clip
    else:
        head = N - i
        buf[i:] += clip[:head]
        rest = clip[head:]
        while len(rest) > 0:
            k = min(len(rest), N)
            buf[:k] += rest[:k]
            rest = rest[k:]


def envelope(seconds, attack, tau):
    t = t_axis(int(seconds * SR))
    env = np.exp(-t / tau)
    a = int(attack * SR)
    if a > 0:
        env[:a] *= np.linspace(0, 1, a)
    return env


def heartbeat():
    """心跳的一下：低频正弦往下滑 + 一点噪声"""
    seconds = 0.5
    t = t_axis(int(seconds * SR))
    freq = 62 - 24 * np.minimum(t / 0.15, 1)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    rng = np.random.default_rng(13)
    body = np.sin(phase) * envelope(seconds, 0.004, 0.09)
    click = rng.normal(0, 1, len(t)) * envelope(seconds, 0.001, 0.012) * 0.15
    return body + click


def bell(freq, seconds=3.0, tau=0.9):
    t = t_axis(int(seconds * SR))
    partials = (1.0, 2.76, 5.40, 8.93)
    amps = (1.0, 0.45, 0.2, 0.08)
    sig = sum(a * np.sin(2 * np.pi * freq * p * t) * np.exp(-t / (tau / (1 + i * 0.8)))
              for i, (p, a) in enumerate(zip(partials, amps)))
    a = int(0.003 * SR)
    sig[:a] *= np.linspace(0, 1, a)
    return sig


def pluck(freq, seconds=1.2):
    t = t_axis(int(seconds * SR))
    sig = np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(2 * np.pi * freq * 2 * t) + 0.15 * np.sin(2 * np.pi * freq * 3 * t)
    return sig * envelope(seconds, 0.006, 0.35)


def drone():
    """低音嗡鸣：A1 与它的五度，按 0.2 Hz 慢慢起伏（30 秒里 6 圈）"""
    t = t_axis()
    root = np.sin(2 * np.pi * periodic_freq(A1) * t)
    fifth = 0.5 * np.sin(2 * np.pi * periodic_freq(A1 * 1.5) * t + 0.7)
    sub = 0.4 * np.sin(2 * np.pi * periodic_freq(A1 / 2) * t)
    swell = 0.75 + 0.25 * np.sin(2 * np.pi * 0.2 * t)
    return (root + fifth + sub) * swell


def wind(rng):
    """风声：30 秒为周期的带通噪声（每条谱线随机相位），按 0.1 Hz 起伏"""
    spectrum = np.zeros(N // 2 + 1, dtype=complex)
    freqs = np.fft.rfftfreq(N, 1 / SR)
    band = (freqs >= 200) & (freqs <= 1800)
    mag = (freqs[band] / 200.0) ** -0.8
    spectrum[band] = mag * np.exp(1j * rng.uniform(0, 2 * np.pi, band.sum()))
    sig = np.fft.irfft(spectrum, N)
    sig /= np.max(np.abs(sig)) + 1e-12
    return sig * (0.6 + 0.4 * np.sin(2 * np.pi * 0.1 * t_axis()))


def semitone(freq, n):
    return freq * 2 ** (n / 12)


def compose():
    rng = np.random.default_rng(5)
    left = np.zeros(N)
    right = np.zeros(N)
    d = drone() * 0.22
    left += d
    right += d
    w = wind(rng) * 0.08
    left += w
    right += np.roll(w, SR // 3)
    beat = heartbeat()
    for bar in range(BARS):
        start = bar * 4 * BEAT
        root, triad = CHORDS[bar % 4]
        # 心跳：每小节第 1 拍「咚」、半拍后轻一点的「哒」；第 3 拍再来一组
        for offset, gain in ((0.0, 0.55), (0.5, 0.35), (2.0, 0.5), (2.5, 0.3)):
            place(left, beat * gain, start + offset * BEAT)
            place(right, beat * gain, start + offset * BEAT)
        # 低音拨弦：根音在 1、2.5、3.5 拍
        for offset in (0.0, 1.5, 2.5):
            p = pluck(root / 2) * 0.22
            place(left, p, start + offset * BEAT)
            place(right, p * 0.9, start + offset * BEAT + 0.004)
        # 钟声琶音：每小节往上走一遍三和弦再落回五度，左右轮流
        notes = [semitone(root * 2, triad[0]), semitone(root * 2, triad[1]), semitone(root * 2, triad[2]),
                 semitone(root * 4, triad[0]), semitone(root * 2, triad[2]), semitone(root * 2, triad[1])]
        for i, freq in enumerate(notes):
            b = bell(freq) * (0.11 if i != 3 else 0.14)
            at = start + i * (4 * BEAT / len(notes))
            if i % 2 == 0:
                place(left, b, at)
                place(right, b * 0.55, at + 0.012)
            else:
                place(right, b, at)
                place(left, b * 0.55, at + 0.012)
    stereo = np.stack([left, right], axis=1)
    peak = np.max(np.abs(stereo))
    return stereo / peak * 0.85


def check(stereo):
    ok = True
    if abs(len(stereo) / SR - SECONDS) > 1e-6:
        print("时长不对：%.4f 秒" % (len(stereo) / SR))
        ok = False
    peak = float(np.max(np.abs(stereo)))
    if peak > 0.95:
        print("峰值过高：%.3f" % peak)
        ok = False
    for ch in range(2):
        x = stereo[:, ch]
        inner = float(np.max(np.abs(np.diff(x))))
        seam = float(abs(x[0] - x[-1]))
        print("声道 %d：接缝跳变 %.5f，段内最大跳变 %.5f" % (ch, seam, inner))
        if seam > inner:
            ok = False
        joined = np.concatenate([x[-SR:], x[:SR]])
        w = int(0.05 * SR)
        rms_seam = np.sqrt(np.mean(joined[SR - w:SR + w] ** 2))
        # 接缝正好在小节线上：拿段内其余 11 条小节线前后同样长的窗口比
        others = []
        for bar in range(1, BARS):
            c = int(round(bar * 4 * BEAT * SR))
            others.append(np.sqrt(np.mean(x[c - w:c + w] ** 2)))
        rms_bars = float(np.mean(others))
        db = 20 * np.log10((rms_seam + 1e-12) / (rms_bars + 1e-12))
        print("声道 %d：接缝前后 50 毫秒响度比段内小节线处平均 %+.2f dB" % (ch, db))
        if abs(db) > 3.0:
            ok = False
    return ok


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    stereo = compose()
    ok = check(stereo)
    print("峰值 %.3f，时长 %.1f 秒" % (float(np.max(np.abs(stereo))), len(stereo) / SR))
    if not ok:
        print("检查没过")
        return 1
    if not args.check:
        os.makedirs(os.path.dirname(OUT), exist_ok=True)
        # 分块写：libsndfile 一次写进很长一段 Vorbis 会直接崩掉进程
        data = stereo.astype(np.float32)
        with sf.SoundFile(OUT, "w", SR, 2, format="OGG", subtype="VORBIS") as f:
            for i in range(0, len(data), 8192):
                f.write(data[i:i + 8192])
        print("写出", os.path.relpath(OUT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
