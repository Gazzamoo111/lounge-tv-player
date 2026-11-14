package tv.own.owntv.features.sports

/** Evidence describes how a suggestion was found. None of these establish the video content. */
internal enum class SportsCandidateEvidence { EVENT_NAME, BROADCASTER, UNVERIFIED }

internal fun backendCandidateEvidence(confidence: String): SportsCandidateEvidence = when (confidence) {
    "definite" -> SportsCandidateEvidence.EVENT_NAME // Deployed v9 uses names, not EPG or playback.
    "likely" -> SportsCandidateEvidence.BROADCASTER
    else -> SportsCandidateEvidence.UNVERIFIED
}

/** Live event suggestions must not include channels explicitly advertising retrospective content. */
internal fun isRetrospectiveSportsFeed(name: String): Boolean =
    Regex("\\b(?:replay|replays|highlights|classic|classics)\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)
