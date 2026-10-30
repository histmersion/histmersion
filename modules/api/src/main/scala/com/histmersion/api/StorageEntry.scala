package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One place on disk (or, for the database, inside Postgres).
  *
  * @param key
  *   stable id (`database`, `backups`, `basemap`, …); [[StorageAdvice]] keys on it
  * @param label
  *   what a person calls it
  * @param group
  *   `database` · `backups` · `corpus` · `caches` · `install` · `logs` · `other`
  * @param path
  *   where it is (`None`: the database's own size, which lives inside the cluster)
  * @param bytes
  *   its size; `None` = nothing there (or not measured)
  * @param files
  *   how many regular files the walk counted
  * @param exists
  *   the path is there
  * @param partial
  *   a cap (time or entries, `storage.max-*`) stopped the walk: `bytes` is a lower bound
  * @param within
  *   the key of the entry this one is part of — counted there, not again in the total
  * @param detail
  *   what is in it (`3 archives, newest …`, the base-map files, …)
  * @param shrink
  *   how to make it smaller, when that is up to the person
  * @param volume
  *   the id of the [[StorageVolume]] it is on
  * @param host
  *   `server`, or `cli` for a location on the machine the CLI runs on
  */
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
