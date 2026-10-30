package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/sources/import`: a note's raw text, parsed server-side with [[Frontmatter.parseNote]] and applied as a
  * `put_source`. Lets the CLI seed from a directory the SERVER cannot see (the host's `wiki/sources`), while the seed
  * guard still runs server-side.
  *
  * @param text
  *   the whole note: YAML frontmatter + Markdown body (the body becomes `notes`)
  * @param citekey
  *   fallback only, used when the frontmatter itself has no `citekey`
  * @param body
  *   the converted paper text (`md_path`), read by the client — the server has no host FS
  * @param mtime
  *   the file's last-modified time in epoch millis; enables the "newer in DB" skip
  * @param force
  *   apply even when the stored row is newer than `mtime`
  * @param assets
  *   the note's figures (`md/<citekey>/figures/…`, plan §1.8), base64-encoded; the body's `figures/<name>` links are
  *   normalised to `assets/<name>` on the way in. Keep the batch small — `POST /api/sources/{ck}/assets` exists for
  *   larger sets.
  */
final case class NoteImport(
    text: String,
    citekey: Option[String] = None,
    body: Option[String] = None,
    mtime: Option[Long] = None,
    force: Boolean = false,
    assets: List[AssetInput] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object NoteImport:
  given Schema[NoteImport] = summon[KindlingsSchema[NoteImport]].schema
