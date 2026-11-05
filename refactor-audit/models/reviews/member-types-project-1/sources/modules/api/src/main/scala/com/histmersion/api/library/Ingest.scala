package com.histmersion.api.library
import com.histmersion.api.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object Ingest:
  type OriginalDispositionResult = OriginalDispositionResult.T
  object OriginalDispositionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalDispositionCitekey = OriginalDispositionCitekey.T
  object OriginalDispositionCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalDispositionStoredName = OriginalDispositionStoredName.T
  object OriginalDispositionStoredName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispositionExtensionResult = DispositionExtensionResult.T
  object DispositionExtensionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispositionExtensionDisposition = DispositionExtensionDisposition.T
  object DispositionExtensionDisposition extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val CodeConflict = "conflict"

  val AssetCacheControl = "public, max-age=31536000, immutable"

  val MediaCacheControl = "public, max-age=86400"

  val KindIngest = com.histmersion.api.library.IngestJobKinds.Ingest.kind.value
  val KindReocr = com.histmersion.api.library.IngestJobKinds.Reocr.kind.value

  def originalDisposition(citekey: com.histmersion.api.library.Ingest.OriginalDispositionCitekey, storedName: com.histmersion.api.library.Ingest.OriginalDispositionStoredName): com.histmersion.api.library.Ingest.OriginalDispositionResult =
    val ext = storedName.reverse.takeWhile(_ != '.').reverse
    s"""attachment; filename="$citekey.${if ext == storedName then "bin" else ext}""""

  def dispositionExtension(disposition: com.histmersion.api.library.Ingest.DispositionExtensionDisposition): Option[com.histmersion.api.library.Ingest.DispositionExtensionResult] =
    """filename="?([^";]+)"?""".r
      .findFirstMatchIn(disposition)
      .map(_.group(1).trim)
      .map(_.reverse.takeWhile(_ != '.').reverse.toLowerCase)
      .filter(e => e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit))

  final case class Accepted(jobId: com.histmersion.api.jobs.JobInfo.Id, citekey: com.histmersion.api.library.Citekey, status: Accepted.Status, sha256: Option[com.histmersion.api.Sha256] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Accepted:
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Params(
      citekey: com.histmersion.api.library.Citekey,
      filename: Option[Params.Filename] = None,
      quality: Params.Quality = false,
      force: Params.Force = false,
      title: Option[com.histmersion.api.library.Title] = None,
      authors: List[com.histmersion.api.library.AuthorName] = Nil,
      year: Option[com.histmersion.api.catalogue.Year] = None,
      doi: Option[com.histmersion.api.library.Doi] = None,
      provenance: Option[Params.Provenance] = None,
      acquireItem: Option[Params.AcquireItem] = None
  )

  object Params:
    type Filename = Filename.T
    object Filename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Quality = Quality.T
    object Quality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Force = Force.T
    object Force extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Provenance = Provenance.T
    object Provenance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AcquireItem = AcquireItem.T
    object AcquireItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    type DropIn = DropIn.T
    object DropIn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Request(
      id: Request.Id,
      citekey: Option[com.histmersion.api.library.Citekey] = None,
      arxiv: Option[Request.Arxiv] = None,
      refetch: Request.Refetch = false,
      legalOnly: Request.LegalOnly = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Request:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Arxiv = Arxiv.T
    object Arxiv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Refetch = Refetch.T
    object Refetch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LegalOnly = LegalOnly.T
    object LegalOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Step(step: Step.Value, ok: Step.Ok, detail: Step.Detail) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Step:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Ok = Ok.T
    object Ok extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Detail = Detail.T
    object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
