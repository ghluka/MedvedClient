from pathlib import Path
import re

root = Path(r'c:\Users\lukam\OneDrive\Documents\GitHub\MedvedClient')
base = root / 'docs' / 'features' / 'modules'
orders = {
    'combat': ['KillAura','TriggerBot','AimAssist','LeftClicker','RightClicker','NoHitDelay','AutoBlock','HitSelect','Velocity','Refill','ComboTap','KnockbackDelay','Reach','KnockbackDisplacement','Backtrack','Criticals','AutoRod'],
    'movement': ['Sprint','Speed','Flight','Timer','NoFall'],
    'player': ['FakeLag','Blink','ClientBrand'],
    'world': ['Scaffold','Clutch','FastPlace','AutoPlace','BedBreaker','ChestAura'],
    'other': ['ClickGui','Font','Colour','Rotations','TargetFilter'],
    'hud': ['ModulesList','TargetHud','ScaffoldInfo'],
    'utility': ['AntiFireball','AutoTotem'],
}
pattern = re.compile(r'^(---\s*\n)(sidebar_position:\s*)(\d+)(\s*\n)', re.MULTILINE)
changed = []
for category, names in orders.items():
    folder = base / category
    if not folder.exists():
        print(f'Missing category folder: {folder}')
        continue
    for idx, name in enumerate(names, start=1):
        path = folder / f'{name}.mdx'
        if not path.exists():
            print(f'Missing MDX file: {path}')
            continue
        text = path.read_text(encoding='utf-8')
        def repl(m):
            current = int(m.group(3))
            if current != idx:
                return m.group(1) + m.group(2) + str(idx) + m.group(4)
            return m.group(0)
        newtext, n = pattern.subn(repl, text, count=1)
        if n == 0:
            print(f'No sidebar_position header found in {path}')
            continue
        if newtext != text:
            path.write_text(newtext, encoding='utf-8')
            changed.append((path.relative_to(root), idx))

print('Updated:', len(changed))
for p, idx in changed:
    print(p, idx)
