package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class StorageEntry(
    key: String,
    label: String,
    group: String,
    path: Option[String] = None,
    bytes: Option[Long] = None,
    files: Option[Long] = None,
    exists: Boolean = true,
    partial: Boolean = false,
    within: Option[String] = None,
    detail: Option[String] = None,
    shrink: Option[String] = None,
    volume: Option[String] = None,
    host: String = "server"
) derives KindlingsCodecAsObject,
      KindlingsSchema
object StorageEntry:
  given Schema[StorageEntry] = summon[KindlingsSchema[StorageEntry]].schema
