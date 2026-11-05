"""
恶魔五芒星的物品图标：把原作 512×512 的方块贴图缩成 32×32（同灵魂五芒星的 block/icons/playerpentacle.png，Lanczos 缩放）。

原作拿 512×512 的贴图直接当物品层，1.20 的 item/generated 会按像素边缘生成几万个面，所以另存一张小图给物品模型用。
用法：python tools/gen/demonic_pentacle_icon.py（重跑结果不变）。
"""
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'essentialcraft5',
                    'textures', 'block')
SOURCE = os.path.join(ROOT, 'demonicpentacle.png')
TARGET = os.path.join(ROOT, 'icons', 'demonicpentacle.png')
SIZE = 32


def main():
    image = Image.open(SOURCE).convert('RGBA')
    image.resize((SIZE, SIZE), Image.LANCZOS).save(TARGET)
    print('wrote', os.path.normpath(TARGET))


if __name__ == '__main__':
    main()
