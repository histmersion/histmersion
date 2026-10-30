package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** One entry of a source page's "Cites" / "Cited by" lists — edges AND article links, typed. */
enum Citation:
  /** A stored source, through a typed edge (`references`, `challenges`, …). */
  case ToSource(ref: SourceRef, kind: String)

  /** A compiled article: one citing this source in its prose (`kind = None`), or named by an edge. */
  case ToArticle(ref: ArticleRef, kind: Option[String])

  /** An edge to a work that is not in the corpus yet. */
  case Unresolved(id: String, kind: String)
