package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── pictures of sources: covers and illustrations (#1446) ──────────────────────────────────────

/** `POST /api/media/covers` (a dry run: what would be done, from the database alone) and `POST /api/media/covers/job`
  * (doing it, as a job): Open Library covers for the sources whose record holds an ISBN or OCLC number and no cover
  * yet, and an illustration — one of its own figures (the domain's `FigurePick`) — for every source that has figures
  * and no illustration yet. Calibre's covers are the CLI's part (`histmersion media covers`): the library is on the
  * user's machine. `citekey` narrows it to one source; `refresh` redoes the ones that already have theirs.
  */
final case class MediaCoversRequest(
    dryRun: Boolean = true,
    limit: Option[Int] = None,
    citekey: Option[String] = None,
    refresh: Boolean = false,
    covers: Boolean = true,
    illustrations: Boolean = true,
    /** With `citekey`: an ISBN to ask Open Library by when the record holds none (the Calibre entry's, which
      * `histmersion media covers` passes when Calibre has no cover file).
      */
    isbn: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object MediaCoversRequest:
  given Schema[MediaCoversRequest] = summon[KindlingsSchema[MediaCoversRequest]].schema
