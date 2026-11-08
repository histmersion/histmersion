package com.histmersion
package server

import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.json.circe.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Which standalone page a document is — the web bundle's page entry point reads it from `<body data-page>` to wire
  * that page's behaviour (link previews, match navigation).
  */
enum PageKind(val wire: PageKind.Wire):
  case Source extends PageKind(PageKind.Wire("source"))
  case Article extends PageKind(PageKind.Wire("article"))

object PageKind:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]
