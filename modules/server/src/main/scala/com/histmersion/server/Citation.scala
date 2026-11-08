package com.histmersion
package server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.domain.library.Edge
import io.github.iltotore.iron.constraint.any.Pure

/** One entry of a source page's "Cites" / "Cited by" lists — edges AND article links, typed. */
enum Citation:
  /** A stored source, through a typed edge (`references`, `challenges`, …). */
  case ToSource(ref: Source.Ref, kind: Citation.ToSource.Kind)

  /** A compiled article: one citing this source in its prose (`kind = None`), or named by an edge. */
  case ToArticle(ref: Article.Ref, kind: Option[Citation.ToArticle.Kind])

  /** An edge to a work that is not in the corpus yet. */
  case Unresolved(id: Citation.Unresolved.Id, kind: Citation.Unresolved.Kind)

object Citation:
  object ToSource:
    type Kind = Edge.Kind.Wire
    val Kind: Edge.Kind.Wire.type = Edge.Kind.Wire
  object ToArticle:
    type Kind = Edge.Kind.Wire
    val Kind: Edge.Kind.Wire.type = Edge.Kind.Wire
  object Unresolved:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Kind = Edge.Kind.Wire
    val Kind: Edge.Kind.Wire.type = Edge.Kind.Wire
