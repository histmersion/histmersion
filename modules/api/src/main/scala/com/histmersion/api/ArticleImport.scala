package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/articles/import`: one note's raw text, parsed server-side by [[ArticleNote.parse]]. The host-side path of
  * `histmersion seed` for articles — the CLI can read the host's `wiki` directories, the containerised server cannot.
  * Carries the same "stored row is newer than the file" guard as the source seed, so a reseed cannot wipe an agent's
  * enrichment.
  *
  * @param slug
  *   fallback only, used when the frontmatter has no `slug` (normally the filename)
  * @param kind
  *   fallback only, used when the frontmatter has no `type` (normally the directory)
  * @param mtime
  *   the file's last-modified time in epoch millis; enables the "newer in DB" skip
  */
final case class ArticleImport(
    text: String,
    slug: Option[String] = None,
    kind: Option[String] = None,
    mtime: Option[Long] = None,
    force: Boolean = false,
    /** Refuse the import when the body has a formatting ERROR (see [[FormatIssue]]). */
    strict: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArticleImport:
  given Schema[ArticleImport] = summon[KindlingsSchema[ArticleImport]].schema
