package com.pilldev.zenith.domain.model

public object HdRezkaCandidateMirrors {
    public val NO_AUTH: List<String> =
        listOf(
            "hdrezka.in",
            "hdrezka.ag",
            "hdrezka.me",
            "rezka.ag",
            "hdrezka-home.tv",
            "hdrezka.website",
            "omnirezka.tv",
            "hello-rezka.tv",
            "hdrezka.name",
            "hdrezka.sh",
            "hdrezka.club",
            "rezka-kz.tv",
            "rezka-ua.net",
            "rezka-ua.org",
            "rezka-ua.in",
            "rezka-ua.co",
            "hdrezka.kim",
            "rezka-ua.pub",
            "rezka.pub",
            "rezkery.com",
            "rezka-ua.tv",
            "hdrezka-fly.net",
            "hdrezka-sonic.net",
            "hdrezka.hk",
            "rezkify.com",
            "hdrezka.co.uk",
        )

    public val AUTH: List<String> =
        listOf(
            "hdrezka.sb",
            "hdrezka.fi",
            "rezka.fi",
            "flymaterez.net",
            "rezka.si",
            "standby-rezka.tv",
            "hdrezka.pm",
            "hdrezka.la",
            "hdrezka.cn.com",
            "hdrezka.mn",
            "hdrezka.cm",
            "hdrezka.me",
            "rezka.ag",
            "hdrezka.ag",
            "hdrezka2vbppy.org",
            "hdrezka9bsbhq.org",
            "hdrezka720dhh.org",
            "hdrezka8benxe.org",
        )

    public val KVK: List<String> =
        listOf(
            "kvk.pub",
            "kvk.zone",
            "kvk.plus",
        )

    public val REGIONAL: List<String> =
        listOf(
            "rezka-kz.tv",
            "rezka-ua.net",
            "rezka-ua.org",
            "rezka-ua.in",
            "rezka-ua.co",
            "rezka-ua.pub",
            "rezka-ua.tv",
        )

    public val ALL: List<String> = (AUTH + NO_AUTH + KVK + REGIONAL).distinct()
}
