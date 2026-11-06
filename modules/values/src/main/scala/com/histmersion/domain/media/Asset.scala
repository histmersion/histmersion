package com.histmersion.domain.media

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Asset:

  /** A picture's stable identity across databases (`assets.uid`). */
  type Uid = Uid.T

  object Uid extends RefinedType[java.util.UUID, Pure]

  /** What a picture's link points at: the kinds of record it depicts. */
  object LinkTarget:
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Event = "event"
    val Finding = "finding"
    val Place = "place"
    val Source = "source"
    val Article = "article"
    val all: List[com.histmersion.domain.media.Asset.LinkTarget.All] = List(_root_.com.histmersion.domain.media.Asset.LinkTarget.All(Event), _root_.com.histmersion.domain.media.Asset.LinkTarget.All(Finding), _root_.com.histmersion.domain.media.Asset.LinkTarget.All(Place), _root_.com.histmersion.domain.media.Asset.LinkTarget.All(Source), _root_.com.histmersion.domain.media.Asset.LinkTarget.All(Article))
