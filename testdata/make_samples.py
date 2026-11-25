#!/usr/bin/env python3
"""Generates sample packs for testing KyleMCTools on a phone. Run: python3 make_samples.py"""
import json, struct, zlib, zipfile, os

def png(rgb, size=16):
    raw = b"".join(b"\x00" + bytes(rgb) * size for _ in range(size))
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")

def j(o): return json.dumps(o, indent=2).encode()
here = os.path.dirname(os.path.abspath(__file__))

def write(name, files):
    with zipfile.ZipFile(os.path.join(here, name), "w", zipfile.ZIP_DEFLATED) as z:
        for k, v in files.items(): z.writestr(k, v)

# 1) Legacy pack (custom_model_data overrides, works for 1.14 - 1.21.3)
write("sample_legacy_pack.zip", {
    "pack.mcmeta": j({"pack": {"pack_format": 34, "description": "KMT legacy sample"}}),
    "pack.png": png((79, 70, 229), 64),
    "assets/minecraft/models/item/diamond_sword.json": j({
        "parent": "item/handheld", "textures": {"layer0": "item/diamond_sword"},
        "overrides": [
            {"predicate": {"custom_model_data": 1001}, "model": "custom/ruby_sword"},
            {"predicate": {"custom_model_data": 1002}, "model": "custom/emerald_sword"}]}),
    "assets/minecraft/models/item/paper.json": j({
        "parent": "item/generated", "textures": {"layer0": "item/paper"},
        "overrides": [{"predicate": {"custom_model_data": 2001}, "model": "custom/magic_scroll"}]}),
    "assets/minecraft/models/custom/ruby_sword.json": j({"parent": "item/handheld", "textures": {"layer0": "custom/ruby_sword"}}),
    "assets/minecraft/models/custom/emerald_sword.json": j({"parent": "item/handheld", "textures": {"layer0": "custom/emerald_sword"}}),
    "assets/minecraft/models/custom/magic_scroll.json": j({"parent": "item/generated", "textures": {"layer0": "custom/magic_scroll"}}),
    "assets/minecraft/textures/custom/ruby_sword.png": png((200, 30, 60)),
    "assets/minecraft/textures/custom/emerald_sword.png": png((30, 200, 90)),
    "assets/minecraft/textures/custom/magic_scroll.png": png((230, 210, 150)),
})

# 2) 1.21.4+ pack (item definitions + item_model), with a sound
write("sample_item_model_pack.zip", {
    "pack.mcmeta": j({"pack": {"pack_format": 46, "description": "KMT item_model sample"}}),
    "assets/kyle/items/ruby.json": j({"model": {"type": "minecraft:model", "model": "kyle:item/ruby"}}),
    "assets/kyle/items/crystal_staff.json": j({"model": {"type": "minecraft:model", "model": "kyle:item/crystal_staff"}}),
    "assets/kyle/models/item/ruby.json": j({"parent": "minecraft:item/generated", "textures": {"layer0": "kyle:item/ruby"}}),
    "assets/kyle/models/item/crystal_staff.json": j({"parent": "minecraft:item/handheld", "textures": {"layer0": "kyle:item/crystal_staff"}}),
    "assets/kyle/textures/item/ruby.png": png((220, 20, 60)),
    "assets/kyle/textures/item/crystal_staff.png": png((120, 90, 230)),
    "assets/kyle/sounds.json": j({"staff.cast": {"sounds": [{"name": "kyle:cast", "volume": 0.8}]}}),
    "assets/kyle/sounds/cast.ogg": b"OggS-fake-sample-data",
})

# 3) Pack wrapped in a root folder (common when zipped from a folder) + a 3D model + a broken JSON
write("sample_wrapped_with_problems.zip", {
    "MyPack/pack.mcmeta": j({"pack": {"pack_format": 34}}),
    "MyPack/assets/minecraft/models/item/stick.json": j({
        "overrides": [{"predicate": {"custom_model_data": 5}, "model": "custom/crate"}]}),
    "MyPack/assets/minecraft/models/custom/crate.json": j({
        "textures": {"all": "custom/crate"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"north": {"texture": "#all"}}}]}),
    "MyPack/assets/minecraft/textures/custom/crate.png": png((150, 100, 50)),
    "MyPack/assets/minecraft/models/item/broken.json": b"{ this is not json",
})

# 4) Things that must fail politely
with open(os.path.join(here, "sample_corrupt.zip"), "wb") as f: f.write(b"PK\x03\x04 this is not really a zip")
write("sample_not_a_pack.zip", {"readme.txt": b"just a text file"})
print("sample packs written to", here)

