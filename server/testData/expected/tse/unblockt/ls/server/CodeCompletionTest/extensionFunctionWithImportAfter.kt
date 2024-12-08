package tse.com

import tse.com.root.alsoIfNull

fun main() {
    val simpleString = "abc"
    simpleString.alsoIfNull { $$ }
}