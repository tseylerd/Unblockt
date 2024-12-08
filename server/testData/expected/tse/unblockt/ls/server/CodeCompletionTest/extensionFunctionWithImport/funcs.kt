package tse.com.root

inline fun <T> T?.alsoIfNull(block: () -> Unit): T? {
    if (this == null) {
        block()
    }
    return this
}

//src/main/java/tse/com/root/funcs.kt