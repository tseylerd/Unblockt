package tse.com

fun main() {
    test(second = 1, <caret>)
}

fun test(first: Int) {

}

fun test(first: Int, second: Int) {

}

fun test(first: Int, second: Int, third: Int) {

}