package com.histmersion.api.library
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object Ingest:
  type Disposition = Disposition.T
  object Disposition extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DropInPath = DropInPath.T
  object DropInPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Extension = Extension.T
  object Extension extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobAge = JobAge.T
  object JobAge extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobStatus = JobStatus.T
  object JobStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepName = StepName.T
  object StepName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoredName = StoredName.T
  object StoredName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val CodeConflict = "conflict"

  val AssetCacheControl = "public, max-age=31536000, immutable"

  val MediaCacheControl = "public, max-age=86400"

  val KindIngest = com.histmersion.api.library.IngestJobKinds.Ingest.kind.value
  val KindReocr = com.histmersion.api.library.IngestJobKinds.Reocr.kind.value

  def originalDisposition(citekey: com.histmersion.api.library.Citekey, storedName: com.histmersion.api.library.Ingest.StoredName): com.histmersion.api.library.Ingest.Disposition =
    val ext = (storedName).value.reverse.takeWhile(_ != '.').reverse
    _root_.com.histmersion.api.library.Ingest.Disposition(s"""attachment; filename="$citekey.${if ext == (storedName).value then "bin" else ext}"""")

  def dispositionExtension(disposition: com.histmersion.api.library.Ingest.Disposition): Option[com.histmersion.api.library.Ingest.Extension] =
    ("""filename="?([^";]+)"?""".r
      .findFirstMatchIn((disposition).value)
      .map(_.group(1).trim)
      .map(_.reverse.takeWhile(_ != '.').reverse.toLowerCase)
      .filter(e => e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit))).map(refactorValue => _root_.com.histmersion.api.library.Ingest.Extension(refactorValue))

  final case class Accepted(jobId: com.histmersion.api.SpatialHierarchyResult.Job.Id, citekey: com.histmersion.api.library.Citekey, status: Accepted.Status, sha256: Option[com.histmersion.api.Sha256] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Accepted:
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Params(
      citekey: com.histmersion.api.library.Citekey,
      filename: Option[Params.Filename] = None,
      quality: Params.Quality = _root_.com.histmersion.api.library.Ingest.Params.Quality(false),
      force: Params.Force = _root_.com.histmersion.api.library.Ingest.Params.Force(false),
      title: Option[com.histmersion.api.library.Title] = None,
      authors: List[com.histmersion.api.library.AuthorName] = Nil,
      year: Option[com.histmersion.api.catalogue.Year] = None,
      doi: Option[com.histmersion.api.library.Doi] = None,
      provenance: Option[Params.Provenance] = None,
      acquireItem: Option[Params.AcquireItem] = None
  )

  object Params:
    type Filename = com.histmersion.api.exchange.Bundle.Asset.FileName
    val Filename: _root_.com.histmersion.api.exchange.Bundle.Asset.FileName.type = _root_.com.histmersion.api.exchange.Bundle.Asset.FileName
    type Quality = Quality.T
    object Quality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Force = Force.T
    object Force extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Provenance = com.histmersion.api.library.Source.Provenance
    val Provenance: _root_.com.histmersion.api.library.Source.Provenance.type = _root_.com.histmersion.api.library.Source.Provenance
    type AcquireItem = com.histmersion.api.library.Acquire.ItemNumber
    val AcquireItem: _root_.com.histmersion.api.library.Acquire.ItemNumber.type = _root_.com.histmersion.api.library.Acquire.ItemNumber

  final case class Report(
      citekey: com.histmersion.api.library.Citekey,
      doi: Option[com.histmersion.api.library.Doi],
      title: Option[com.histmersion.api.library.Title],
      created: Report.Created,
      pdf: Option[PdfInfo],
      edgesAdded: Report.EdgesAdded,
      assessment: Option[Assessment],
      steps: List[Step],
      dropIn: Option[Report.DropIn] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Report:
    type Created = Created.T
    object Created extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgesAdded = EdgesAdded.T
    object EdgesAdded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DropIn = com.histmersion.api.library.Ingest.DropInPath
    val DropIn: _root_.com.histmersion.api.library.Ingest.DropInPath.type = _root_.com.histmersion.api.library.Ingest.DropInPath


  final case class Request(
      id: Request.Id,
      citekey: Option[com.histmersion.api.library.Citekey] = None,
      arxiv: Option[Request.Arxiv] = None,
      refetch: Request.Refetch = _root_.com.histmersion.api.library.Ingest.Request.Refetch(false),
      legalOnly: Request.LegalOnly = _root_.com.histmersion.api.library.Ingest.Request.LegalOnly(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Request:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Arxiv = com.histmersion.api.library.WorkRef.ArxivId
    val Arxiv: _root_.com.histmersion.api.library.WorkRef.ArxivId.type = _root_.com.histmersion.api.library.WorkRef.ArxivId
    type Refetch = Refetch.T
    object Refetch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LegalOnly = LegalOnly.T
    object LegalOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Step(step: Step.Value, ok: Step.Ok, detail: Step.Detail) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Step:
    type Value = com.histmersion.api.library.Ingest.StepName
    val Value: _root_.com.histmersion.api.library.Ingest.StepName.type = _root_.com.histmersion.api.library.Ingest.StepName
    type Ok = Ok.T
    object Ok extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Detail = Detail.T
    object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
