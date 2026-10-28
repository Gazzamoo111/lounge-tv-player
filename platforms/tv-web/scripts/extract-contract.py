"""Extract the feature/wording contract from the real Android source, never a substitute UI.
Usage: CORE_STRINGS_DIR=/path/to/core-1.0.69/values python3 scripts/extract-contract.py
"""
from pathlib import Path
import json, os, re, subprocess, xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[3]
out = Path(__file__).resolve().parents[1] / 'src' / 'contract.json'
base = root / 'app/src/main/java/tv/own/owntv'
strings = {}
core = Path(os.environ['CORE_STRINGS_DIR'])
for folder in [core, root / 'app/src/main/res/values']:
    for file in sorted(folder.glob('*.xml')):
        tree = ET.parse(file)
        for item in tree.getroot().findall('string'):
            strings[item.attrib['name']] = ''.join(item.itertext()).replace('\\n', '\n').replace("\\'", "'")
groups = []
for ident, key in re.findall(r'^\s+(\w+)\(R.string.(\w+)', (base / 'features/shell/components/SettingsGroup.kt').read_text(), re.M):
    groups.append({'id':ident, 'label':strings.get(key,key), 'stringKey':key})
screens = []
for file in sorted((base / 'features').rglob('*.kt')):
    if 'Screen' not in file.stem and file.parent.name != 'settings':
        continue
    text=file.read_text()
    keys=sorted(set(re.findall(r'R.string.(\w+)',text)))
    screens.append({'id':file.stem,'source':str(file.relative_to(root)), 'area':str(file.relative_to(base/'features')).split('/')[0], 'status':'not_ported', 'strings':[{'key':k,'text':strings[k]} for k in keys if k in strings]})
contract={'androidCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(), 'androidVersion':'5.2.3.1', 'coreVersion':'1.0.69', 'coreTagCommit':'1de78b1e2690c22b50c060e986bce221454bf880', 'rail':['SEARCH','HOME','LIVE_TV','SPORTS','EPG','MOVIES','SERIES','DOWNLOADS','MORE'], 'settingsGroups':groups, 'screens':screens, 'homeGeometry':{'designWidth':1920,'designHeight':1080,'contentStart':150,'rowGap':40,'heroHeight':644,'compactHeroHeight':188,'noHeroTop':110}, 'palette':{'page':'#050A14','text':'#F1F5FC','muted':'#A9BAD4','dim':'#71839F','glassTint':'#0B1E35','glassOff':'#0C1829'}, 'more':['SETTINGS','FAVORITES','HISTORY','BACKUP','LOCAL_SYNC','ERROR_LOG','ABOUT','DEVELOPER'], 'notes':['This is a port inventory, not a claim that every screen has been implemented.','Android-specific capabilities must be mapped and tested per platform, never silently dropped.']}
out.write_text(json.dumps(contract,indent=2,ensure_ascii=False)+'\n')
print(f'{len(groups)} settings groups, {len(screens)} screen/settings source units tracked')
