package com.histmersion
package domain.media.assets

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What a picture is shown on: a place, an event, or a source (its cover or illustration). */
enum PictureTarget(val wire: PictureTarget.Wire):
  case Place extends PictureTarget(PictureTarget.Wire("place"))
  case Event extends PictureTarget(PictureTarget.Wire("event"))
  case Source extends PictureTarget(PictureTarget.Wire("source"))

object PictureTarget:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]
