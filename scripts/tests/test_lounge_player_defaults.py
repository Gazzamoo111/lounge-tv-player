"""Guardrails for Lounge TV's user-tested factory playback order.

The actual Android and core build is validated by GitHub QA; these checks make
sure every settings surface shows the same defaults without overriding choices.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class PlayerFactoryDefaultTest(unittest.TestCase):
    def test_pinned_core_patch_contains_new_factory_defaults_and_preserves_legacy(self):
        patch = (ROOT / "scripts/patch_lounge_playback_core.py").read_text()
        self.assertIn('PATCHES["core/src/main/java/tv/own/owntv/core/settings/SettingsRepository.kt"]', patch)
        self.assertIn("?: tv.own.owntv.core.player.EnginePreference.MPV_FIRST", patch)
        self.assertIn("?: tv.own.owntv.core.player.EnginePreference.EXO_FIRST", patch)
        self.assertIn("prefs[Keys.VOD_PREFER_EXO]?.let", patch)
        self.assertIn("prefs[Keys.VOD_ENGINE]?.let", patch)
        self.assertIn("prefs[Keys.LIVE_ENGINE]?.let", patch)

    def test_settings_ui_and_vm_agree_on_order(self):
        vm = (ROOT / "app/src/main/java/tv/own/owntv/features/settings/SettingsViewModel.kt").read_text()
        ui = (ROOT / "app/src/main/java/tv/own/owntv/features/settings/VideoPlayerSettingsScreen.kt").read_text()
        self.assertIn("EnginePreference.EXO_FIRST)\n    fun setVodEnginePreference", vm)
        self.assertIn("EnginePreference.MPV_FIRST)\n    fun setLiveEnginePreference", vm)
        self.assertIn("recommended = EnginePreference.MPV_FIRST.ordinal", ui)
        self.assertIn("recommended = EnginePreference.EXO_FIRST.ordinal", ui)
        self.assertIn("engineOptions(default = EnginePreference.MPV_FIRST)", ui)
        self.assertIn("engineOptions(default = EnginePreference.EXO_FIRST)", ui)
        self.assertIn("val engine by vm.liveEnginePreference.collectAsStateWithLifecycle()\n            link(engineLabel(engine), engine != EnginePreference.MPV_FIRST)", ui)
        self.assertIn("val engine by vm.vodEnginePreference.collectAsStateWithLifecycle()\n            link(engineLabel(engine), engine != EnginePreference.EXO_FIRST)", ui)


if __name__ == "__main__":
    unittest.main()
