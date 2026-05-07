package com.amfaro.jarify.dialect

import com.intellij.database.Dbms
import com.intellij.psi.tree.IElementType
import com.intellij.sql.dialects.base.SqlLanguageDialectBase
import com.intellij.sql.dialects.base.TokensHelper
import com.intellij.sql.dialects.sql92.Sql92Dialect

/** Surfaces "DuckDB" in the per-file SQL Dialects picker; identity is used by the dialect-mapping gate. */
class DuckDbSqlDialect private constructor() : SqlLanguageDialectBase(ID) {

    override fun getDbms(): Dbms = Dbms.UNKNOWN
    override fun getDisplayName(): String = ID
    override fun getSystemVariables(): Set<String> = emptySet()
    override fun isOperatorSupported(operator: IElementType): Boolean = true
    override fun createTokensHelper(): TokensHelper = Sql92Dialect.INSTANCE.tokensHelper

    companion object {
        const val ID: String = "DuckDB"

        @JvmField
        val INSTANCE: DuckDbSqlDialect = DuckDbSqlDialect()
    }
}
