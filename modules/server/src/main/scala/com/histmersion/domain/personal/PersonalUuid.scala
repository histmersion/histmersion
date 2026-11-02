package com.histmersion.domain.personal

/** The shape of a bookmark's or a note's id, which the server gives them: a UUID in its lower-case text form. */
object PersonalUuid:
  private val Shape = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r

  def matches(s: String): Boolean = Shape.matches(s)
