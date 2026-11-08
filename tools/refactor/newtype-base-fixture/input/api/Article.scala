package com.histmersion.api.research

final case class Article(exported: Article.Export)

object Article:
  type Export = Export.T
  object Export extends _root_.io.github.iltotore.iron.RefinedType[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
