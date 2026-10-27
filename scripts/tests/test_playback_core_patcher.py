import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

import patch_lounge_playback_core as patcher


class PlaybackCorePatcherTests(unittest.TestCase):
    def make_pinned_core_fixture(self, root: Path) -> None:
        for relative, changes in patcher.PATCHES.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            snippets: list[str] = []
            earlier_afters: list[str] = []
            for before, _ in changes:
                if any(before in after for after in earlier_afters):
                    earlier_afters.append(_)
                    continue
                if any(before in existing for existing in snippets):
                    earlier_afters.append(_)
                    continue
                snippets = [existing for existing in snippets if existing not in before]
                snippets.append(before)
                earlier_afters.append(_)
            path.write_text("\n\n".join(snippets) + "\n", encoding="utf-8")

        live = "player-core/src/main/java/tv/own/owntv/player/LivePreviewEngine.kt"
        manifest = root / "tools/i18n/safe_literals.txt"
        manifest.parent.mkdir(parents=True, exist_ok=True)
        manifest.write_text(
            "# fixture\n\n"
            f"1\tlog\t{live}\tHTTP 458 while the previous engine's session was still open — waiting and retrying once\n"
            f"1\ttechnical\t{live}\tprovider refused with HTTP 458 (account session still in use) uri=${{HttpClient.redactUrl(uri)}}\n"
            f"1\ttechnical\t{live}\tended mid-live\n",
            encoding="utf-8",
        )

    def test_every_exact_replacement_applies_to_the_pinned_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_pinned_core_fixture(root)
            with patch.object(patcher.subprocess, "check_output", return_value=patcher.PIN):
                patcher.apply(root)

            for relative, changes in patcher.PATCHES.items():
                text = (root / relative).read_text(encoding="utf-8")
                for index, (before, after) in enumerate(changes):
                    if before not in after:
                        self.assertNotIn(before, text, relative)
                    if not any(later_before in after for later_before, _ in changes[index + 1:]):
                        self.assertIn(after, text, relative)

    def test_patcher_fails_closed_when_source_is_already_patched(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_pinned_core_fixture(root)
            with patch.object(patcher.subprocess, "check_output", return_value=patcher.PIN):
                patcher.apply(root)
                with self.assertRaises(SystemExit):
                    patcher.apply(root)


if __name__ == "__main__":
    unittest.main()
