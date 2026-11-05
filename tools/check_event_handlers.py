"""
事件处理器撞名检查：python tools/check_event_handlers.py（有撞名时退出码 1）。

Forge 47.x 的事件总线（eventbus 6.0.5）给 @SubscribeEvent 静态方法生成监听器类，类名是
「包名.__简单类名_方法名_事件类型简单名」（IEventListenerFactory.getUniqueName），ModLauncherFactory 按这个名字缓存、先到先得：
**同一个包里**两个不同的类只要简单类名、方法名、事件类型都相同（例如 loot 包里两个 Registration.register(RegisterEvent)），
后注册的那个会被当成前一个，**只有前一个会被调用（两次）**，后一个静默失效，不报任何错。不同包不会撞，所以跨模组也不会撞。
2026-09-25 实测：loot/FortuneScaledCount.Registration.register 和 loot/NotDisabledCondition.Registration.register 撞名，
前者被调用 148 次、后者 0 次，战利品条件没注册上。

所以内部事件类不要起 Events、Tooltips、Registration 这种大众名字，用 CacheEvents、ResistanceTooltips 这类带前缀的名字。

处理器归属于按大括号配对后包住它的最内层类（字符串、注释先抹掉），外层类里写在嵌套类后面的处理器不会被算到嵌套类头上。
"""
import collections
import glob
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'java')

CLASS_RE = re.compile(r'\b(?:class|record|enum|interface)\s+(\w+)')
HANDLER_RE = re.compile(
    r'@SubscribeEvent(?:\([^)]*\))?\s+(?:public\s+|protected\s+|private\s+)?static\s+void\s+(\w+)\s*\(\s*(?:final\s+)?([\w.<>?, ]+?)\s+\w+\s*\)')
# 字符串、字符字面量、注释（\x5c 是反斜杠）：算大括号层级前抹成等长空白，位置不变
NOISE_RE = re.compile(r'''"(?:\x5c.|[^"\x5c\n])*"|'(?:\x5c.|[^'\x5c\n])*'|//[^\n]*|/\*.*?\*/''', re.S)


def class_spans(text):
    """[(类体左括号位置, 类体右括号位置, 简单类名)]，按大括号配对认每个类声明的类体"""
    clean = NOISE_RE.sub(lambda m: ' ' * len(m.group(0)), text)
    spans = []
    for m in CLASS_RE.finditer(clean):
        open_pos = clean.find('{', m.end())
        if open_pos < 0:
            continue
        depth = 0
        for i in range(open_pos, len(clean)):
            if clean[i] == '{':
                depth += 1
            elif clean[i] == '}':
                depth -= 1
                if depth == 0:
                    spans.append((open_pos, i, m.group(1)))
                    break
    return spans


def main() -> int:
    handlers = collections.defaultdict(list)
    for path in glob.glob(os.path.join(ROOT, '**', '*.java'), recursive=True):
        with open(path, encoding='utf-8') as f:
            text = f.read()
        spans = class_spans(text)
        package_match = re.search(r'^package\s+([\w.]+);', text, re.M)
        package = package_match.group(1) if package_match else ''

        for m in HANDLER_RE.finditer(text):
            owners = [(start, name) for start, end, name in spans if start < m.start() < end]
            if not owners:
                continue
            owner = max(owners)[1]
            event = m.group(2).split('.')[-1].split('<')[0]
            handlers[(package, owner, m.group(1), event)].append(os.path.relpath(path, ROOT))
    clashes = {key: files for key, files in handlers.items() if len(files) > 1}
    for (package, owner, method, event), files in sorted(clashes.items()):
        print(f'撞名：{package}.{owner}.{method}({event}) 出现在 {", ".join(sorted(files))}——只有一个会被调用，改类名')
    print(f'{len(handlers)} 个处理器，{len(clashes)} 处撞名')
    return 1 if clashes else 0


if __name__ == '__main__':
    sys.exit(main())
