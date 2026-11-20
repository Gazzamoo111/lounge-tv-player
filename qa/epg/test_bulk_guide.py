"""Regression tests for programme identity and preservation across EPG feeds."""
from argparse import Namespace
from datetime import datetime, timedelta, timezone
import gzip
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from lxml import etree

from bulk_guide import (Row, Source, add_schedule, baseline, build, candidates, coverage,
                        identity, load_catalogue, read_source, safe_match, timestamp)

NOW = datetime(2026, 11, 20, 0, tzinfo=timezone.utc)
END = NOW + timedelta(days=7)
START = NOW - timedelta(hours=12)
SOURCE = Source("share-us", "us", "", "")
FAST = Source("talk-fast", "us", "multi", "")
EPGENIUS = Source("epgenius-66", "", "", "", "epgenius")


class BulkGuideTest(unittest.TestCase):
    def test_country_timeshift_and_market_must_agree(self):
        row = Row("1", "HBO WEST HD", "US TV", "ltv-1")
        self.assertTrue(safe_match(row, SOURCE, "HBO.West.us", ["HBO West"], "name"))
        self.assertFalse(safe_match(row, SOURCE, "HBO.East.us", ["HBO East"], "name"))
        self.assertFalse(safe_match(row, SOURCE, "HBO.West.ca", ["HBO West"], "name"))
        self.assertFalse(safe_match(row, SOURCE, "HBO.West.us", ["HBO West +1"], "name"))
        self.assertFalse(safe_match(row, SOURCE, "HBO.West.us", ["Showtime West"], "exact"))
        self.assertEqual(identity("UK | BBC ONE ᴴᴰ", "uk"), identity("BBC 1", "uk"))

    def test_fast_requires_matching_platform_and_market(self):
        row = Row("1", "TUBI| NFL CHANNEL ᴿᴬᵂ", "FAST TV", "ltv-1")
        self.assertEqual(identity(row.name, "us"), identity("NFL Channel (Tubi)", "us"))
        self.assertTrue(safe_match(row, FAST, "121705.Tubi", ["NFL Channel (Tubi)"], "name"))
        self.assertFalse(safe_match(row, FAST, "121705.PlutoTV", ["NFL Channel"], "name"))
        self.assertFalse(safe_match(row, SOURCE, "NFLChannel.us", ["NFL Channel"], "name"))

    def test_shared_ids_cannot_add_schedules_for_two_services(self):
        rows = [Row("1", "HBO EAST HD", "US TV", "a"),
                Row("2", "HBO WEST HD", "US TV", "a"),
                Row("3", "HBO WEST HD", "24/7 Movie", "b")]
        _, index, _, why = candidates(rows, {}, {}, NOW)
        self.assertFalse(index)
        self.assertEqual(why, {"a": "shared-id-conflict", "b": "shared-id-conflict"})

    def test_timezone_and_overlap_preserve_original_windows(self):
        self.assertEqual(timestamp("20261120121500 +1245"), NOW - timedelta(minutes=30))
        self.assertIsNone(timestamp("20261120121500 +1260"))
        self.assertIsNone(timestamp("20261120121500 +1200 tail"))
        original = (NOW - timedelta(hours=1), NOW + timedelta(hours=1))
        bad = (NOW, NOW + timedelta(hours=2))
        new = (NOW + timedelta(hours=1), NOW + timedelta(hours=2))
        programmes = {original: b"original"}
        self.assertEqual(add_schedule(programmes, {bad: b"<programme/>", new: b'<programme channel="UPSTREAM"/>'},
                                      "ltv-042"), 1)
        self.assertEqual(programmes[original], b"original")
        self.assertEqual(etree.fromstring(programmes[new]).get("channel"), "ltv-042")

    def test_m3u_parser_keeps_emitted_names_ids_and_headers(self):
        with TemporaryDirectory() as tmp:
            file = Path(tmp) / "guide.m3u"
            file.write_text(
                '#EXTM3U\n#EXTINF:-1 group-title="07 📡 FAST TV" channel-id="ltv-3" tvg-id="a&amp;b",'
                'A, B\nhttp://example.invalid/asset\n')
            self.assertEqual(load_catalogue(file), [Row("ltv-3", "A, B", "FAST TV", "a&b")])

    def test_stale_and_ambiguous_xmltv_do_not_attach(self):
        row = Row("1", "Test Movie Channel", "US TV", "ltv-1")
        _, index, aliases, _ = candidates([row], {}, {}, NOW)
        channels = ('<channel id="id-a"><display-name>Test Movie Channel</display-name></channel>'
                    '<channel id="id-b"><display-name>Test Movie Channel HD</display-name></channel>')
        program = ('<programme channel="id-a" start="20261119230000 +0000" stop="20261120010000 +0000">'
                   '<title>First</title></programme>')
        with TemporaryDirectory() as tmp:
            file = Path(tmp) / "source.gz"
            file.write_bytes(gzip.compress(f"<tv>{channels}{program}</tv>".encode()))
            matches, reasons, _ = read_source(SOURCE, file, index, aliases, NOW, START, END)
            self.assertFalse(matches)
            self.assertEqual(reasons["ltv-1"], "ambiguous-in-source")

    def test_epgenius_rejects_alias_only_and_conflicting_markets(self):
        usa = Row("1", "USA NETWORK HD", "US TV", "ACCNetwork.us")
        self.assertFalse(safe_match(usa, EPGENIUS, "ACCNetwork.us",
                                    ["ACC Network", "USA Network"], "exact"))
        self.assertTrue(safe_match(usa, EPGENIUS, "usanetwork.us", ["USA Network"], "name"))
        au = Row("2", "beIN SPORTS AU 2", "UK Sports", "ltv-2")
        self.assertFalse(safe_match(au, EPGENIUS, "bein2.uk", ["beIN SPORTS AU 2"], "name"))

    def test_epgenius_requires_real_distinct_and_nonconflicting_listings(self):
        row = Row("1", "Test Movie Channel", "US TV", "TestMovie.us")
        _, index, aliases, _ = candidates([row], {}, {}, NOW)
        channel = '<channel id="TestMovie.us"><display-name>Test Movie Channel</display-name></channel>'
        with TemporaryDirectory() as tmp:
            file = Path(tmp) / "guide.xml"
            for first, second, next_start, expected in (
                ("Real Movie", "Another Movie", "20261120010000", True),
                ("Next Event: TBD", "Another Movie", "20261120010000", False),
                ("Real Movie", "Real Movie", "20261120010000", False),
                ("Real Movie", "Another Movie", "20261120003000", False),
            ):
                with self.subTest(first=first, second=second, next_start=next_start):
                    file.write_text(f'<tv>{channel}'
                                    f'<programme channel="TestMovie.us" start="20261119230000" stop="20261120010000"><title>{first}</title></programme>'
                                    f'<programme channel="TestMovie.us" start="{next_start}" stop="20261120020000"><title>{second}</title></programme></tv>')
                    matches, _, _ = read_source(EPGENIUS, file, index, aliases, NOW, START, END)
                    self.assertEqual(bool(matches), expected)

    def test_legacy_duplicate_channel_declarations_are_normalized(self):
        sample = ('<tv><channel id="guide"/><channel id="guide"/>'
                  '<programme channel="guide" start="20261119230000 +0000" stop="20261120010000 +0000">'
                  '<title>First</title></programme></tv>')
        with TemporaryDirectory() as tmp:
            file = Path(tmp) / "baseline.xml"
            file.write_text(sample)
            with self.assertRaisesRegex(ValueError, "duplicate"):
                baseline(file, START, END)
            channels, programmes, health = baseline(file, START, END, legacy=True)
            self.assertEqual(health["duplicate_channel_tags"], 1)
            results = coverage([Row("1", "TVNZ 1", "New Zealand", "guide"),
                                Row("2", "Continuing", "24/7 Movies", "guide")],
                               channels, programmes, NOW, {})
            self.assertEqual(results["categories"]["TOTAL"]["eligible"], 1)
            self.assertEqual(results["categories"]["TOTAL"]["current"], 1)

    def test_shared_output_retains_existing_continuous_labels(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            programmes = '''<tv><channel id="guide"/><channel id="loop"/>
                <programme channel="guide" start="20261119230000 +0000" stop="20261120010000 +0000">
                    <title>Scheduled show</title>
                </programme>
                <programme channel="loop" start="20261119230000 +0000" stop="20261120010000 +0000">
                    <title>24/7 • Continuous</title>
                </programme></tv>'''
            (base / "canonical.xml").write_text(programmes)
            (base / "sources.tsv").touch()
            (base / "catalogue.json").write_text(json.dumps([
                {"key": "1", "name": "TVNZ 1", "category": "New Zealand", "guide": "guide"},
                {"key": "2", "name": "Continuous", "category": "24/7 Movies", "guide": "loop"},
            ]))
            output = base / "out"
            build(Namespace(
                now=NOW.isoformat(), baseline=base / "canonical.xml",
                international_baseline=None, previous_bulk=None, before_profile=[],
                profile=[f"{p}={base / 'catalogue.json'}" for p in ("nz-au", "plus", "international")],
                sources=base / "sources.tsv", cache=base / "cache", out=output,
            ))
            with gzip.open(output / "guide.xml.gz") as guide:
                root = etree.parse(guide)
            self.assertEqual(root.findtext('programme[@channel="loop"]/title'), "24/7 • Continuous")
            report = json.loads((output / "report.json").read_text())
            self.assertEqual(report["guides"]["shared"]["programmes"], 2)
            self.assertEqual(report["after"]["nz-au"]["categories"]["TOTAL"]["current"], 1)


if __name__ == "__main__":
    unittest.main()
