package com.histmersion
package api.library.query

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.library.Query

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class QueryRequest(sql: QueryRequest.Sql) derives JsonCodec, KindlingsSchema, api.json.Schema

object QueryRequest:
  export Query.Statement as Sql
