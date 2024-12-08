package tse.com

fun main() {
    takesInt("abc")
}

fun takesInt(i: Int) {
    takesMany("a", 4)
    takesMany("a", "a")
    takesMany(4, "a")
    takesMany(i = "a", str = 1)
    takesMany(i = 1, str = 1)
    takesMany(i = 1, str = "a")
    takesMany(str = 1, i = 1)
    takesMany(str = "a", i = "a")
    takesMany(str = "a", i = 1)

    takesLambda {
        //hey
    }
}

fun takesMany(i: Int, str: String) {

}

fun takesLambda(runnable: Runnable) {

}