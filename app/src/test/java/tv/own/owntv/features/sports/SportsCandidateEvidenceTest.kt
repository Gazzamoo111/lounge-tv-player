package tv.own.owntv.features.sports

import org.junit.Assert.*
import org.junit.Test

class SportsCandidateEvidenceTest {
    @Test fun `backend definite means event name evidence only`() {
        assertEquals(SportsCandidateEvidence.EVENT_NAME, backendCandidateEvidence("definite"))
    }
    @Test fun `network and unknown hints remain unverified`() {
        assertEquals(SportsCandidateEvidence.BROADCASTER, backendCandidateEvidence("likely"))
        for (value in listOf("maybe", "verified", "", "new-server-value")) {
            assertEquals(SportsCandidateEvidence.UNVERIFIED, backendCandidateEvidence(value))
        }
    }
    @Test fun `explicit retrospective names are excluded from live suggestions`() {
        for (name in listOf("UFC Replay", "NBA HIGHLIGHTS HD", "ESPN Classics", "All Blacks Replays")) {
            assertTrue(name, isRetrospectiveSportsFeed(name))
        }
        for (name in listOf("Sky Sport 1 RAW", "NBA League Pass", "UFC Live", "Premier Sports")) {
            assertFalse(name, isRetrospectiveSportsFeed(name))
        }
    }
}
