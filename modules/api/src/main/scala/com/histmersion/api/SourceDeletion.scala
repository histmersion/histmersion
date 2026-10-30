package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The answer of a delete, dry run or applied.
  *
  * @param counts
  *   non-zero row counts, in [[DeletePart]] order
  * @param edgesIn
  *   the edges of other sources that point at it (parked by the delete)
  * @param coverage
  *   articles whose coverage reasons name it (the reason goes, the article stays)
  * @param files
  *   the stored original(s) on the server that go after the commit
  * @param filesFailed
  *   `path: reason` for each file the applied delete could not remove (logged)
  * @param mentions
  *   acquisition/task/log items whose text or reason links it — left as they are
  * @param calibreId
  *   the linked Calibre entry, which is never touched
  * @param logEntry
  *   the log-queue item recording an applied delete
  */
final case class SourceDeletion(
    citekey: String,
    title: String,
    applied: Boolean,
    blockers: List[DeleteBlocker] = Nil,
    counts: List[DeleteCount] = Nil,
    aliases: List[String] = Nil,
    edgesIn: List[Edge] = Nil,
    coverage: List[String] = Nil,
    files: List[String] = Nil,
    filesFailed: List[String] = Nil,
    mentions: List[Int] = Nil,
    calibreId: Option[String] = None,
    logEntry: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def refused: Boolean = blockers.nonEmpty
  def count(p: DeletePart): Int = counts.find(_.part == p).fold(0)(_.rows)

object SourceDeletion:
  given Schema[SourceDeletion] = summon[KindlingsSchema[SourceDeletion]].schema
