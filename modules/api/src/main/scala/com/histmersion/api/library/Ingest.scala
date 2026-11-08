package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.Sha256
import com.histmersion.api.catalogue.Year
object Ingest:
  type Disposition = Disposition.T
  object Disposition extends Newtype[String, Pure]
  type DropInPath = DropInPath.T
  object DropInPath extends Newtype[String, Pure]
  type Extension = Extension.T
  object Extension extends Newtype[String, Pure]
  type JobAge = JobAge.T
  object JobAge extends Newtype[String, Pure]
  type JobStatus = JobStatus.T
  object JobStatus extends Newtype[String, Pure]
  type StepName = StepName.T
  object StepName extends Newtype[String, Pure]
  type StoredName = StoredName.T
  object StoredName extends Newtype[String, Pure]

  val CodeConflict = "conflict"

  val AssetCacheControl = "public, max-age=31536000, immutable"

  val MediaCacheControl = "public, max-age=86400"

  val KindIngest = IngestJobKinds.Ingest.kind.value
  val KindReocr = IngestJobKinds.Reocr.kind.value

  def originalDisposition(citekey: Citekey, storedName: StoredName): Disposition =
    val ext = (storedName).value.reverse.takeWhile(_ != '.').reverse
    Disposition(s"""attachment; filename="$citekey.${if ext == (storedName).value then "bin" else ext}"""")

  def dispositionExtension(disposition: Disposition): Option[Extension] =
    ("""filename="?([^";]+)"?""".r
      .findFirstMatchIn((disposition).value)
      .map(_.group(1).trim)
      .map(_.reverse.takeWhile(_ != '.').reverse.toLowerCase)
      .filter(e => e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit)))
      .map(refactorValue => Extension(refactorValue))

  final case class Accepted(
      jobId: SpatialHierarchyResult.Job.Id,
      citekey: Citekey,
      status: Accepted.Status,
      sha256: Option[Sha256] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Accepted:
    type Status = Status.T
    object Status extends Newtype[String, Pure]

  final case class Params(
      citekey: Citekey,
      filename: Option[Params.Filename] = None,
      quality: Params.Quality = Params.Quality(false),
      force: Params.Force = Params.Force(false),
      title: Option[Title] = None,
      authors: List[AuthorName] = Nil,
      year: Option[Year] = None,
      doi: Option[Doi] = None,
      provenance: Option[Params.Provenance] = None,
      acquireItem: Option[Params.AcquireItem] = None
  )

  object Params:
    export com.histmersion.api.exchange.Bundle.Asset.FileName as Filename
    type Quality = Quality.T
    object Quality extends Newtype[Boolean, Pure]
    type Force = Force.T
    object Force extends Newtype[Boolean, Pure]
    export Source.Provenance
    export Acquire.ItemNumber as AcquireItem

  final case class Report(
      citekey: Citekey,
      doi: Option[Doi],
      title: Option[Title],
      created: Report.Created,
      pdf: Option[PdfInfo],
      edgesAdded: Report.EdgesAdded,
      assessment: Option[Assessment],
      steps: List[Step],
      dropIn: Option[Report.DropIn] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Report:
    type Created = Created.T
    object Created extends Newtype[Boolean, Pure]
    type EdgesAdded = EdgesAdded.T
    object EdgesAdded extends Newtype[Int, Pure]
    export Ingest.DropInPath as DropIn

  final case class Request(
      id: Request.Id,
      citekey: Option[Citekey] = None,
      arxiv: Option[Request.Arxiv] = None,
      refetch: Request.Refetch = Request.Refetch(false),
      legalOnly: Request.LegalOnly = Request.LegalOnly(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Request:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    export Source.Candidate.ArxivId as Arxiv
    type Refetch = Refetch.T
    object Refetch extends Newtype[Boolean, Pure]
    type LegalOnly = LegalOnly.T
    object LegalOnly extends Newtype[Boolean, Pure]

  final case class Step(step: Step.Value, ok: Step.Ok, detail: Step.Detail)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Step:
    export Ingest.StepName as Value
    type Ok = Ok.T
    object Ok extends Newtype[Boolean, Pure]
    type Detail = Detail.T
    object Detail extends Newtype[String, Pure]
