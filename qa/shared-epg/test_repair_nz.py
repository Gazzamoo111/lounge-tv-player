import gzip
import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from lxml import etree as E
from repair_nz import CORE, choose, make, name_key

NOW = datetime(2026, 11, 12, 0, tzinfo=timezone.utc)


def stamp(value):
    return value.strftime("%Y%m%d%H%M%S +0000")


def guide(channels, hours=7 * 24):
    root = E.Element("tv")
    for cid, name in channels:
        node = E.SubElement(root, "channel", id=cid)
        E.SubElement(node, "display-name").text = name
        for i in range(hours // 12):
            prog = E.SubElement(root, "programme", channel=cid,
                                start=stamp(NOW + timedelta(hours=12 * i)),
                                stop=stamp(NOW + timedelta(hours=12 * (i + 1))))
            E.SubElement(prog, "title").text = f"{name} programme {i}"
    return E.tostring(root)


def doc(programmes):
    root = E.Element("tv")
    for cid, start, stop, title in programmes:
        ch = E.SubElement(root, "programme", channel=cid, start=stamp(start),
                          stop=stamp(stop))
        E.SubElement(ch, "title").text = title
    return E.tostring(root)


class NzRepairTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.home = Path(self.tmp.name)
        names = {"mjh-tvnz-1": "TVNZ 1", "mjh-tvnz-2": "TVNZ 2",
                 "mjh-three": "Three", "mjh-maori-tv": "Whakaata Māori",
                 "mjh-te-reo": "Te Reo"}
        self.rows = [dict(name=names[cid], epgId=cid, category="New Zealand")
                     for cid in sorted(CORE)]
        self.rows.append(dict(name="SKY SPORT 1", epgId="SkySport1.nz",
                              category="New Zealand"))
        self.rows.append(dict(name="BBC One", epgId="BBCOne.uk",
                              category="Entertainment"))
        (self.home / "manifest.json").write_text(json.dumps({"rows": self.rows}))
        self.nz = self.home / "nz.xml.gz"
        self.nz1 = self.home / "nz1.xml.gz"
        with gzip.open(self.nz, "wb") as f:
            f.write(guide([(c["epgId"], c["name"]) for c in self.rows[:5]]))
        with gzip.open(self.nz1, "wb") as f:
            f.write(guide([("SkySport1.nz", "SKY SPORT 1")]))
        self.existing = self.home / "existing.xml.gz"
        old = NOW - timedelta(days=10)
        self.data = doc([("BBCOne.uk", NOW, NOW + timedelta(hours=4), "UK untouched"),
                         ("mjh-tvnz-1", old, old + timedelta(hours=1), "Expired NZ")])
        with gzip.open(self.existing, "wb") as f:
            f.write(self.data)

    def run_build(self):
        return make(self.home / "manifest.json", self.existing,
                    [("mjh", self.nz), ("nz1", self.nz1)], self.home / "out", NOW)

    def test_restores_five_freeview_and_sky_without_touching_uk(self):
        result = self.run_build()
        self.assertEqual(result["coreNowCovered"], 5)
        self.assertEqual(result["matchedChannels"], 6)
        self.assertEqual(result["prunedExpired"], 1)
        root = E.parse(str(self.home / "out/guide.xml")).getroot()
        self.assertEqual([p.findtext("title") for p in root.findall("programme")
                          if p.get("channel") == "BBCOne.uk"], ["UK untouched"])
        self.assertFalse(any(p.findtext("title") == "Expired NZ"
                             for p in root.findall("programme")))
        self.assertEqual(len(list((self.home / "out/short").glob("*.json"))), 6)
        self.assertGreater(result["addedProgrammes"], 70)

    def test_invalid_source_does_not_publish_candidate(self):
        self.nz.write_bytes(b"not XML")
        with self.assertRaises(ValueError):
            self.run_build()
        self.assertFalse((self.home / "out/guide.xml.gz").exists())

    def test_missing_core_channel_aborts_candidate(self):
        with gzip.open(self.nz, "wb") as f:
            f.write(guide([(c["epgId"], c["name"]) for c in self.rows[:4]]))
        with self.assertRaisesRegex(ValueError, "core current coverage incomplete"):
            self.run_build()

    def test_cross_category_epg_collision_is_blocked(self):
        rows = self.rows + [dict(name="Different broadcaster", category="Sports",
                                 epgId="SkySport1.nz")]
        (self.home / "manifest.json").write_text(json.dumps({"rows": rows}))
        result = self.run_build()
        self.assertIn("SkySport1.nz", result["conflicts"])
        self.assertEqual(result["matchedChannels"], 5)
        self.assertFalse((self.home / "out/short" / "other").exists())

    def test_nz_broadcaster_number_not_collapsed(self):
        self.assertNotEqual(name_key("SKY SPORT 1 HD"), name_key("SKY SPORT 11 HD"))

    def test_ambiguous_nz1_name_skips_non_core(self):
        with gzip.open(self.nz1, "wb") as f:
            f.write(guide([("SkyA", "SKY SPORT 1"), ("SkyB", "SKY SPORT 1")]))
        result = self.run_build()
        self.assertEqual(result["matchedChannels"], 5)


if __name__ == "__main__":
    unittest.main()
