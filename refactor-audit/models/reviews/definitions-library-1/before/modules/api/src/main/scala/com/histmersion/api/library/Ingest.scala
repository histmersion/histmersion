package com.histmersion.api.library
import com.histmersion.api.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object Ingest:
  val CodeConflict = "conflict"

  val AssetCacheControl = "public, max-age=31536000, immutable"

  val MediaCacheControl = "public, max-age=86400"

  val KindIngest = com.histmersion.api.library.IngestJobKinds.Ingest.kind.value
  val KindReocr = com.histmersion.api.library.IngestJobKinds.Reocr.kind.value

  def originalDisposition(citekey: String, storedName: String): String =
    val ext = storedName.reverse.takeWhile(_ != '.').reverse
    s"""attachment; filename="$citekey.${if ext == storedName then "bin" else ext}""""

  def dispositionExtension(disposition: String): Option[String] =
    """filename="?([^";]+)"?""".r
      .findFirstMatchIn(disposition)
      .map(_.group(1).trim)
      .map(_.reverse.takeWhile(_ != '.').reverse.toLowerCase)
      .filter(e => e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit))

  final case class Accepted(jobId: String, citekey: String, status: String, sha256: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class Params(
      citekey: String,
      filename: Option[String] = None,
      quality: Boolean = false,
      force: Boolean = false,
      title: Option[String] = None,
      authors: List[String] = Nil,
      year: Option[Int] = None,
      doi: Option[String] = None,
      provenance: Option[String] = None,
      acquireItem: Option[Int] = None
  )

  final case class Report(
      citekey: String,
      doi: Option[String],
      title: Option[String],
      created: Boolean,
      pdf: Option[PdfInfo],
      edgesAdded: Int,
      assessment: Option[Assessment],
      steps: List[Step],
      dropIn: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class Request(
      id: String,
      citekey: Option[String] = None,
      arxiv: Option[String] = None,
      refetch: Boolean = false,
      legalOnly: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class Step(step: String, ok: Boolean, detail: String) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
