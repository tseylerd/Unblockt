package tse.com

import java.io.File

/*
block comment
*/

/**
 * Javadoc
 */
// comment

fun main(args: Array<String>) {
    val variable = "some string"
    println(args)
}

suspend fun foo() {
    run {
        println("Some kotlin code")
    }
}

enum class SomeEnum {
    FIRST,
    SECOND
}

fun <T> withType() {

}
