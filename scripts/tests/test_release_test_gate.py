import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class ReleaseTestGateTests(unittest.TestCase):
    def test_release_candidate_requires_the_secret_free_tests(self):
        release = (ROOT / '.github/workflows/publish.yml').read_text()
        self.assertIn('uses: ./.github/workflows/playback-tests.yml', release)
        self.assertRegex(release, r'(?m)^  candidate:\n    needs: tests\n')
        self.assertIn('ref: ${{ github.sha }}', release)

    def test_all_three_kotlin_suites_run_without_signing_secrets(self):
        tests = (ROOT / '.github/workflows/playback-tests.yml').read_text()
        self.assertNotIn('secrets.', tests)
        self.assertNotIn('Decode', tests)
        for task in (':shared-core:core:testDebugUnitTest',
                     ':shared-core:player-core:testDebugUnitTest',
                     ':app:testStandardDebugUnitTest'):
            self.assertIn(task, tests)
        self.assertIn('scripts/patch_lounge_playback_core.py shared-core', tests)

    def test_qa_build_cannot_bypass_the_tests(self):
        qa = (ROOT / '.github/workflows/ci.yml').read_text()
        self.assertRegex(qa, r'(?m)^  test:\n    needs: tests\n')
        self.assertIn("name='com.loungetv.player.auditqa'", qa)


if __name__ == '__main__':
    unittest.main()
