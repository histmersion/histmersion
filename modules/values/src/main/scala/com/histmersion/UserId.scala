package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** Whose bookmarks, notes and read marks they are. While the server runs for one person, everything is the one local
  * user's.
  */
type UserId = UserId.T

object UserId extends Newtype[String, Pure]:
  val Local: UserId = UserId("local")
