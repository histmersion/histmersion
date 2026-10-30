package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

/** A source an article cites, with the metadata the footer shows. `title` is `None` when the citekey is not (yet) in
  * the corpus — a dangling citation.
  */
final case class CitedSource(
    citekey: String,
    title: Option[String],
    year: Option[Int],
    status: Option[SourceStatus],
    authors: List[String] = Nil,
    doi: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CitedSource:
  given Schema[CitedSource] = summon[KindlingsSchema[CitedSource]].schema
