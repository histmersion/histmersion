package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** A Crossref `update-to` entry. */
final case class UpdateNotice(kind: Option[UpdateNotice.Kind], label: Option[UpdateNotice.Label], doi: Option[com.histmersion.api.library.Doi])
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object UpdateNotice:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
