package com.histmersion.api.instance
import com.histmersion.api.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object Version:
  val name: String = "histmersion"
  val version: String = "0.1.0-SNAPSHOT"

  /** The SPDX id of the licence the program is distributed under. */
  val licence: String = "AGPL-3.0-or-later"

  final case class Info(name: Info.Name, version: Info.Version) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Info:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Version = Version.T
    object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
