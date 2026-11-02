package com.histmersion.api.research

import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
final case class Article(
    slug: String,
    title: String,
    kind: String,
    body: String,
    summary: Option[String] = None,
    tags: List[String] = Nil,
    regions: List[String] = Nil,
    period: Option[Period] = None,
    updatedAt: Option[String] = None,
    filter: Option[ArticleFilter] = None,
    reasons: Map[String, String] = Map.empty
)
