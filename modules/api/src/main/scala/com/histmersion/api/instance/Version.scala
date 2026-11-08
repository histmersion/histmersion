package com.histmersion
package api.instance
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
object Version:
  type Licence = Licence.T
  object Licence extends RefinedType[String, Pure]

  val name: Info.Name = Info.Name("histmersion")
  val version: Info.Version = Info.Version("0.1.0-SNAPSHOT")

  /** The SPDX id of the licence the program is distributed under. */
  val licence: Licence = Licence("AGPL-3.0-or-later")

  final case class Info(name: Info.Name, version: Info.Version) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Info:
    type Name = Name.T
    object Name extends RefinedType[String, Pure]
    type Version = Version.T
    object Version extends RefinedType[String, Pure]
