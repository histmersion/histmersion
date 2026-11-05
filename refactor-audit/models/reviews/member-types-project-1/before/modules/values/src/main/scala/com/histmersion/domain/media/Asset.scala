package com.histmersion.domain.media

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Asset:

  /** A picture's stable identity across databases (`assets.uid`). */
  type Uid = Uid.T

  object Uid extends RefinedType[java.util.UUID, Pure]

  /** What a picture's link points at: the kinds of record it depicts. */
  object LinkTarget:
    val Event = "event"
    val Finding = "finding"
    val Place = "place"
    val Source = "source"
    val Article = "article"
    val all: List[String] = List(Event, Finding, Place, Source, Article)
