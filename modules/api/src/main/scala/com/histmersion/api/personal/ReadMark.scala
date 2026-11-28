package com.histmersion
package api.personal

import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.ItemKind
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.TwoStates

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class ReadMark(
    kind: ItemKind,
    id: ItemKey,
    title: ReadMark.Title,
    read: ReadMark.Read = ReadMark.Read.Unread,
    readAt: Option[ReadMark.ReadAt] = None,
    openedAt: Option[ReadMark.OpenedAt] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ReadMark:

  export com.histmersion.api.library.Title
  enum Read derives CanEqual:
    case Read, Unread
  object Read extends TwoStates[Read]:
    extension (read: Read) def read: Boolean = read == Read
  type ReadAt = ReadAt.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ReadAt extends Newtype[String, Pure]
  type OpenedAt = OpenedAt.T
  object OpenedAt extends Newtype[String, Pure]

  final case class Set(kind: ItemKind, id: ItemKey, read: Read, title: Option[api.library.Title] = None)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema
