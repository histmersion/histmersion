package com.histmersion.server

import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.json.circe.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** Which standalone page a document is — the web bundle's page entry point reads it from `<body data-page>` to wire
  * that page's behaviour (link previews, match navigation).
  */
enum PageKind(val wire: String):
  case Source extends PageKind("source")
  case Article extends PageKind("article")
