package com.histmersion
package domain.media

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Asset:

  /** A picture's stable identity across databases (`assets.uid`). */
  type Uid = Uid.T

  object Uid extends RefinedType[java.util.UUID, Pure]

  /** What a picture's link points at: the kinds of record it depicts. */
  object LinkTarget:
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]

    val Event = "event"
    val Finding = "finding"
    val Place = "place"
    val Source = "source"
    val Article = "article"
    val all: List[Wire] = List(Wire(Event), Wire(Finding), Wire(Place), Wire(Source), Wire(Article))
