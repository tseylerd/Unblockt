// Copyright 2024 Dmitrii Tseiler. Licensed under the Apache 2.0 Licence

package tse.unblockt.ls.server.analysys.text

interface PrefixMatcher {
    val isEmpty: Boolean

    fun isStartMatch(string: String): Boolean
    fun isPrefixMatch(string: String): Boolean

    object Always: PrefixMatcher {
        override val isEmpty: Boolean
            get() = true

        override fun isStartMatch(string: String): Boolean = true
        override fun isPrefixMatch(string: String): Boolean = true
    }
}