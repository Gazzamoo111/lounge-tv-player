from pathlib import Path
import re,json,xml.etree.ElementTree as ET,argparse
args=argparse.ArgumentParser(description='Import Android Settings rows and English resources.');args.add_argument('--android-root',type=Path,required=True);args.add_argument('--core-root',type=Path,required=True);opts=args.parse_args()
root=opts.android_root/'app/src/main'; strings={}
for folder in [opts.core_root/'core/src/main/res/values', root/'res/values']:
 for file in folder.glob('*.xml'):
  for e in ET.parse(file).getroot():
   if e.tag=='string': strings[e.attrib['name']]=''.join(e.itertext()).replace('\\n','\n').replace("\\'", "'")

def st(k): return strings.get(k,{"settings_auto_frame_rate":"Auto frame rate"}.get(k,k))
s=(root/'java/tv/own/owntv/features/shell/components/SettingsScreen.kt').read_text();s=s[s.index('val rootRows'):] if 'val rootRows' in s else s[s.index('RootGroup("group_quick"'):]
# Extract balanced constructor arguments, preserving source order.
def calls(text,name):
 for m in re.finditer(r'\b'+name+r'\(',text):
  start=m.end();n=1;quoted=False;esc=False;i=start
  while i<len(text) and n:
   c=text[i]
   if quoted:
    if esc:esc=False
    elif c=='\\':esc=True
    elif c=='"':quoted=False
   elif c=='"':quoted=True
   elif c=='(':n+=1
   elif c==')':n-=1
   i+=1
  yield m.start(),text[start:i-1]
groups=[('quick','Quick','The toggles you reach for most','SPARKLE'),('profile','Profile','Viewers, kids mode, PIN locks','PERSON'),('sources','Sources & guide','Playlists, EPG, reminders, catch-up','LIST'),('appearance','Appearance','Theme, accent, glass, text','PALETTE'),('layout','Layout','Navigation, panels, Home, menus','GRID'),('content_metadata','Content & metadata','Categories, TMDB, subtitles','MOVIE'),('player','Player','Which player, streaming','PLAY'),('picture','Picture','Decode, HDR, zoom, frame rate','VIDEO'),('sound','Sound & subtitles','Volume, surround, languages','AUDIO'),('live','Live TV','Preview, rewind, latency, start-up','LIVE_TV'),('watching','Watching & recording','Skip, resume, multiview, recording','PLAY'),('app','App','Language, updates, network, diagnostics','INFO')]
items=[];current='quick'
cs=sorted([(a,'g',b) for a,b in calls(s,'RootGroup')]+[(a,'r',b) for a,b in calls(s,'RootRow')])
for _,typ,b in cs:
 if typ=='g':
  m=re.search(r'"group_([^"]+)"',b)
  if m:current=m[1]
  continue
 title=re.search(r'title\s*=\s*stringResource\(R.string.(\w+)',b)
 if not title:continue
 key=re.match(r'\s*"([^"]+)"',b)
 tab=re.search(r'tabRowKey\(SettingsTab.(\w+)\)',b)
 if not key and not tab:continue
 desc=re.search(r'desc\s*=\s*stringResource\(R.string.(\w+)',b)
 items.append(dict(key=key[1] if key else 'tab_'+tab[1].lower(),group=current,title=st(title[1]),description=st(desc[1]) if desc else ''))
v=(root/'java/tv/own/owntv/features/settings/VideoPlayerSettingsScreen.kt').read_text();v=v[v.index('internal val VIDEO_QUICK_ROWS'):v.index('/**',v.index('internal val VIDEO_QUICK_ROWS')+10)]
for _,b in calls(v,'VideoQuickRef'):
 m=re.match(r'"([^"]+)",\s*(\w+),\s*OwnTVIcon.(\w+),\s*R.string.(\w+)(?:,\s*R.string.(\w+))?',b)
 if not m:continue
 g={'SECTION_PLAYER':'player','SECTION_STREAMING':'player','SECTION_PICTURE':'picture','SECTION_FRAME_RATE':'picture','SECTION_SOUND':'sound','SECTION_LANGUAGES':'sound','SECTION_LIVE':'live','SECTION_LIVE_TUNING':'live','SECTION_CONTROLS':'watching','SECTION_MULTIVIEW':'watching','SECTION_RESUME':'watching','SECTION_DIAGNOSTICS':'app'}[m[2]]
 items.append(dict(key=m[1],group=g,title=st(m[4]),description=st(m[5]) if m[5] else ''))
# Helper-generated rows are also real Android settings. Extract their canonical title resources.
for file,g in [('ProfileSettingsRows.kt','profile'),('RecordingSettingsScreen.kt','watching')]:
 text=(root/'java/tv/own/owntv/features/settings'/file).read_text()
 for key in dict.fromkeys(re.findall(r'title\s*=\s*stringResource\(R.string.(\w+)',text)):
  if key not in [x['title'] for x in items]: items.append(dict(key=key,group=g,title=st(key),description=''))
# There is no static profile row because Android builds one per actual profile.
items.append(dict(key='profiles',group='profile',title='Profiles',description='Each viewer keeps their own favorites, watch history, resume points and settings.'))
for x in items:
 if x['key'] in ['navigation','live_layout','live_opens_in']: x['group']='layout'
 if x['key'] in ['reminders','reminder_lead']: x['group']='sources'
seen=set();items=[x for x in items if x['key'] not in seen and not seen.add(x['key'])]
Path('src/settings-catalogue.json').write_text(json.dumps({'reference':'Android v5.2.3.10 44388fa8683df20a51ad5166f3ea73d7b08c8051','groups':[dict(id=g,title=t,description=d,icon=i) for g,t,d,i in groups],'rows':items},indent=2,ensure_ascii=False)+'\n')
print('Imported',len(items),'rows',[(g,sum(x['group']==g for x in items)) for g,*_ in groups])
