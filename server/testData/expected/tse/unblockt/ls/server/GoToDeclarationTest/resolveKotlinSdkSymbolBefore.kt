package tse.com

fun main(args: Array<String>) {
    val entry = SomeEnum.FIRST
    val second = SecondEnum.SECOND
}

suspend fun foo() {
    ru<caret>n { }
}