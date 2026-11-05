package com.histmersion.domain.media.assets

/** What a picture is shown on: a place, an event, or a source (its cover or illustration). */
enum PictureTarget(val wire: PictureTarget.Wire):
  case Place extends PictureTarget("place")
  case Event extends PictureTarget("event")
  case Source extends PictureTarget("source")

object PictureTarget:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
