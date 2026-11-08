package com.histmersion
package domain.personal

import io.github.iltotore.iron.constraint.any.Pure

/** The shape of a bookmark's or a note's id, which the server gives them: a UUID in its lower-case text form. */
object PersonalUuid:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  private val Shape = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r

  def matches(s: Text): Boolean = Shape.matches(s.value)
