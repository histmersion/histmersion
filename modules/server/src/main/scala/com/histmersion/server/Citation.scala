package com.histmersion.server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article

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
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object ToArticle:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Unresolved:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
