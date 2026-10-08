package com.pilldev.zenith.domain.model

import kotlinx.serialization.Serializable

@Serializable
public enum class MirrorAuthStatus {
    UNAUTHORIZED,
    AUTHORIZED,
    FAILED,
    NOT_APPLICABLE,
}

@Serializable
public data class HdRezkaMirrorProfile(
    val mirror: String,
    val guestCookies: String = "",
    val authCookies: String = "",
    val isGuestWorking: Boolean = false,
    val isAuthWorking: Boolean = false,
    val authStatus: MirrorAuthStatus = MirrorAuthStatus.UNAUTHORIZED,
    val has1080pUltra: Boolean? = null,
    val guestPingMs: Long = 0L,
    val authPingMs: Long = 0L,
    val lastTestedTimestamp: Long = 0L,
    val details: String = "",
) {
    public val bestPingMs: Long
        get() =
            when {
                isAuthWorking && authPingMs > 0 -> authPingMs
                isGuestWorking && guestPingMs > 0 -> guestPingMs
                else -> Long.MAX_VALUE
            }

    public val isAnyWorking: Boolean
        get() = isAuthWorking || isGuestWorking
}
