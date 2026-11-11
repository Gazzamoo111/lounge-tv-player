"""Exercise the staged Room snapshots in SQLite, including case-sensitive Live collation."""
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def staged_queries():
    added = '\n'.join(line[1:] for line in (ROOT / 'scripts/patches/guide-order-parity.patch').read_text().splitlines()
                      if line.startswith('+') and not line.startswith('+++'))
    return {name: ''.join(re.findall(r'"([^"]*)"', query))
            for query, name in re.findall(r'@Query\((.*?)\)\s*suspend fun (\w+)', added, re.S)}


class GuideOrderSqlTest(unittest.TestCase):
    def setUp(self):
        self.queries = staged_queries()
        self.db = sqlite3.connect(':memory:')
        self.db.executescript('''CREATE TABLE channels(id INTEGER, sourceId INTEGER, categoryId INTEGER, name TEXT, catchup INTEGER, sortOrder INTEGER);
            CREATE TABLE content_order(itemId INTEGER, profileId INTEGER, mediaType TEXT, contextKey TEXT, position INTEGER);
            CREATE TABLE custom_category_members(itemId INTEGER, profileId INTEGER, mediaType TEXT, contextKey TEXT);''')
        self.db.executemany('INSERT INTO channels VALUES(?,1,7,?,?,?)',
                            [(1, 'alpha', 1, 0), (2, 'Bravo', 0, 1), (3, 'Zulu', 1, 2)])
        self.params = dict(sourceIds=1, limit=50, profileId=1, contextKey='sports', categoryId=7)

    def ids(self, name):
        return [r[0] for r in self.db.execute(self.queries[name], self.params)]

    def test_all_alpha_uses_the_exact_live_query_collation(self):
        self.assertEqual([2, 3, 1], self.ids('snapshotAllAlpha'))
        self.assertEqual(self.db.execute('SELECT * FROM channels WHERE sourceId=1 ORDER BY name ASC').fetchall(),
                         self.db.execute(self.queries['snapshotAllAlpha'], self.params).fetchall())

    def test_catchup_alpha_excludes_noncatchup_with_the_same_collation(self):
        self.assertEqual([3, 1], self.ids('snapshotCatchupAlpha'))

    def test_manual_alpha_pins_moves_and_scopes_them_to_profile_and_context(self):
        self.db.execute("INSERT INTO content_order VALUES(1,1,'LIVE','sports',0)")
        self.assertEqual([1, 2, 3], self.ids('snapshotByCategoryManualAlpha'))
        self.params['profileId'] = 2
        self.assertEqual([2, 3, 1], self.ids('snapshotByCategoryManualAlpha'))

    def test_nz_manual_order_presence_is_profile_context_and_type_specific(self):
        query = self.queries['hasManualLiveOrder']
        self.db.execute("INSERT INTO content_order VALUES(1,2,'LIVE','sports',0)")
        self.db.execute("INSERT INTO content_order VALUES(1,1,'LIVE','other',0)")
        self.db.execute("INSERT INTO content_order VALUES(1,1,'MOVIE','sports',0)")
        self.assertEqual(0, self.db.execute(query, self.params).fetchone()[0])
        self.db.execute("INSERT INTO content_order VALUES(1,1,'LIVE','sports',0)")
        self.assertEqual(1, self.db.execute(query, self.params).fetchone()[0])

    def test_custom_alpha_preserves_its_own_move_and_membership(self):
        self.db.executemany("INSERT INTO custom_category_members VALUES(?,1,'LIVE','sports')", [(1,), (3,)])
        self.db.execute("INSERT INTO content_order VALUES(1,1,'LIVE','sports',0)")
        self.assertEqual([1, 3], self.ids('snapshotChannelsAlpha'))


if __name__ == '__main__':
    unittest.main()
