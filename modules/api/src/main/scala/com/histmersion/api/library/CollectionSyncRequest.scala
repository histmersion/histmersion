package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Push every counted gap into a collection (a dry run unless `apply`), considering at most `limit` gaps. */
final case class CollectionSyncRequest(
    apply: Boolean = false,
    limit: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionSyncRequest:
  given Schema[CollectionSyncRequest] = summon[KindlingsSchema[CollectionSyncRequest]].schema
