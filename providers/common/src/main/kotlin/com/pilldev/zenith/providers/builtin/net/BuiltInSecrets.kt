package com.pilldev.zenith.providers.builtin.net

public object BuiltInSecrets {
    private const val K1 = "ZenithNeon"
    private const val K2 = "PillDev2025"

    public val YUMMY_PUBLIC_TOKEN: String get() = d(intArrayOf(100, 125, 102, 119, 68, 96, 14, 37, 114, 38, 6, 93, 42, 102, 104, 29))
    public val YUMMY_PRIVATE_TOKEN: String get() = d(intArrayOf(126, 53, 109, 117, 72, 97, 89, 111, 111, 113, 91, 92, 112, 100, 40, 28, 18, 39, 58, 60, 95, 15, 91, 121, 96, 109, 60, 121, 126, 108, 13, 57))
    public val KODIK_TOKEN: String get() = d(intArrayOf(107, 53, 54, 52, 6, 53, 8, 49, 103, 107, 95, 80, 102, 55, 124, 25, 73, 36, 104, 107, 11, 96, 8, 52, 121, 55, 108, 49, 33, 56, 95, 53))
    public val ANIME_SKIP_CLIENT_ID: String get() = d(intArrayOf(64, 57, 76, 109, 90, 91, 90, 3, 15, 108, 56, 127, 86, 52, 104, 86, 72, 88, 25, 18, 25, 29, 124, 68, 125, 78, 120, 49, 81, 18, 82, 111))

    private fun d(i: IntArray): String {
        val sb = StringBuilder()
        for (idx in i.indices) {
            var c = i[idx]
            c = c xor K2[idx % K2.length].code
            c = c xor K1[idx % K1.length].code
            sb.append(c.toChar())
        }
        return sb.toString()
    }
}