# ---- Phase 2 samples (optimizer / protector) ----
def png_rgba(colors, size=64, extra=True):
    raw = b"".join(b"\x00" + b"".join(bytes(colors[(x // 8 + y // 8) % len(colors)]) for x in range(size)) for y in range(size))
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    out = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    if extra:
        out += chunk(b"tEXt", b"Software\x00Some Editor " + b"x" * 400) + chunk(b"pHYs", struct.pack(">IIB", 2835, 2835, 1))
    return out + chunk(b"IDAT", zlib.compress(raw, 1)) + chunk(b"IEND", b"")

cols = [(200, 30, 60, 255), (30, 200, 90, 255), (20, 20, 20, 128), (240, 240, 240, 255)]
big_json = json.dumps({"parent": "item/generated", "textures": {"layer0": "kyle:item/ruby"}, "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}}}, indent=8).encode()
write("sample_bloated_pack.zip", {
    "pack.mcmeta": j({"pack": {"pack_format": 46, "description": "KMT optimizer sample"}}),
    "assets/kyle/items/ruby.json": j({"model": {"type": "minecraft:model", "model": "kyle:item/ruby"}}),
    "assets/kyle/models/item/ruby.json": big_json,
    "assets/kyle/textures/item/ruby.png": png_rgba(cols),
    "assets/kyle/textures/item/gem.png": png_rgba(cols[::-1]),
    "assets/kyle/textures/item/orphan_unused.png": png_rgba(cols),
    "assets/minecraft/textures/block/stone.png": png_rgba(cols),
    ".DS_Store": b"junk", "art/ruby_source.psd": b"P" * 20000, "Thumbs.db": b"junk",
})
write("sample_bedrock_pack.mcpack", {
    "manifest.json": j({"format_version": 2, "header": {"name": "KMT bedrock sample", "description": "x", "uuid": "11111111-1111-4111-8111-111111111111", "version": [1, 0, 0], "min_engine_version": [1, 16, 0]},
                        "modules": [{"type": "resources", "uuid": "22222222-2222-4222-8222-222222222222", "version": [1, 0, 0]}]}),
    "textures/item_texture.json": j({"resource_pack_name": "kmt", "texture_name": "atlas.items", "texture_data": {"ruby": {"textures": ["textures/items/ruby"]}}}),
    "textures/items/ruby.png": png_rgba(cols),
})
print("phase 2 samples written")

# ---- Phase 3 samples (MEG converter / version converter) ----
import base64
tiny = base64.b64encode(png((120, 90, 230), 16)).decode()
bb = {
  "meta": {"format_version": "4.10", "model_format": "free"}, "name": "sample_golem", "resolution": {"width": 64, "height": 32},
  "elements": [
    {"uuid": "c1", "type": "cube", "name": "body", "from": [-4, 0, -2], "to": [4, 12, 2], "origin": [0, 8, 0], "rotation": [0, 0, 0], "box_uv": False,
     "faces": {d: {"uv": [0, 0, 8, 12], "texture": 0} for d in ["north", "east", "south", "west", "up", "down"]}},
    {"uuid": "c2", "type": "cube", "name": "head", "from": [-3, 12, -3], "to": [3, 18, 3], "origin": [0, 12, 0], "rotation": [0, 0, 0], "box_uv": True, "uv_offset": [0, 16]}],
  "outliner": [{"uuid": "g1", "name": "body", "origin": [0, 8, 0], "rotation": [0, 0, 0], "children": ["c1",
               {"uuid": "g2", "name": "head", "origin": [0, 12, 0], "rotation": [0, 0, 0], "children": ["c2"]}]}],
  "textures": [{"name": "sample_golem.png", "source": "data:image/png;base64," + tiny}],
  "animations": [{"uuid": "a1", "name": "idle", "loop": "loop", "length": 2.0, "animators": {
      "g2": {"name": "head", "type": "bone", "keyframes": [
        {"channel": "rotation", "time": 0, "interpolation": "linear", "data_points": [{"x": 0, "y": 0, "z": 0}]},
        {"channel": "rotation", "time": 1, "interpolation": "catmullrom", "data_points": [{"x": 10, "y": 25, "z": 0}]},
        {"channel": "rotation", "time": 2, "interpolation": "linear", "data_points": [{"x": 0, "y": 0, "z": 0}]}]}}}]}
with open(os.path.join(here, "sample_model.bbmodel"), "w") as f: json.dump(bb, f, indent=1)
write("sample_old_pack_1_21.zip", {
    "pack.mcmeta": j({"pack": {"pack_format": 34, "description": "KMT version sample (1.21)"}}),
    "assets/minecraft/models/item/stick.json": j({"parent": "item/handheld", "textures": {"layer0": "item/stick"}, "overrides": [
        {"predicate": {"custom_model_data": 1}, "model": "custom/wand"}, {"predicate": {"custom_model_data": 2}, "model": "custom/staff"}]}),
    "assets/minecraft/models/custom/wand.json": j({"parent": "item/handheld", "textures": {"layer0": "custom/wand"}}),
    "assets/minecraft/models/custom/staff.json": j({"parent": "item/handheld", "textures": {"layer0": "custom/staff"}}),
    "assets/minecraft/textures/custom/wand.png": png((90, 200, 250)), "assets/minecraft/textures/custom/staff.png": png((250, 200, 90)),
    "assets/minecraft/textures/models/armor/iron_layer_1.png": png((180, 180, 190), 64),
    "assets/minecraft/textures/models/armor/iron_layer_2.png": png((170, 170, 180), 64),
    "assets/minecraft/textures/gui/widgets.png": png((60, 60, 60), 64),
})
print("phase 3 samples written")
