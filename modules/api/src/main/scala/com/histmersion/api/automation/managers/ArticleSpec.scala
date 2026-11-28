package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.research.Article
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Managers.Rationale

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class ArticleSpec(
    title: Article.Title,
    kind: Article.Kind.Wire,
    filter: Article.Filter,
    why: Rationale = Rationale("")
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
