import sys
from pathlib import Path
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from qa_version_code import qa_version_code
from verify_update_compatibility import normalize_digest, parse_badging, parse_signer_digest
from version_code import version_code


class VersioningTests(unittest.TestCase):
    def test_current_production_and_next_hotfix(self):
        self.assertEqual(version_code('5.2.2.0'), 5_020_200)
        self.assertEqual(version_code('5.2.2.1'), 5_020_201)

    def test_qa_codes_are_reserved_and_monotonic(self):
        first = qa_version_code(41, 1)
        retry = qa_version_code(41, 2)
        next_run = qa_version_code(42, 1)
        self.assertGreater(first, 5_020_200)
        self.assertLess(first, retry)
        self.assertLess(retry, next_run)

    def test_invalid_version_segments_fail(self):
        with self.assertRaises(ValueError):
            version_code('5.2.2')
        with self.assertRaises(ValueError):
            version_code('5.100.0.0')


class ApkOutputParsingTests(unittest.TestCase):
    def test_badging_parser(self):
        output = "package: name='com.loungetv.player.qa' versionCode='1000004201' versionName='5.2.2.1-ci.42.1-qa' platformBuildVersionName=''"
        self.assertEqual(
            parse_badging(output),
            ('com.loungetv.player.qa', 1_000_004_201, '5.2.2.1-ci.42.1-qa'),
        )

    def test_signer_parser_normalizes_digest(self):
        digest = 'ab' * 32
        output = f'Signer #1 certificate SHA-256 digest: {digest.upper()}\n'
        self.assertEqual(parse_signer_digest(output), digest)
        self.assertEqual(normalize_digest(':'.join(['ab'] * 32)), digest)

    def test_signer_parser_accepts_current_apksigner_format(self):
        digest = '75' * 32
        output = f'V2 Signer: certificate SHA-256 digest: {digest}\n'
        self.assertEqual(parse_signer_digest(output), digest)

    def test_signer_parser_rejects_multiple_signers(self):
        output = (
            f'Signer #1 certificate SHA-256 digest: {"ab" * 32}\n'
            f'Signer #2 certificate SHA-256 digest: {"cd" * 32}\n'
        )
        with self.assertRaises(ValueError):
            parse_signer_digest(output)


if __name__ == '__main__':
    unittest.main()
