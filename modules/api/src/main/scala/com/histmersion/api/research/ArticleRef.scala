package com.histmersion.api.research
import com.histmersion.api.given
final case class ArticleRef(slug: String, title: String, summary: Option[String] = None)
