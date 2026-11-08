package com.histmersion
package api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.ItemKind
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ReadMark(
    kind: ItemKind,
    id: ItemKey,
    title: ReadMark.Title,
    read: ReadMark.Read = ReadMark.Read(false),
    readAt: Option[ReadMark.ReadAt] = None,
    openedAt: Option[ReadMark.OpenedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ReadMark:


  export com.histmersion.api.library.Title
  type Read = Read.T
  object Read extends Newtype[Boolean, Pure]
  type ReadAt = ReadAt.T
  object ReadAt extends Newtype[String, Pure]
  type OpenedAt = OpenedAt.T
  object OpenedAt extends Newtype[String, Pure]

  final case class Set(kind: ItemKind, id: ItemKey, read: Read, title: Option[com.histmersion.api.library.Title] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
