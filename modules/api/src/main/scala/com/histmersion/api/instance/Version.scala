package com.histmersion.api.instance
import com.histmersion.api.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object Version:
  type Licence = Licence.T
  object Licence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val name: com.histmersion.api.instance.Version.Info.Name = _root_.com.histmersion.api.instance.Version.Info.Name("histmersion")
  val version: com.histmersion.api.instance.Version.Info.Version = _root_.com.histmersion.api.instance.Version.Info.Version("0.1.0-SNAPSHOT")

  /** The SPDX id of the licence the program is distributed under. */
  val licence: com.histmersion.api.instance.Version.Licence = _root_.com.histmersion.api.instance.Version.Licence("AGPL-3.0-or-later")

  final case class Info(name: Info.Name, version: Info.Version) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Info:
