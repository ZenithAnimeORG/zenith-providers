package com.pilldev.zenith.providers.builtin.api

import com.pilldev.zenith.providers.builtin.api.ktorfit.AnitypeKtorfitApi
import com.pilldev.zenith.providers.builtin.api.ktorfit.createAnitypeKtorfitApi
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class AnitypeLoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class AnitypeLoginResponse(
    val access: String = "",
    val refresh: String = "",
    val refreshTime: Long = 0L,
)

@Serializable
data class AnitypeSubscriptionDto(
    val id: String? = null,
    val endDate: String? = null,
)

@Serializable
data class AnitypeUserResponse(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val sub: Boolean = false,
    val subscriptions: List<AnitypeSubscriptionDto> = emptyList(),
)

fun AnitypeUserResponse.isSubscriptionActive(): Boolean {
    if (sub) return true
    val now = Instant.fromEpochMilliseconds(
        kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
    )
    return subscriptions.any { subDto ->
        val endDateStr = subDto.endDate
        if (endDateStr.isNullOrBlank()) {
            false
        } else {
            try {
                val instant = Instant.parse(endDateStr)
                instant > now
            } catch (e: IllegalArgumentException) {
                try {
                    val localDate = LocalDate.parse(endDateStr)
                    val systemTimeZone = TimeZone.currentSystemDefault()
                    val instant = localDate.atStartOfDayIn(systemTimeZone)
                    instant > now
                } catch (ex: IllegalArgumentException) {
                    false
                }
            }
        }
    }
}

public open class AnitypeApi(
    private val ktorfitApi: AnitypeKtorfitApi,
) {
    public constructor(client: HttpClient) : this(
        Ktorfit
            .Builder()
            .httpClient(client)
            .baseUrl("https://anitype.ru/api/")
            .build()
            .createAnitypeKtorfitApi(),
    )

    public constructor() : this(HttpClient())

    public open suspend fun login(
        email: String,
        password: String,
    ): AnitypeLoginResponse = ktorfitApi.login(AnitypeLoginRequest(email, password))

    public open suspend fun getProfile(): AnitypeUserResponse = ktorfitApi.getProfile()
}
