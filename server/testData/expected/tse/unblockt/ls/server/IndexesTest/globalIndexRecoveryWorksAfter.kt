<keyword>package</keyword> tse.com

<keyword>import</keyword> java.io.File

<comment>/*
</comment><comment>block comment
</comment><comment>*/</comment>

<comment>/**
</comment><comment> * Javadoc
</comment><comment> */</comment>
<comment>// comment</comment>

<keyword>fun</keyword> <function>main</function>(<parameter>args</parameter>: <type>Array</type><<type>String</type>>) {
    <keyword>val</keyword> <variable>variable</variable> = <string>"some string"</string>
    <function>println</function>(args)
}

<modifier>suspend</modifier> <keyword>fun</keyword> <function>foo</function>() {
    <function>run</function> {
        <function>println</function>(<string>"Some kotlin code"</string>)
    }
}

<modifier>enum</modifier> <keyword>class</keyword> <class>SomeEnum</class> {
    <enummember><class>FIRST</class>,</enummember>
    <enummember><class>SECOND</enummember></class>
}

<keyword>fun</keyword> <<typeparameter>T</typeparameter>> <function>withType</function>() {

}
