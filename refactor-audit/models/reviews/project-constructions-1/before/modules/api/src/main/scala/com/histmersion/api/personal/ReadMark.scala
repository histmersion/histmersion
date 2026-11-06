package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.ItemKind

final case class ReadMark(
    kind: ItemKind,
    id: ItemKey,
    title: ReadMark.Title,
    read: ReadMark.Read = false,
    readAt: Option[ReadMark.ReadAt] = None,
    openedAt: Option[ReadMark.OpenedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ReadMark:


  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Read = Read.T
  object Read extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAt = ReadAt.T
  object ReadAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenedAt = OpenedAt.T
  object OpenedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Set(kind: ItemKind, id: ItemKey, read: com.histmersion.api.personal.ReadMark.Read, title: Option[com.histmersion.api.personal.ReadMark.Title] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
