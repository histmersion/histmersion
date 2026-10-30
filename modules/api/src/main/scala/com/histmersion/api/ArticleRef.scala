package com.histmersion.api

final case class ArticleRef(slug: String, title: String, summary: Option[String] = None)
