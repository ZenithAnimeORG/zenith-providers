rootProject.name = "zenith-providers"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

include(":providers:common")
include(":providers:kodik")
include(":providers:hdrezka")
include(":providers:anilibria")
include(":providers:yummy")
include(":providers:anitype")
include(":providers:lift")
include(":providers:opensubtitles")
include(":providers:animego")
include(":providers:aniskip")
include(":providers:animeskip")
include(":providers:theintrodb")
include(":providers:nyaa")
include(":providers:rutor")
include(":providers:rutracker")
include(":providers:anixart")
include(":providers:shikimori")
include(":providers:anilist")
