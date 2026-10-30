package com.histmersion.api

/** One `[[…]]` link out of an article, classified at write time. */
final case class ArticleLink(targetKind: String, target: String)
