package com.histmersion
package server

import sttp.tapir.*
import sttp.tapir.files.*
import com.histmersion.api.json.TapirJson.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** Which standalone page a document is — the web bundle's page entry point reads it from `<body data-page>` to wire
  * that page's behaviour (link previews, match navigation).
  */
@movesTo(FeaturePlace.ResearchArticles)
enum PageKind(val wire: PageKind.Wire):
  case Source extends PageKind(PageKind.Wire("source"))
  case Article extends PageKind(PageKind.Wire("article"))

object PageKind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
