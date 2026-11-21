package com.histmersion
package util.jobs

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

object Job:

  type Id = Id.T

  object Id extends Newtype[String, Match["[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"]]

  type Kind = Kind.T

  object Kind extends Newtype[String, Match["[a-z][a-z_-]*"]]
