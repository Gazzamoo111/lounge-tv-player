package tv.own.owntv.features.sports
import org.junit.Assert.*
import org.junit.Test
class SportsCarouselPolicyTest {
    @Test fun idleRotationWaitsTwentySeconds() {
        assertFalse(SportsCarouselPolicy.canRotate(19_999, 0, 3, false))
        assertTrue(SportsCarouselPolicy.canRotate(20_000, 0, 3, false))
    }
    @Test fun inputAndPreviewPauseRotation() {
        assertFalse(SportsCarouselPolicy.canRotate(21_000, 5_000, 3, false))
        assertFalse(SportsCarouselPolicy.canRotate(21_000, 0, 3, true))
        assertFalse(SportsCarouselPolicy.canRotate(21_000, 0, 1, false))
    }
}
