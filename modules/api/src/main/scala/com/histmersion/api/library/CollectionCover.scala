package com.histmersion
package api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** What became of an imported book's cover: one line for a person, a `warning` when it could not be stored. */
final case class CollectionCover(
    line: CollectionCover.Line,
    warning: CollectionCover.Warning = CollectionCover.Warning(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionCover:
  type Line = Line.T
  object Line extends Newtype[String, Pure]
  type Warning = Warning.T
  object Warning extends Newtype[Boolean, Pure]
