package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A sync: the gaps it considered, and with `apply` each push made. */
final case class CollectionSync(
    gaps: CollectionGaps,
    applied: Boolean,
    pushes: List[com.histmersion.api.library.CollectionSync.Push]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionSync:


  /** One push of a sync: the item it became, or why it failed. */
  final case class Push(
      citekey: String,
      id: Option[String],
      error: Option[String]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema



  /** Push every counted gap into a collection (a dry run unless `apply`), considering at most `limit` gaps. */
  final case class Request(
      apply: Boolean = false,
      limit: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
