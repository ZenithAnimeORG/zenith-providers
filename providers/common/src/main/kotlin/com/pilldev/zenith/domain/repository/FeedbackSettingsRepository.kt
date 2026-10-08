package com.pilldev.zenith.domain.repository

public interface FeedbackSettingsRepository {
    public fun isFeedbackDisabled(): Boolean = false

    public fun setFeedbackDisabled(disabled: Boolean) {}

    public fun isFeedbackPostponed(): Boolean = false

    public fun postponeFeedback(days: Int = 14) {}

    public fun shouldShowDopamineFeedback(): Boolean = false

    public fun recordEpisodeCompleted(): Int = 0

    public fun recordBookmarkAdded(): Int = 0

    public fun recordFeedbackPromptDismissed() {}
}
