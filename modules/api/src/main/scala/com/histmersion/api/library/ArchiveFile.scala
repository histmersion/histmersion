package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class ArchiveFile(name: ArchiveFile.Name, format: Option[ArchiveFile.Format], megabytes: ArchiveFile.Megabytes)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ArchiveFile:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Format = Format.T
  object Format extends RefinedType[String, Pure]:
    type Problem = Problem.T
    object Problem extends RefinedType[String, Pure]
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]
  type Megabytes = Megabytes.T
  object Megabytes extends RefinedType[Double, Pure]
