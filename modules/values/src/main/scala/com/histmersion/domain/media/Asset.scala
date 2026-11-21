package com.histmersion
package domain.media

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Asset:

  type Uid = Uid.T

  object Uid extends Newtype[java.util.UUID, Pure]

  object LinkTarget:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    val Event = "event"
    val Finding = "finding"
    val Place = "place"
    val Source = "source"
    val Article = "article"
    val all: List[Wire] = List(Wire(Event), Wire(Finding), Wire(Place), Wire(Source), Wire(Article))
