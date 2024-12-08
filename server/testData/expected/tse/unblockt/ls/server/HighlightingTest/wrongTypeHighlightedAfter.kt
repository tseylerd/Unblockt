package tse.com

fun main() {
    takesInt(<Expected: Int, got: String>"abc"</Expected: Int, got: String>)
}

fun takesInt(i: Int) {
    takesMany(<Expected: Int, got: String>"a"</Expected: Int, got: String>, <Expected: String, got: Int>4</Expected: String, got: Int>)
    takesMany(<Expected: Int, got: String>"a"</Expected: Int, got: String>, "a")
    takesMany(4, "a")
    takesMany(<Expected: Int, got: String>i = "a"</Expected: Int, got: String>, <Expected: String, got: Int>str = 1</Expected: String, got: Int>)
    takesMany(i = 1, <Expected: String, got: Int>str = 1</Expected: String, got: Int>)
    takesMany(i = 1, str = "a")
    takesMany(<Expected: String, got: Int>str = 1</Expected: String, got: Int>, i = 1)
    takesMany(str = "a", <Expected: Int, got: String>i = "a"</Expected: Int, got: String>)
    takesMany(str = "a", i = 1)

    takesLambda {
        //hey
    }
}

fun takesMany(i: Int, str: String) {

}

fun takesLambda(runnable: Runnable) {

}