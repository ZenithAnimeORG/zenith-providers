package com.pilldev.zenith.providers.builtin.repository

import com.pilldev.zenith.domain.model.AnitypeLoginResult
import com.pilldev.zenith.domain.repository.AnitypeRepository
import com.pilldev.zenith.providers.builtin.api.AnitypeApi
import com.pilldev.zenith.providers.builtin.api.isSubscriptionActive

public class DefaultAnitypeRepository(
    private val anitypeApi: AnitypeApi,
) : AnitypeRepository {
    override suspend fun checkSubscription(): Boolean = anitypeApi.getProfile().isSubscriptionActive()

    override suspend fun login(
        email: String,
        password: String,
    ): AnitypeLoginResult {
        val res = anitypeApi.login(email, password)
        return AnitypeLoginResult(
            access = res.access,
            refresh = res.refresh,
            refreshTime = res.refreshTime,
        )
    }
}
