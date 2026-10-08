package com.pilldev.zenith.domain.repository

import com.pilldev.zenith.provider.model.ProviderId
import kotlinx.coroutines.flow.Flow

public interface ProviderSettingsRepository {
    public fun getSetting(
        providerId: ProviderId,
        key: String,
        defaultValue: String? = null,
    ): String?

    public fun setSetting(
        providerId: ProviderId,
        key: String,
        value: String?,
    )

    public fun observeSetting(
        providerId: ProviderId,
        key: String,
        defaultValue: String? = null,
    ): Flow<String?>
}
