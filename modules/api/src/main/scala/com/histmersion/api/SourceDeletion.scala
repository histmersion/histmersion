package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

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
