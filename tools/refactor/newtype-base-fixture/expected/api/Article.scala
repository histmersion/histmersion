package com.histmersion.api.research

import com.histmersion.Newtype

final case class Article(exported: Article.Export)

object Article:
  type Export = Export.T
  object Export extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
