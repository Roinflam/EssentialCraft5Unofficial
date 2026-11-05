#!/usr/bin/env python3
"""机器工作音效合成器：用 numpy 现算，不依赖任何录音，输出单声道 OGG（Minecraft 只对单声道做空间定位）。

用法：python tools/gen/machine_sounds.py [--check] [--only 名字,名字]
  生成到 src/main/resources/assets/essentialcraft5/sounds/machine/*.ogg；声音事件定义在 tools/sounds/machines.json。
  --check  不写文件，只做数值检查（峰值、环形片段的接缝是否平滑、时长）。
  --only   只写这几个文件（数值检查照样全做）。Vorbis 编码每次写出来的字节不一样，加新声音时用它，别把没改的旧文件也改一遍。

环形片段（*_hum / *_loop / *_swirl 等）的做法：
  内容是周期恰好 1.0 秒的信号（音调用整数赫兹，噪声用随机相位的频谱反变换），截成 1.1 秒、首尾各 0.1 秒线性淡入淡出，
  游戏里每 20 刻（正好 1.0 秒）重播一次，相邻两段在接缝处重叠 0.1 秒；因为两段重叠区的内容完全相同，
  线性淡入淡出之和恒为 1，接缝处听不出音量起伏。--check 会把两段按这个方式叠起来量一遍。
所有随机数都用固定种子，重跑结果一致。
"""
import argparse
import os
import sys

import numpy as np
import soundfile as sf

SR = 44100
LOOP_SECONDS = 1.0
OVERLAP = 0.1
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources",
                   "assets", "essentialcraft5", "sounds", "machine")


# ------------------------------------------------------------------ 基础件

def t_axis(seconds):
    return np.arange(int(round(seconds * SR))) / SR


def tone(freq, seconds=LOOP_SECONDS, phase=0.0):
    return np.sin(2 * np.pi * freq * t_axis(seconds) + phase)


