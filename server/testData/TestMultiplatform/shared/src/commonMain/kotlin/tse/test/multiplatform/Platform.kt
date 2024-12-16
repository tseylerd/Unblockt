package tse.test.multiplatform

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform