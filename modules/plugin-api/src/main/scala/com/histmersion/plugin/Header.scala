package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** An HTTP header: sent with a request, or read from an answer (names in lower case there). */
object Header:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Value = Value.T
  object Value extends Newtype[String, Pure]
