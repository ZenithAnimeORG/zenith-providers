package com.pilldev.zenith.domain.repository

import com.pilldev.zenith.domain.model.AnitypeLoginResult

public interface AnitypeRepository {
    public suspend fun checkSubscription(): Boolean

    public suspend fun login(
        email: String,
        password: String,
    ): AnitypeLoginResult
}
