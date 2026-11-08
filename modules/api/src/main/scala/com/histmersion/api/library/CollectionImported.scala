package com.histmersion
package api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** An import: the record written (`source`), the item it is linked to, the entry it was linked to before
  * (`relinkedFrom`), the file taken (left in the collection, never modified), the cover, and the conversion queued
  * (`job`) or why it was not (`jobError`, with its `jobErrorCode`).
  */
final case class CollectionImported(
    citekey: Citekey,
    id: CollectionImported.Id,
    title: Title,
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
  object Id extends Newtype[String, Pure]
  type RelinkedFrom = RelinkedFrom.T
  object RelinkedFrom extends Newtype[String, Pure]
  type File = File.T
  object File extends Newtype[String, Pure]
  type JobError = JobError.T
  object JobError extends Newtype[String, Pure]
  type JobErrorCode = JobErrorCode.T
  object JobErrorCode extends Newtype[String, Pure]
