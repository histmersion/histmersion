package com.histmersion.domain.personal

/** The shape of a bookmark's or a note's id, which the server gives them: a UUID in its lower-case text form. */
object PersonalUuid:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Shape = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r

  def matches(s: com.histmersion.domain.personal.PersonalUuid.Text): Boolean = Shape.matches(s.value)
