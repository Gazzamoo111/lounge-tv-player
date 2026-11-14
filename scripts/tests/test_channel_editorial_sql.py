"""Execute the actual Room query in SQLite against a representative customer category."""
import re, sqlite3, unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
class EditorialChannelOrderTest(unittest.TestCase):
    def setUp(self):
        patch = (ROOT / 'scripts/patches/channel-editorial-order.patch').read_text()
        tier = re.search(r'const val TIER = "([^"]+)"', patch)[1]
        line = next(x for x in patch.splitlines() if x.startswith('+    @Query(') and 'LIMIT :limit' not in x)
        parts = re.findall(r'"([^"]+)"', line)
        self.query = parts[0] + tier + parts[1]
        self.db = sqlite3.connect(':memory:')
        self.db.executescript('''CREATE TABLE channels(id INTEGER,categoryId INTEGER,name TEXT,epgChannelId TEXT,sortOrder INTEGER);
        CREATE TABLE content_order(itemId INTEGER,profileId INTEGER,mediaType TEXT,contextKey TEXT,position INTEGER);
        CREATE TABLE epg_programmes(epgChannelId TEXT,startMs INTEGER,stopMs INTEGER);''')
        self.db.executemany('INSERT INTO channels VALUES(?,1,?,?,?)',[(1,'24/7 KIDS 1',None,0),(2,'DISNEY+ CLASSIC',None,1),(3,'HBO FAMILY','hbo',2),(4,'Other channel',None,3),(5,'HBO FAMILY BACKUP','hbo',4),(6,'Stale guide','stale',5)])
        self.db.executemany('INSERT INTO epg_programmes VALUES(?,?,?)',[('hbo',0,2000),('stale',0,500)])
        self.args=dict(categoryId=1,profileId=1,contextKey='kids',alpha=1,nowMs=1000)
    def order(self):return [r[0] for r in self.db.execute(self.query,self.args)]
    def test_curated_and_current_guide_precede_anonymous_and_backups(self):
        self.assertEqual([2,3,4,6,1,5],self.order())
    def test_explicit_moves_keep_precedence(self):
        self.db.execute("INSERT INTO content_order VALUES(1,1,'LIVE','kids',0)")
        self.assertEqual([1,2,3,4,6,5],self.order())
    def test_other_profile_moves_do_not_leak(self):
        self.db.execute("INSERT INTO content_order VALUES(1,2,'LIVE','kids',0)")
        self.assertEqual([2,3,4,6,1,5],self.order())
