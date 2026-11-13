import unittest,tempfile,gzip,json
from pathlib import Path
from datetime import datetime,timezone
from repair_missing import make,key,scoped_name,region,eligible
NOW=datetime(2026,11,7,1,tzinfo=timezone.utc)
def xml(cid,title='Show',hour=0):return f'<programme channel="{cid}" start="20261107{hour:02d}0000 +0000" stop="20261107{hour+2:02d}0000 +0000"><title>{title}</title></programme>'
class RepairTests(unittest.TestCase):
 def build(self,ids=('source',),names=('US - Demo HD',),titles=None,epgid='Demo.us',name='Demo',existing=''):
  with tempfile.TemporaryDirectory()as d:
   p=Path(d);m={'rows':[dict(id='1',name=name,category='Entertainment',subcategory='US TV',epg_id=epgid,epgId=epgid,region='us')]};(p/'manifest.json').write_text(json.dumps(m))
   with gzip.open(p/'base.gz','wb')as f:f.write(('<tv><channel id="SkySport1.nz"><display-name>Sky Sport 1</display-name></channel>'+xml('SkySport1.nz','NZ untouched')+existing+'</tv>').encode())
   ch=''.join(f'<channel id="{i}"><display-name>{n}</display-name></channel>'for i,n in zip(ids,names));pg=''.join(xml(i,(titles or ['Show']*len(ids))[j])+xml(i,'Next',2)for j,i in enumerate(ids))
   with gzip.open(p/'us.gz','wb')as f:f.write(('<tv>'+ch+pg+'</tv>').encode())
   r=make(p/'manifest.json',p/'base.gz',[('talk-us','us',p/'us.gz')],p/'out',NOW);out=(p/'out/guide.xml').read_text();shorts=[json.loads(f.read_text())for f in (p/'out/short').glob('*.json')];return r,out,shorts
 def test_appends_and_preserves_nz_and_emits_short(self):
  r,out,s=self.build();self.assertEqual(r['newlyCurrentTargets'],1);self.assertIn('NZ untouched',out);self.assertEqual(s[0]['epg_id'],'Demo.us');self.assertEqual(s[0]['epg_listings'][0]['now_playing'],1)
 def test_equivalent_sd_hd_allowed(self):self.assertEqual(self.build(('a','b'),('Demo','Demo HD'))[0]['newlyCurrentTargets'],1)
 def test_conflicting_sd_hd_rejected(self):self.assertEqual(self.build(('a','b'),('Demo','Demo HD'),['Show','Different'])[0]['modifiedTargets'],0)
 def test_plus_one_and_west_not_folded(self):
  self.assertEqual(self.build(name='Demo +1')[0]['modifiedTargets'],0);self.assertEqual(self.build(name='Demo West')[0]['modifiedTargets'],0)
 def test_existing_programmes_never_overwritten(self):
  r,out,s=self.build(existing=xml('Demo.us','Existing',0));self.assertEqual(r['newlyCurrentTargets'],0);self.assertIn('Existing',out);self.assertEqual(r['addedProgrammes'],1)
 def test_uk_generic_foreign_channel_not_assumed(self):
  self.assertFalse(scoped_name('Disney Channel','uk','talk-uk'));self.assertTrue(scoped_name('UK - Discovery History','uk','talk-uk'));self.assertTrue(scoped_name('Sky Sports Golf HD','uk','talk-uk'))
 def test_only_expired_managed_history_is_pruned(self):
  expired='<programme channel="Demo.us" start="20261103000000 +0000" stop="20261103020000 +0000"><title>Expired demo</title></programme>'
  r,out,_=self.build(existing=expired);self.assertEqual(r['prunedExpiredProgrammes'],1);self.assertNotIn('Expired demo',out);self.assertIn('NZ untouched',out)
 def test_provider_country_is_evidence_not_a_guess(self):
  self.assertEqual(region(dict(sourceName='UK-NOWTV| CARTOON NETWORK',region=None)), 'uk')
  self.assertFalse(eligible(dict(sourceName='US| DISCOVERY CHANNEL WEST',region='us',name='Discovery Channel West',category='Entertainment',subcategory='US TV',epg_id='DiscoveryTurbo.uk')))
 def test_quality_alias_keeps_delay(self):self.assertNotEqual(key('Sky Sports +'),key('Sky Sports'));self.assertEqual(key('TNT (Pacific)'),key('TNT West'))
if __name__=='__main__':unittest.main()

class FreeSourceTests(unittest.TestCase):
 def test_multicountry_source_requires_matching_country(self):
  from repair_missing import source_scope
  self.assertTrue(source_scope('AMC.HD.us2','us','all-us'))
  self.assertFalse(source_scope('AMC.HD.uk','us','all-us'))
  self.assertFalse(source_scope('Tennis.Dummy.us','us','all-us'))
  self.assertFalse(source_scope('AMC.plex','us','all-us'))
 def test_real_pluto_channels_eligible_but_private_streaming_slots_excluded(self):
  row=dict(name='TBN',category='Streaming',subcategory='Pluto TV',region='us',epg_id='TBN.us')
  self.assertTrue(eligible(row))
  row['subcategory']='Netflix';self.assertFalse(eligible(row))
