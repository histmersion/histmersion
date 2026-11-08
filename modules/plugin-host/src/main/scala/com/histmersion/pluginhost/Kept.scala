package com.histmersion
package pluginhost

import io.github.iltotore.iron.constraint.any.Pure

/** A plugin's record as the host keeps it, and why each field it left out was left out (a value the model's type
  * refuses: a DOI that is none, a position off the globe, …) — one bad field costs the field, not the record.
  */
final case class Kept[A](record: A, warnings: List[Kept.Warnings])

object Kept:
  type Warnings = Warnings.T
  object Warnings extends Newtype[String, Pure]
