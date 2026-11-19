package com.histmersion
package fixtures.hellolib

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A TEST FIXTURE: a library no Histmersion module depends on, so that a plugin using it needs it installed beside the
  * plugin (the dependency-resolution tests and `scripts/bundle/smoke.sh`).
  */
object Shout:
  type Shouted = Shouted.T
  object Shouted
      extends RefinedType[
        String,
        Pure
      ]
  type ApplyText = ApplyText.T
  object ApplyText
      extends RefinedType[
        String,
        Pure
      ]

  def apply(
      text: ApplyText
  ): Shouted =
    Shouted((text).value.toUpperCase(java.util.Locale.ROOT) + "!")
