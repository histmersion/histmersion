package com.histmersion.api

/** A lightweight reference to an article, carrying just enough to render a link. */
final case class ArticleRef(slug: String, title: String, summary: Option[String] = None)
