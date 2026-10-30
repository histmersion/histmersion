package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A source or an article the person has not marked read, as a list shows it. */
final case class UnreadRow(
    kind: ItemKind,
    id: ItemKey,
    title: String,
    authors: List[String] = Nil,
    year: Option[Int] = None,
    status: Option[String] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object UnreadRow:
  given Schema[UnreadRow] = summon[KindlingsSchema[UnreadRow]].schema
