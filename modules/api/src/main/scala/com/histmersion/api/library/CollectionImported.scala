package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

/** An import: the record written (`source`), the item it is linked to, the entry it was linked to before
  * (`relinkedFrom`), the file taken (left in the collection, never modified), the cover, and the conversion queued
  * (`job`) or why it was not (`jobError`, with its `jobErrorCode`).
  */
final case class CollectionImported(
    citekey: com.histmersion.api.library.Citekey,
    id: CollectionImported.Id,
    title: com.histmersion.api.library.Title,
    relinkedFrom: Option[CollectionImported.RelinkedFrom],
    file: CollectionImported.File,
    source: Source.WithFindings,
    cover: Option[CollectionCover],
    job: Option[Ingest.Accepted],
    jobError: Option[CollectionImported.JobError] = None,
    jobErrorCode: Option[CollectionImported.JobErrorCode] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionImported:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RelinkedFrom = RelinkedFrom.T
  object RelinkedFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type File = File.T
  object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobError = JobError.T
  object JobError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobErrorCode = JobErrorCode.T
  object JobErrorCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
