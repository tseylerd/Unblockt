<keyword>package</keyword> tse.unblockt.ls.server

<keyword>import</keyword> com.intellij.psi.PsiElement
<keyword>import</keyword> kotlinx.coroutines.flow.<interface>Flow</interface>
<keyword>import</keyword> kotlinx.coroutines.flow.<function>flow</function>
<keyword>import</keyword> org.apache.logging.log4j.kotlin.logger

<modifier>inline</modifier> <keyword>fun</keyword> <<modifier>reified</modifier> T> <type>T</type>.<function>debugLog</function>(<parameter>element</parameter>: <type>PsiElement</type>) {
    <variable>logger</variable>.<function>debug</function>(<string>"[${element::class.simpleName}] [${element.text}] [${element.node?.elementType?.let { it::class.simpleName }}]"</string>)
}

<keyword>fun</keyword> <<typeparameter>T</typeparameter>> <type>Flow</type><<type>T</type>>.<function>distinct</function>(): <type>Flow</type><<type>T</type>> {
    <keyword>val</keyword> <variable>thisFlow</variable> = <keyword>this</keyword>
    <keyword>return</keyword> <function>flow</function> {
        <keyword>val</keyword> <variable>past</variable> = <function>mutableSetOf</function><<type>T</type>>()
        thisFlow.<function>collect</function> {
            <keyword>val</keyword> <variable>isNew</variable> = past.<function>add</function>(it)
            <keyword>if</keyword> (isNew) <function>emit</function>(it)
        }
    }
}