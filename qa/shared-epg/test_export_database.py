import gzip,hashlib,json,tempfile,unittest
from pathlib import Path
from datetime import datetime,timezone
from export_database import export
class ExportTests(unittest.TestCase):
 def test_shards_integrity_expiry_and_unicode(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)
   with gzip.open(p/'guide.gz','wb')as f:f.write(('''<tv><channel id="Demo.us"><display-name>Demo &amp; Co</display-name></channel><programme channel="Demo.us" start="20261113000000 +0000" stop="20261113020000 +0000"><title>Whānau &amp; Friends</title></programme><programme channel="Demo.us" start="20261101000000 +0000" stop="20261101020000 +0000"><title>Expired</title></programme></tv>''').encode())
   m=export(p/'guide.gz','a'*40,p/'out',datetime(2026,11,13,1,tzinfo=timezone.utc))
   self.assertNotEqual(m['revision'],'a'*40);self.assertEqual(m['source_guide_revision'],'a'*40);self.assertEqual(m['channels'],1);self.assertEqual(m['programmes'],1);rows=[]
   for shard in m['shards']:
    raw=gzip.decompress((p/'out'/shard['file']).read_bytes());self.assertEqual(hashlib.sha256(raw).hexdigest(),shard['sha256']);rows+=json.loads(raw)['channels']
   self.assertIn('Whānau &amp; Friends',rows[0]['xmltv']);self.assertEqual(len(rows[0]['listings']),1)
