#!/usr/bin/env python3
"""Offline EPG hub acceptance tests: no URLs or external services required."""
import gzip
import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from lxml import etree as E
from hub import build, catalogue, norm

NOW = datetime(2026, 11, 12, tzinfo=timezone.utc)


def stamp(dt):
    return dt.strftime("%Y%m%d%H%M%S +0000")


def xml(catalogue, duration):
    root = E.Element("tv")
    for cid, name in catalogue:
        ch = E.SubElement(root, "channel", id=cid)
        E.SubElement(ch, "display-name").text = name
        for day in range(duration):
            for half in (0, 12):
                start = NOW + timedelta(days=day, hours=half)
                p = E.SubElement(root, "programme", channel=cid, start=stamp(start),
                                 stop=stamp(start + timedelta(hours=12)))
                E.SubElement(p, "title").text = name + " actual programme"
    return E.tostring(root)


class HubTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.home = Path(self.tmp.name)
        self.rows = [
            {"category": "New Zealand", "name": "TVNZ 1", "epgId": "mjh-tvnz-1"},
            {"category": "New Zealand", "name": "SKY SPORT 1", "epgId": "SkySport1.nz"},
            {"category": "Australia", "name": "ABC", "epgId": "ABC.au"},
            {"category": "Entertainment", "sourceName": "UK| BBC One", "name": "BBC One", "epgId": "BBCOne.uk"},
            {"category": "Entertainment", "sourceName": "US| TNT", "name": "TNT", "epgId": "TNT.us"},
            {"category": "24/7", "name": "LOOP", "epgId": "live:loop"},
            {"category": "Streaming", "name": "NETFLIX", "epgId": "live:netflix"},
        ]
        self.manifest = self.home / "manifest.json"
        self.manifest.write_text(json.dumps({"rows": self.rows}))
        self.existing = self.home / "existing.xml.gz"
        old = E.Element("tv")
        el = E.SubElement(old, "programme", channel="BBCOne.uk", start=stamp(NOW),
                          stop=stamp(NOW + timedelta(days=8)))
        E.SubElement(el, "title").text = "Protected UK schedule"
        with gzip.open(self.existing, "wb") as f:
            f.write(E.tostring(old))
        self.sources = []

    def add_source(self, label, country, rows, days):
        p = self.home / (label + ".xml.gz")
        with gzip.open(p, "wb") as f:
            f.write(xml(rows, days))
        self.sources.append({"id": label, "region": country, "path": str(p)})

    def run_build(self):
        return build(self.manifest, self.existing, self.sources, self.home / "out", NOW)

    def test_three_days_accepted_seven_preserved(self):
        self.add_source("nz", "nz", [("mjh-tvnz-1", "TVNZ 1"),
                                     ("S1", "SKY SPORT 1 HD")], 3)
        self.add_source("au", "au", [("ABC.au", "ABC")], 7)
        self.add_source("uk", "uk", [("BBCOne.uk", "BBC One")], 7)
        self.add_source("us-short", "us", [("TNT.us", "TNT")], 2)
        report = self.run_build()
        self.assertEqual(report["minimumRealDays"], 3)
        self.assertEqual(report["before"]["7day"], 1)
        self.assertEqual(report["after"]["3day"], 4)
        self.assertEqual(report["after"]["7day"], 2)
        self.assertEqual(report["byRegion"]["nz"]["after"]["3day"], 2)
        self.assertEqual(report["byRegion"]["us"]["after"]["now"], 0)
        root = E.parse(str(self.home / "out/guide.xml")).getroot()
        self.assertEqual(len(root.xpath('programme[@channel="TNT.us"]')), 0)
        self.assertEqual(len(root.xpath('programme[@channel="ABC.au"]')), 14)
        self.assertEqual(root.xpath('programme[@channel="BBCOne.uk"]')[0].findtext("title"),
                         "Protected UK schedule")

    def test_longer_feed_preferred_and_no_duplicate_programmes(self):
        self.add_source("short", "au", [("ABC.au", "ABC")], 3)
        self.add_source("long", "au", [("ABC.au", "ABC")], 7)
        report = self.run_build()
        self.assertEqual(report["after"]["7day"], 2)
        root = E.parse(str(self.home / "out/guide.xml")).getroot()
        self.assertEqual(len(root.xpath('programme[@channel="ABC.au"]')), 14)

    def test_conflicting_shared_epg_ids_are_rejected(self):
        self.rows.insert(1, {"category": "New Zealand", "name": "Different",
                             "epgId": "mjh-tvnz-1"})
        self.manifest.write_text(json.dumps({"rows": self.rows}))
        self.add_source("nz", "nz", [("mjh-tvnz-1", "TVNZ 1")], 7)
        report = self.run_build()
        self.assertIn("mjh-tvnz-1", report["conflictingEpgIds"])
        self.assertEqual(report["changedTargets"], 0)

    def test_wrong_country_id_and_ambiguous_name_blocked(self):
        self.add_source("au", "au", [("ABC.us", "ABC"),
                                     ("abc2", "ABC")], 7)
        report = self.run_build()
        self.assertEqual(report["changedTargets"], 0)
        self.assertEqual(report["after"]["3day"], 1)

    def test_fails_closed_when_no_feed_can_parse(self):
        self.add_source("bad", "nz", [("mjh-tvnz-1", "TVNZ 1")], 7)
        (self.home / "bad.xml.gz").write_bytes(b"invalid gzip")
        with self.assertRaisesRegex(ValueError, "No valid XMLTV sources"):
            self.run_build()
        self.assertFalse((self.home / "out/report.json").exists())

    def test_us_uk_loops_not_reported_as_scheduled_broadcast_channels(self):
        self.rows.extend([
            {"category": "Entertainment", "subcategory": "CINEMANIA TV SHOWS",
             "sourceName": "US| US TV", "name": "CHIPS", "epgId": "live:chips"},
            {"category": "Movies", "subcategory": "Sky Store",
             "sourceName": "UK| SKY STORE", "name": "Sky Store Premiere 1",
             "epgId": "live:sky-store-premiere-1"},
            {"category": "Streaming", "subcategory": "Pluto TV",
             "name": "BLACK CINEMA", "epgId": "BlackCinema.us"},
        ])
        self.manifest.write_text(json.dumps({"rows": self.rows}))
        targets, conflicts, _ = catalogue(self.manifest)
        self.assertNotIn("live:chips", targets)
        self.assertNotIn("live:sky-store-premiere-1", targets)
        self.assertIn("BlackCinema.us", targets)
        self.assertNotIn("live:netflix", targets)

    def test_reviewed_uk_duplicate_broadcasters_share_schedule_only_when_identical(self):
        self.rows.extend([
            {"category": "Entertainment", "subcategory": "UK TV",
             "name": "SKY ARTS", "epgId": "SkyArts.uk"},
            {"category": "Entertainment", "subcategory": "UK Now TV",
             "name": "SKY ARTS 1", "epgId": "SkyArts.uk"},
            {"category": "Entertainment", "subcategory": "UK TV",
             "name": "CHANNEL 4", "epgId": "Channel4.uk"},
            {"category": "Entertainment", "subcategory": "UK TV",
             "name": "CHANNEL 4SEVEN", "epgId": "Channel4.uk"},
            {"category": "Sports", "subcategory": "UK Sports",
             "name": "SKY SPORTS F1", "epgId": "SkySportsF1.uk"},
            {"category": "Sports", "subcategory": "UK Sports",
             "name": "SKY SPORTS DARTS", "epgId": "SkySportsF1.uk"},
        ])
        self.manifest.write_text(json.dumps({"rows": self.rows}))
        targets, conflicts, _ = catalogue(self.manifest)
        self.assertIn("SkyArts.uk", targets)
        self.assertNotIn("SkyArts.uk", conflicts)
        self.assertIn("Channel4.uk", conflicts)
        self.assertIn("SkySportsF1.uk", conflicts)
        self.assertNotIn("Channel4.uk", targets)
        self.assertNotIn("SkySportsF1.uk", targets)

    def test_reviewed_alias_blocked_if_a_third_different_channel_shares_id(self):
        self.rows.extend([
            {"category": "Entertainment", "subcategory": "UK TV",
             "name": "SKY ARTS", "epgId": "SkyArts.uk"},
            {"category": "Entertainment", "subcategory": "UK Now TV",
             "name": "SKY ARTS 1", "epgId": "SkyArts.uk"},
            {"category": "Entertainment", "subcategory": "UK TV",
             "name": "SKY ARTS +1", "epgId": "SkyArts.uk"},
        ])
        self.manifest.write_text(json.dumps({"rows": self.rows}))
        targets, conflicts, _ = catalogue(self.manifest)
        self.assertIn("SkyArts.uk", conflicts)
        self.assertNotIn("SkyArts.uk", targets)

    def test_multi_region_exact_ids_match_without_cross_market_leakage(self):
        self.add_source("seven", "multi", [
            ("TNT.us", "TNT"),
            ("BBCOne.uk", "BBC One"),
            ("ABC.au", "ABC"),
            ("mjh-tvnz-1", "TVNZ 1"),
        ], 3)
        result = self.run_build()
        self.assertEqual(result["byRegion"]["us"]["after"]["3day"], 1)
        self.assertEqual(result["byRegion"]["au"]["after"]["3day"], 1)
        self.assertEqual(result["byRegion"]["nz"]["after"]["3day"], 1)
        self.assertIn("seven", result["sourceContributions"])
        # The 8-day UK guide must remain untouched.
        self.assertEqual(result["byRegion"]["uk"]["after"]["7day"], 1)

    def test_multi_region_unqualified_name_never_guessed(self):
        self.add_source("seven", "multi", [
            ("generic_ABC", "ABC"),
            ("generic_TNT", "TNT"),
            ("generic_Sky", "SKY SPORT 1 HD"),
        ], 7)
        result = self.run_build()
        self.assertEqual(result["changedTargets"], 0)
        self.assertEqual(result["after"]["3day"], 1)

    def test_multi_region_explicit_country_name_accepted(self):
        self.add_source("seven", "multi", [
            ("US| TNT HD", "US| TNT HD"),
            ("UK| BBC One", "UK| BBC One"),
            ("AU| ABC", "AU| ABC"),
        ], 7)
        result = self.run_build()
        self.assertEqual(result["byRegion"]["us"]["after"]["3day"], 1)
        self.assertEqual(result["byRegion"]["au"]["after"]["3day"], 1)
        self.assertEqual(result["byRegion"]["uk"]["after"]["7day"], 1)

    def test_multi_region_conflicting_country_id_is_skipped(self):
        self.add_source("seven", "multi", [
            ("TNT.us", "UK| TNT"),
            ("ABC.au", "US| ABC"),
        ], 7)
        result = self.run_build()
        self.assertEqual(result["changedTargets"], 0)

    def test_complete_gap_report_includes_on_air_short_horizons(self):
        # Existing TNT has real listings now but only for two days, so it MUST
        # still appear in the missing-three-day report (not just missing-now).
        with gzip.open(self.existing, "rb") as f:
            existing = E.fromstring(f.read())
        short = E.fromstring(xml([("TNT.us", "TNT")], 2))
        for ch in short.findall("channel"):
            existing.append(ch)
        for prog in short.findall("programme"):
            existing.append(prog)
        with gzip.open(self.existing, "wb") as f:
            f.write(E.tostring(existing))
        self.add_source("nz", "nz", [("mjh-tvnz-1", "TVNZ 1")], 3)
        report = self.run_build()
        gaps = {entry["id"]: entry for entry in report["uncovered3day"]}
        self.assertEqual(gaps["TNT.us"]["gapReason"], "short-horizon")
        self.assertEqual(gaps["ABC.au"]["gapReason"] if "ABC.au" in gaps else "outside-uk-us", "outside-uk-us")
        self.assertEqual(report["byRegion"]["us"]["after"]["3day"], 0)

    def test_gap_report_is_not_truncated_to_150_channels(self):
        from hub import uncovered_three_day
        rows = {f"Missing{i}.us": {"name": f"Missing {i}", "region": "us",
                 "category": "Entertainment", "subcategory": "US TV"} for i in range(201)}
        gaps = uncovered_three_day({}, rows, NOW, 3)
        self.assertEqual(len(gaps), 201)
        self.assertTrue(all(x["gapReason"] == "no-current-listing" for x in gaps))

    def test_normalisation_retains_variants(self):
        self.assertNotEqual(norm("Sky Sport 1 +1"), norm("Sky Sport 1"))
        self.assertNotEqual(norm("TNT West"), norm("TNT East"))
        self.assertEqual(norm("NZ| SKY SPORT 1 HD"), norm("Sky Sport 1"))


if __name__ == "__main__":
    unittest.main()
