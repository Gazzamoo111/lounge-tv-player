#!/usr/bin/env python3
"""Regression tests: seven-day horizon, offsets, preservation, honest 24/7 labels."""
import gzip
import json
from datetime import datetime, timezone, timedelta
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from lxml import etree

sys.path.insert(0, str(Path(__file__).parent))
from seven_day_targeted import dt, select_feed, source_index

NOW = datetime(2026, 11, 5, tzinfo=timezone.utc)


class EpgTargetedTest(unittest.TestCase):
    def test_xmltv_timezone_offset(self):
        self.assertEqual(dt("20261105130000 +1300"), NOW)
        self.assertEqual(dt("20261105000000 +0000"), NOW)
        self.assertEqual(dt("20261105060000 +0600"), NOW)

    def test_reject_short_real_au_schedule(self):
        root = etree.fromstring(
            b'<tv><channel id="Channel9.au"><display-name>Channel 9</display-name></channel>'
            b'<programme channel="Channel9.au" start="20261105000000 +0000" '
            b'stop="20261107000000 +0000"><title>TV</title></programme></tv>')
        self.assertIsNone(select_feed(
            {"name": "Channel 9", "epgId": "Channel9.au"},
            {"primary": source_index(root, NOW, NOW + timedelta(days=7))},
            NOW, NOW + timedelta(days=7)))

    def test_exact_au_fox_sports_ids_from_epgshare(self):
        au = etree.Element("tv")
        for cid in ("FoxCricket.au", "FoxSportsNews.au"):
            ch = etree.SubElement(au, "channel", id=cid)
            etree.SubElement(ch, "display-name").text = cid
            for day in range(4):
                start = NOW + timedelta(days=day)
                p = etree.SubElement(au, "programme", channel=cid,
                    start=start.strftime("%Y%m%d%H%M%S +0000"),
                    stop=(start + timedelta(days=1)).strftime("%Y%m%d%H%M%S +0000"))
                etree.SubElement(p, "title").text = "Verified programming"
        index = source_index(au, NOW, NOW + timedelta(days=7))
        for name, epg_id in (("FOX CRICKET", "FOXCricket.au"),
                             ("Fox Sports News Australia", "FoxSportsNews.au")):
            match = select_feed({"name": name, "epgId": epg_id},
                {"epgshare": index}, NOW, NOW + timedelta(days=7))
            self.assertIsNotNone(match, name)
            self.assertEqual(match[2], "epgshare")
            self.assertEqual(match[3].casefold(), epg_id.casefold())
        mapped_501 = select_feed(
            {"name": "FOX SPORTS 501", "epgId": "FoxSports1.au"},
            {"epgshare": index}, NOW, NOW + timedelta(days=7))
        self.assertIsNotNone(mapped_501)
        self.assertEqual(mapped_501[3], "FoxCricket.au")
        self.assertIsNone(select_feed(
            {"name": "FOX SPORTS 504", "epgId": "FoxSports4.au"},
            {"epgshare": index}, NOW, NOW + timedelta(days=7)))

    def test_three_real_days_accepted(self):
        au = etree.Element("tv")
        c = etree.SubElement(au, "channel", id="Channel9.au")
        etree.SubElement(c, "display-name").text = "Channel 9"
        for day in range(3):
            start = NOW + timedelta(days=day)
            p = etree.SubElement(au, "programme", channel="Channel9.au",
                start=start.strftime("%Y%m%d%H%M%S +0000"),
                stop=(start + timedelta(days=1)).strftime("%Y%m%d%H%M%S +0000"))
            etree.SubElement(p, "title").text = "Real programme"
        found = select_feed({"name": "Channel 9", "epgId": "Channel9.au"},
            {"primary": source_index(au, NOW, NOW + timedelta(days=7))},
            NOW, NOW + timedelta(days=7))
        self.assertIsNotNone(found)
        self.assertEqual(found[0], NOW + timedelta(days=3))

    def test_less_than_three_real_days_rejected(self):
        au = etree.fromstring(
            b'<tv><channel id="Channel9.au"><display-name>Channel 9</display-name></channel>'
            b'<programme channel="Channel9.au" start="20261105000000 +0000" '
            b'stop="20261107230000 +0000"><title>Real programme</title></programme></tv>')
        found = select_feed({"name": "Channel 9", "epgId": "Channel9.au"},
            {"primary": source_index(au, NOW, NOW + timedelta(days=7))},
            NOW, NOW + timedelta(days=7))
        self.assertIsNone(found)

    def test_prefers_seven_day_schedule_over_three_day(self):
        def feed(days):
            au = etree.Element("tv")
            c = etree.SubElement(au, "channel", id="Channel9.au")
            etree.SubElement(c, "display-name").text = "Channel 9"
            for day in range(days):
                start = NOW + timedelta(days=day)
                p = etree.SubElement(au, "programme", channel="Channel9.au",
                    start=start.strftime("%Y%m%d%H%M%S +0000"),
                    stop=(start + timedelta(days=1)).strftime("%Y%m%d%H%M%S +0000"))
                etree.SubElement(p, "title").text = "Actual programme"
            return source_index(au, NOW, NOW + timedelta(days=7))
        choice = select_feed({"name": "Channel 9", "epgId": "Channel9.au"},
            {"three": feed(3), "seven": feed(7)}, NOW, NOW + timedelta(days=7))
        self.assertEqual(choice[2], "seven")
        self.assertEqual(choice[0], NOW + timedelta(days=7))

    def test_three_day_candidate_reports_actual_horizon_without_fake_schedule(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            manifest = {"rows": (
                [{"category": "Australia", "name": "Channel 9", "epgId": "Channel9.au",
                  "key": "live:channel-9"}]
                + [{"category": "Australia", "name": f"Other {i}", "epgId": None,
                    "key": f"live:other-{i}"} for i in range(19)]
                + [{"category": "24/7", "name": f"Show {i}", "epgId": None,
                    "key": f"live:show-{i}"} for i in range(100)])}
            (base / "manifest.json").write_text(json.dumps(manifest))
            (base / "existing.xml").write_text("<tv/>")
            au = etree.Element("tv")
            c = etree.SubElement(au, "channel", id="Channel9.au")
            etree.SubElement(c, "display-name").text = "Channel 9"
            for day in range(3):
                start = NOW + timedelta(days=day)
                p = etree.SubElement(au, "programme", channel="Channel9.au",
                    start=start.strftime("%Y%m%d%H%M%S +0000"),
                    stop=(start + timedelta(days=1)).strftime("%Y%m%d%H%M%S +0000"))
                etree.SubElement(p, "title").text = f"Actual programme {day}"
            (base / "au.xml").write_bytes(etree.tostring(au))
            cmd = [sys.executable, str(Path(__file__).parent / "seven_day_targeted.py"),
                   "--manifest", str(base / "manifest.json"),
                   "--existing", str(base / "existing.xml"),
                   "--au", f"primary={base / 'au.xml'}", "--out", str(base / "out"),
                   "--now", NOW.isoformat()]
            done = subprocess.run(cmd, capture_output=True, text=True)
            self.assertEqual(done.returncode, 0, done.stdout + done.stderr)
            report = json.loads((base / "out/report.json").read_text())
            self.assertEqual(report["minimumRealDays"], 3)
            self.assertEqual(report["auChannelsWithThreeDaySource"], 1)
            self.assertEqual(report["auChannelsWithSevenDaySource"], 0)
            self.assertEqual(datetime.fromisoformat(report["requiredThroughUtc"]), NOW + timedelta(days=3))
            self.assertEqual(datetime.fromisoformat(report["preferredThroughUtc"]), NOW + timedelta(days=7))
            with gzip.open(base / "out/guide.xml.gz", "rb") as f:
                published = etree.parse(f).getroot()
            self.assertEqual(len(published.xpath('programme[@channel="Channel9.au"]')), 3)
            self.assertEqual(len(published.xpath('programme[@channel="live:show-0"]')), 7)

    def test_targeted_merge_no_other_regions_or_catalogue_changed(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            manifest = {"rows": (
                [{"category": "Australia", "name": "Channel 9", "epgId": "Channel9.au",
                  "key": "live:channel-9"}]
                + [{"category": "Australia", "name": f"Other {i}", "epgId": None,
                    "key": f"live:other-{i}"} for i in range(19)]
                + [{"category": "24/7", "name": f"KIDS SHOW {i}", "epgId": None,
                    "key": f"live:kids-{i}"} for i in range(100)]
                + [{"category": "United Kingdom", "name": "BBC One", "epgId": "BBCOne.uk",
                    "key": "live:bbc-one"}])}
            (base / "manifest.json").write_text(json.dumps(manifest))
            (base / "old.xml").write_bytes(
                b'<?xml version="1.0"?><tv><channel id="BBCOne.uk"/>'
                b'<programme channel="BBCOne.uk" start="20261105000000 +0000" '
                b'stop="20261105120000 +0000"><title>Leave unchanged</title></programme></tv>')
            au = etree.Element("tv")
            ch = etree.SubElement(au, "channel", id="Channel9.au")
            etree.SubElement(ch, "display-name").text = "Channel 9"
            for i in range(8):
                st = NOW + timedelta(days=i)
                en = st + timedelta(days=1)
                prog = etree.SubElement(au, "programme", channel="Channel9.au",
                    start=st.strftime("%Y%m%d%H%M%S +0000"),
                    stop=en.strftime("%Y%m%d%H%M%S +0000"))
                etree.SubElement(prog, "title").text = f"Programme {i}"
            (base / "au.xml").write_bytes(etree.tostring(au))
            cmd = [sys.executable, str(Path(__file__).parent / "seven_day_targeted.py"),
                   "--manifest", str(base / "manifest.json"),
                   "--existing", str(base / "old.xml"),
                   "--au", f"primary={base / 'au.xml'}", "--out", str(base / "out"),
                   "--now", NOW.isoformat()]
            result = subprocess.run(cmd, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            with gzip.open(base / "out" / "guide.xml.gz", "rb") as f:
                root = etree.parse(f).getroot()
            uk = root.xpath('programme[@channel="BBCOne.uk"]')
            self.assertEqual(len(uk), 1)
            self.assertEqual(uk[0].findtext("title"), "Leave unchanged")
            self.assertEqual(len(root.xpath('programme[@channel="Channel9.au"]')), 7)
            loop = root.xpath('programme[@channel="live:kids-0"]')
            self.assertEqual(len(loop), 7)
            self.assertTrue(all("24/7" in x.findtext("title") for x in loop))
            self.assertTrue(all("Individual movie and episode times" in x.findtext("desc")
                                for x in loop))
            report = json.loads((base / "out" / "report.json").read_text())
            self.assertEqual(report["auChannels"], 20)
            self.assertEqual(report["loopChannelsNewlyCovered"], 100)
            self.assertEqual(report["untouchedExistingProgrammeEntries"], 1)
            self.assertEqual(report["auProgrammesAdded"], 7)

            # The second refresh must extend coverage, not leave a shrinking week.
            next_now = NOW + timedelta(hours=6)
            repeat = [sys.executable, str(Path(__file__).parent / "seven_day_targeted.py"),
                      "--manifest", str(base / "manifest.json"),
                      "--existing", str(base / "out" / "guide.xml.gz"),
                      "--au", f"primary={base / 'au.xml'}", "--out", str(base / "out2"),
                      "--now", next_now.isoformat()]
            again = subprocess.run(repeat, capture_output=True, text=True)
            self.assertEqual(again.returncode, 0, again.stdout + again.stderr)
            with gzip.open(base / "out2" / "guide.xml.gz", "rb") as f:
                fresh = etree.parse(f).getroot()
            loop_after = fresh.xpath('programme[@channel="live:kids-0"]')
            self.assertEqual(len(loop_after), 8)
            ends = [dt(p.get("stop")) for p in loop_after]
            self.assertGreaterEqual(max(ends), next_now + timedelta(days=7))
            self.assertEqual(fresh.xpath('programme[@channel="BBCOne.uk"]')[0]
                             .findtext("title"), "Leave unchanged")


if __name__ == "__main__":
    unittest.main()
