package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Whether a person has read a source or an article, and when they last opened it. An item is read only when
  * the person says so ("Mark as read"); opening it is kept apart, for "Recently opened". */
final case class ReadMark(
    kind: ItemKind,
    id: ItemKey,
    title: String,
    read: Boolean = false,
    readAt: Option[String] = None,
    openedAt: Option[String] = None
) derives KindlingsCodecAsObject, KindlingsSchema

object ReadMark:
  given Schema[ReadMark] = summon[KindlingsSchema[ReadMark]].schema
