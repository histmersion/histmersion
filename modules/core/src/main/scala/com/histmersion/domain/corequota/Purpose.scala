package com.histmersion
package domain.corequota

import hearth.kindlings.optics.syntax.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

enum Purpose(val wire: Purpose.Wire, val label: Purpose.Label) derives CanEqual:
  /** The acquisition chain's `core` step: a work's download URLs by its DOI. */
  case Lookup extends Purpose(Purpose.Wire("lookup"), Purpose.Label("PDF lookups"))

  /** The new-source search's `core` index: one saved query of a goal. */
  case Search extends Purpose(Purpose.Wire("search"), Purpose.Label("searches"))

object Purpose:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
