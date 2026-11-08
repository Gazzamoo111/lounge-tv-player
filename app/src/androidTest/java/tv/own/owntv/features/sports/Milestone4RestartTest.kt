package tv.own.owntv.features.sports

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.StartupMode
import tv.own.owntv.core.i18n.LocaleStore

/** Run in a SECOND instrumentation process after am force-stop, following Milestone4UiTest. */
@RunWith(AndroidJUnit4::class)
class Milestone4RestartTest {
    @Test fun preferences_survive_process_restart() = runBlocking {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val s = SettingsRepository(c, LocaleStore.from(c))
        assertEquals("test", s.sportsConfig(700001L).first().teams.single().id)
        assertTrue(s.sportsConfig(700002L).first().teams.isEmpty())
        assertEquals(StartupMode.FAVORITES, s.startupMode(700001L).first())
        assertEquals(StartupMode.HOME, s.startupMode(700002L).first())
    }
}