def periodic_noise(lo, hi, rng, seconds=LOOP_SECONDS, tilt=0.0):
    """周期 = seconds 的带通噪声：在 [lo, hi] 赫兹里给每条谱线随机相位。tilt > 0 时高频衰减（1/f^tilt）"""
    n = int(round(seconds * SR))
    spectrum = np.zeros(n // 2 + 1, dtype=complex)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    band = (freqs >= lo) & (freqs <= hi)
    mag = np.ones(band.sum())
    if tilt:
        mag = (np.maximum(freqs[band], 1.0) / max(lo, 1.0)) ** (-tilt)
    spectrum[band] = mag * np.exp(1j * rng.uniform(0, 2 * np.pi, band.sum()))
    sig = np.fft.irfft(spectrum, n)
    return sig / (np.max(np.abs(sig)) + 1e-12)


def am(signal, mod_hz, depth, phase=0.0):
    """整数赫兹的调幅，保持 1 秒周期"""
    t = t_axis(len(signal) / SR)
    return signal * (1 - depth + depth * 0.5 * (1 + np.sin(2 * np.pi * mod_hz * t + phase)))


def exp_decay(seconds, tau):
    return np.exp(-t_axis(seconds) / tau)


def fade(sig, head, tail):
    out = sig.copy()
    h = int(head * SR)
    t = int(tail * SR)
    if h:
        out[:h] *= np.linspace(0, 1, h)
    if t:
        out[-t:] *= np.linspace(1, 0, t)
    return out


def normalize(sig, peak):
    return sig * (peak / (np.max(np.abs(sig)) + 1e-12))


def make_loop(periodic, peak):
    """1.0 秒周期信号 → (1.1 秒片段, 按同一比例缩放后的周期信号)。片段首尾各 0.1 秒线性淡入淡出"""
    n = int(round(LOOP_SECONDS * SR))
    assert len(periodic) == n
    extra = int(round(OVERLAP * SR))
    clip = np.concatenate([periodic, periodic[:extra]])
    clip = fade(clip, OVERLAP, OVERLAP)
    scale = peak / (np.max(np.abs(clip)) + 1e-12)
    return clip * scale, periodic * scale


def bandpass_fft(sig, lo, hi):
    spec = np.fft.rfft(sig)
    f = np.fft.rfftfreq(len(sig), 1 / SR)
    spec[(f < lo) | (f > hi)] = 0
    return np.fft.irfft(spec, len(sig))


# ------------------------------------------------------------------ 环形音

def reactor_hum():
    rng = np.random.default_rng(101)
    s = 0.40 * tone(25) + 1.00 * tone(50, phase=0.3) + 0.55 * tone(100, phase=1.1) + 0.30 * tone(150, phase=2.0) + 0.12 * tone(200)
    s = am(s, 2, 0.12)
    flow = am(periodic_noise(150, 900, rng, tilt=0.5), 3, 0.35)
    whine = 0.03 * tone(1600) * (0.6 + 0.4 * tone(1))
    return make_loop(s / 2.4 + 0.30 * flow + whine, 0.55)


def radiation_hum():
    rng = np.random.default_rng(102)
    s = 0.30 * tone(60) + 0.50 * tone(120, phase=0.5) + 0.35 * tone(240, phase=1.3) + 0.20 * tone(360, phase=2.2)
    s = am(s, 7, 0.28)
    hiss = 0.10 * periodic_noise(2000, 4500, rng)
    return make_loop(s / 1.4 + hiss, 0.42)


def earth_rumble():
    rng = np.random.default_rng(103)
    low = periodic_noise(20, 120, rng, tilt=1.0)
    s = 0.9 * low + 0.5 * am(tone(40) + 0.6 * tone(55, phase=0.8), 1, 0.35)
    grind = 0.12 * am(periodic_noise(300, 800, rng), 5, 0.6)
    return make_loop(s / 1.5 + grind, 0.6)


def electric_hum():
    rng = np.random.default_rng(104)
    s = sum(tone(100 * k, phase=0.4 * k) / k for k in range(1, 9))
    buzz = 0.06 * am(periodic_noise(3000, 8000, rng), 25, 0.6)
    return make_loop(s / 2.2 + buzz, 0.4)


def arcane_shimmer():
    rng = np.random.default_rng(105)
    s = np.zeros(int(SR * LOOP_SECONDS))
    for i, f in enumerate((1200, 1500, 1800, 2100, 2400, 2700)):
        s += am(tone(f, phase=rng.uniform(0, 6.28)), 1 + i % 3, 0.9, phase=rng.uniform(0, 6.28)) / (1 + i * 0.15)
    return make_loop(s / 4, 0.22)


def enchant_swirl():
    rng = np.random.default_rng(106)
    pad = 0.5 * tone(330) + 0.45 * tone(495, phase=1.0) + 0.35 * tone(660, phase=2.1) + 0.2 * tone(990)
    pad = am(pad, 2, 0.25)
    air = am(periodic_noise(2000, 6500, rng, tilt=0.3), 1, 0.85) * 0.5
    return make_loop(pad / 1.6 + 0.25 * air, 0.3)


def fire_roar():
    """炉火：宽带的低沉呼呼声，3 赫兹起伏 + 7 赫兹的高频跳动（火苗），底下垫一点 45 / 70 赫兹"""
    rng = np.random.default_rng(107)
    body = am(periodic_noise(60, 1200, rng, tilt=0.8), 3, 0.25)
    flicker = 0.18 * am(periodic_noise(1500, 4000, rng, tilt=0.4), 7, 0.7)
    low = 0.25 * am(tone(45) + 0.5 * tone(70, phase=0.7), 2, 0.3)
    return make_loop(body + flicker + low, 0.45)


def magma_bubble():
    """岩浆：很低的翻滚声 + 7 个往上滑的冒泡声。气泡落在周期里的随机位置，越过结尾的部分折回开头，整段仍是 1 秒周期"""
    rng = np.random.default_rng(108)
    n = int(round(SR * LOOP_SECONDS))
    bed = 0.6 * periodic_noise(25, 220, rng, tilt=1.0)
    bubbles = np.zeros(n)
    for _ in range(7):
        at = int(rng.uniform(0, 1) * n)
        dur = rng.uniform(0.035, 0.07)
        m = int(dur * SR)
        tt = np.arange(m) / SR
        f0 = rng.uniform(140, 260)
        f1 = f0 * rng.uniform(1.6, 2.4)
        phase = 2 * np.pi * np.cumsum(f0 + (f1 - f0) * (tt / dur)) / SR
        bubbles[(at + np.arange(m)) % n] += np.sin(phase) * np.sin(np.pi * tt / dur) ** 2 * rng.uniform(0.5, 1.0)
    return make_loop(bed + 0.8 * bubbles, 0.5)


def frost_hiss():
    """霜气：细的高频嘶声 + 三个一明一暗的玻璃音（A6 / D7 / F7）+ 一层低一点的风"""
    rng = np.random.default_rng(109)
    hiss = am(periodic_noise(3500, 10000, rng, tilt=0.6), 1, 0.5)
    glass = np.zeros(int(round(SR * LOOP_SECONDS)))
    for i, f in enumerate((1760, 2349, 2794)):
        glass += am(tone(f, phase=rng.uniform(0, 6.28)), 1 + i, 0.95, phase=rng.uniform(0, 6.28)) / (2 + i)
    wind = am(periodic_noise(400, 1600, rng, tilt=0.5), 1, 0.6, phase=1.5)
    return make_loop(0.5 * hiss + 0.35 * glass + 0.4 * wind, 0.3)


def void_draw():
    """虚空吸引：55 / 82 / 110 赫兹的低鸣按 2 赫兹搏动，一层每秒一次的「吸气」噪声，1320 / 1336 赫兹拍出 16 赫兹的颤音"""
    rng = np.random.default_rng(110)
    drone = am(tone(55) + 0.7 * tone(82, phase=0.9) + 0.4 * tone(110, phase=2.0), 2, 0.45)
    suck = 0.35 * am(periodic_noise(300, 2500, rng, tilt=0.7), 1, 0.9)
    shimmer = 0.06 * am(tone(1320) + tone(1336), 1, 0.8)
    return make_loop(drone / 2.1 + suck + shimmer, 0.45)


def crystal_resonance():
    """水晶共鸣：C5 和高 4 赫兹的一条拍出颤动，叠 G5 / C6 / G6，上面一层很淡的空气声"""
    rng = np.random.default_rng(111)
    s = np.zeros(int(round(SR * LOOP_SECONDS)))
    for f, a in ((523, 1.0), (527, 0.8), (784, 0.6), (1046, 0.45), (1569, 0.25)):
        s += a * tone(f, phase=rng.uniform(0, 6.28))
    air = 0.12 * periodic_noise(4000, 9000, rng)
    return make_loop(am(s, 1, 0.3) / 3 + air, 0.25)


# ------------------------------------------------------------------ 单发音

def geiger(seed):
    rng = np.random.default_rng(200 + seed)
    n = int(0.05 * SR)
    t = np.arange(n) / SR
    burst = rng.standard_normal(n) * np.exp(-t / (0.0007 + 0.0002 * seed))
    burst = bandpass_fft(burst, 1500, 7000)
    ping = np.sin(2 * np.pi * (2800 + 350 * seed) * t) * np.exp(-t / 0.004)
    sig = burst / (np.max(np.abs(burst)) + 1e-12) + 0.5 * ping
    return normalize(fade(sig, 0.0, 0.01), 0.7)


def reactor_vent():
    rng = np.random.default_rng(210)
    seconds = 1.8
    t = t_axis(seconds)
    noise = bandpass_fft(rng.standard_normal(len(t)), 1500, 7000)
    env = np.minimum(1.0, t / 0.18) * np.exp(-np.maximum(0.0, t - 0.25) / 0.55)
    thump = np.sin(2 * np.pi * 58 * t) * np.exp(-t / 0.22) * 0.8
    sig = 0.7 * noise / (np.max(np.abs(noise)) + 1e-12) * env + thump
    return normalize(fade(sig, 0.005, 0.15), 0.6)


def bell(freq, seconds, tau, partials=(1.0, 2.76, 5.40, 8.93), amps=(1.0, 0.55, 0.28, 0.12)):
    t = t_axis(seconds)
    sig = np.zeros(len(t))
    for ratio, amp in zip(partials, amps):
        if freq * ratio > 16000:   # 太高的泛音折回低频会变成刺耳的杂音，直接不要
            continue
        sig += amp * np.sin(2 * np.pi * freq * ratio * t) * np.exp(-t / (tau / (1 + 0.5 * (ratio - 1))))
    return sig


def arcane_chime(seed):
    rng = np.random.default_rng(220 + seed)
    freq = (880.0, 1046.5, 1318.5)[seed % 3]
    sig = bell(freq, 1.6, 0.55)
    attack = rng.standard_normal(len(sig)) * np.exp(-t_axis(1.6) / 0.004) * 0.12
    return normalize(fade(sig + attack, 0.001, 0.2), 0.55)


def crystal_tink(seed):
    freq = (2637.0, 3136.0, 3520.0)[seed % 3]
    sig = bell(freq, 0.9, 0.28, partials=(1.0, 2.0, 3.3), amps=(1.0, 0.4, 0.15))
    return normalize(fade(sig, 0.0005, 0.12), 0.5)


def spark_crackle(seed):
    rng = np.random.default_rng(230 + seed)
    seconds = 0.22
    sig = np.zeros(int(seconds * SR))
    for _ in range(rng.integers(3, 7)):
        at = int(rng.uniform(0, 0.17) * SR)
        n = int(rng.uniform(0.003, 0.01) * SR)
        burst = rng.standard_normal(n) * np.exp(-np.arange(n) / SR / 0.0012)
        sig[at:at + n] += burst * rng.uniform(0.4, 1.0)
    sig = bandpass_fft(sig, 1800, 9000)
    return normalize(fade(sig, 0.0, 0.03), 0.55)


def enchant_done():
    seconds = 1.5
    t = t_axis(seconds)
    sweep_t = np.minimum(t, 0.6)
    freq = 600 + (1800 - 600) * (sweep_t / 0.6) ** 1.5
    phase = 2 * np.pi * np.cumsum(freq) / SR
    glide = (np.sin(phase) + 0.3 * np.sin(2 * phase)) * np.minimum(1.0, t / 0.05) * np.exp(-np.maximum(0.0, t - 0.6) / 0.08)
    ring = np.roll(bell(1568.0, seconds, 0.6), int(0.55 * SR)) * (t >= 0.55)
    ring += 0.6 * np.roll(bell(2093.0, seconds, 0.5), int(0.62 * SR)) * (t >= 0.62)
    sig = 0.5 * glide + 0.7 * ring
    return normalize(fade(sig, 0.002, 0.2), 0.6)


def ascend_done():
    seconds = 3.6
    t = t_axis(seconds)
    boom = np.sin(2 * np.pi * 45 * t) * np.exp(-t / 0.9) * 1.0
    vibrato = 1 + 0.004 * np.sin(2 * np.pi * 5 * t)
    swell = np.clip(t / 1.5, 0, 1) ** 2 * np.exp(-np.maximum(0.0, t - 2.4) / 0.7)
    chord = np.zeros(len(t))
    for f, a in ((220.0, 0.8), (277.2, 0.5), (329.6, 0.6), (440.0, 0.7), (554.4, 0.4), (659.3, 0.45)):
        chord += a * np.sin(2 * np.pi * f * np.cumsum(vibrato) / SR) + 0.3 * a * np.sin(4 * np.pi * f * np.cumsum(vibrato) / SR)
    sparkle = np.zeros(len(t))
    for i, f in enumerate((2093.0, 2637.0, 3136.0, 4186.0)):
        sparkle += np.roll(bell(f, seconds, 0.7), int((1.6 + 0.18 * i) * SR)) * (t >= 1.6 + 0.18 * i)
    sig = 0.9 * boom + 0.5 * chord * swell / 2.5 + 0.35 * sparkle
    return normalize(fade(sig, 0.004, 0.4), 0.7)


# ------------------------------------------------------------------ 清单

LOOPS = {
    "reactor_hum": reactor_hum,
    "radiation_hum": radiation_hum,
    "earth_rumble": earth_rumble,
    "electric_hum": electric_hum,
    "arcane_shimmer": arcane_shimmer,
    "enchant_swirl": enchant_swirl,
    "fire_roar": fire_roar,
    "magma_bubble": magma_bubble,
    "frost_hiss": frost_hiss,
    "void_draw": void_draw,
    "crystal_resonance": crystal_resonance,
}

SHOTS = {
    **{f"geiger_{i}": (lambda i=i: geiger(i)) for i in range(4)},
    "reactor_vent": reactor_vent,
    **{f"arcane_chime_{i}": (lambda i=i: arcane_chime(i)) for i in range(3)},
    **{f"crystal_tink_{i}": (lambda i=i: crystal_tink(i)) for i in range(3)},
    **{f"spark_crackle_{i}": (lambda i=i: spark_crackle(i)) for i in range(3)},
    "enchant_done": enchant_done,
    "ascend_done": ascend_done,
}


def check_loop(name, clip, ideal):
    """把两段按「每 1.0 秒重播一次」叠起来，和无限循环的理想信号逐点比，返回最大误差（相对峰值的分贝，越小越好）"""
    n = int(LOOP_SECONDS * SR)
    mix = np.zeros(2 * n + len(clip))
    mix[:len(clip)] += clip
    mix[n:n + len(clip)] += clip
    reference = np.tile(ideal, 3)[:len(mix)]
    lo = int(OVERLAP * SR)
    hi = 2 * n   # 之后只剩第二段，没有叠加
    err = float(np.max(np.abs(mix[lo:hi] - reference[lo:hi])))
    return 20 * np.log10(err / (np.max(np.abs(clip)) + 1e-12) + 1e-12)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--only", default="")
    args = parser.parse_args()
    only = {name for name in args.only.split(",") if name}
    unknown = only - set(LOOPS) - set(SHOTS)
    if unknown:
        print("没有这些声音：" + ", ".join(sorted(unknown)))
        return 1

    def write(name, clip):
        if not args.check and (not only or name in only):
            sf.write(os.path.join(OUT, name + ".ogg"), clip.astype(np.float32), SR, format="OGG", subtype="VORBIS")
    os.makedirs(OUT, exist_ok=True)
    bad = False
    for name, fn in LOOPS.items():
        clip, ideal = fn()
        peak = float(np.max(np.abs(clip)))
        worst = check_loop(name, clip, ideal)
        print(f"{name:16s} {len(clip) / SR:5.2f}s 峰值 {peak:.2f} 接缝误差 {worst:7.1f} dB（低于 -60 才算接得上）")
        if worst > -60.0 or peak > 0.95:
            bad = True
        write(name, clip)
    for name, fn in SHOTS.items():
        clip = fn()
        peak = float(np.max(np.abs(clip)))
        print(f"{name:16s} {len(clip) / SR:5.2f}s 峰值 {peak:.2f}")
        if peak > 0.95 or not np.all(np.isfinite(clip)):
            bad = True
        write(name, clip)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
