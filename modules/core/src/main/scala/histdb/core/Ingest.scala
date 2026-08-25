package histdb.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** OCR that keeps the figures, and OCR as a JOB (plan §1.8).
  *
  * Three things live here, all of them pure:
  *   - the wire DTOs of the ingest surface (uploads, jobs, assets, the converter's own response),
  *   - [[AssetLinks]]: the ONE place that knows how an image link is spelled in each context,
  *   - [[JobState]]: the state machine, so "which transitions are legal" is unit-testable.
  *
  * The link spellings, in the order a figure travels through the system:
  *   1. `figures/<name>` — the old file pipeline's `md/<citekey>/figures/…` (seed importer input),
  *   2. `assets/<name>`  — CANONICAL: what the converter returns and what is stored in the body,
  *   3. `/source/<ck>/assets/<name>`             — what the HTML page and the web UI request,
  *   4. `<publicUrl>/source/<ck>/assets/<name>`  — what MCP hands an image-capable client.
  */
object AssetLinks:

  /** Markdown image link: `![alt](target)`, optionally `<>`-wrapped or with a "title". */
  private val ImageLink = """!\[([^\]]*)\]\(\s*<?([^)>\s]+)>?(\s+"[^"]*")?\s*\)""".r

  /** The canonical, relative prefix a stored body uses. */
  val Prefix = "assets/"

  /** Rewrite every image link whose target is `<from><name>` to `<to><name>`, leaving alt text,
    * titles, non-image links and absolute/foreign targets untouched. */
  private def retarget(md: String, from: String, to: String): String =
    ImageLink.replaceAllIn(md, m =>
      val alt    = m.group(1)
      val target = m.group(2)
      val title  = Option(m.group(3)).getOrElse("")
      if !target.startsWith(from) then java.util.regex.Matcher.quoteReplacement(m.matched)
      else java.util.regex.Matcher.quoteReplacement(s"![$alt]($to${target.drop(from.length)}$title)"))

  /** The old pipeline's `figures/<name>` → the canonical `assets/<name>` (seed import, plan §1.8). */
  def fromFigures(md: String): String = retarget(md, "figures/", Prefix)

  /** `assets/<name>` → `/source/<citekey>/assets/<name>` — the path the server serves. */
  def toWeb(md: String, citekey: String): String = retarget(md, Prefix, webBase(citekey))

  /** `assets/<name>` → `<publicUrl>/source/<citekey>/assets/<name>` — absolute, for MCP clients that
    * can fetch images themselves. A blank `publicUrl` degrades to the web-relative form. */
  def toAbsolute(md: String, citekey: String, publicUrl: String): String =
    retarget(md, Prefix, publicUrl.trim.stripSuffix("/") + webBase(citekey))

  /** The URL path an asset is served under. */
  def webBase(citekey: String): String = s"/source/${enc(citekey)}/assets/"

  def url(citekey: String, name: String): String = webBase(citekey) + enc(name)

  private def enc(s: String): String =
    java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

  /** `<img …>` that already carries a `loading=` attribute. */
  private val BareImg = """<img(?![^>]*\bloading=)\s""".r

  /** Add `loading="lazy"` to every rendered `<img>` that has not got it — the figures of a 300-page
    * paper must not all download at once. Applied to flexmark's HTML output. */
  def lazyImages(html: String): String =
    BareImg.replaceAllIn(html, _ => java.util.regex.Matcher.quoteReplacement("""<img loading="lazy" """))

  /** A link-safe asset name: no directories, no characters that would need escaping in a URL or a
    * Markdown link. Mirrors `converter/app.py::_safe_name`, because either side may name a file. */
  def safeName(raw: String): String =
    val base    = raw.replace('\\', '/').split('/').lastOption.getOrElse("")
    val cleaned = base.map(c => if c.isLetterOrDigit || c == '.' || c == '_' || c == '-' then c else '-')
      .mkString.replaceAll("^-+|-+$", "")
    if cleaned.isEmpty then "image.png" else cleaned

  /** Mime type from a file extension — the seed importer reads figures off disk, where there is no
    * `Content-Type` to inherit. */
  def mimeOf(name: String): String =
    name.toLowerCase.reverse.takeWhile(_ != '.').reverse match
      case "png"          => "image/png"
      case "jpg" | "jpeg" => "image/jpeg"
      case "gif"          => "image/gif"
      case "webp"         => "image/webp"
      case "svg"          => "image/svg+xml"
      case _              => "application/octet-stream"

/** Pixel dimensions read straight from an image's header — PNG, JPEG and GIF.
  *
  * The converter reports the sizes it produced, but figures picked up off disk by the seed importer
  * arrive as bare bytes. Rather than pull in an image library (ImageIO is a native-image liability),
  * the three headers we actually store are parsed by hand: it is a dozen lines, it is pure, and it is
  * unit-testable. Anything unrecognised is simply `None` — the columns are optional. */
object ImageSize:

  def of(bytes: Array[Byte]): Option[(Int, Int)] =
    if png(bytes) then pngSize(bytes)
    else if gif(bytes) then gifSize(bytes)
    else if jpeg(bytes) then jpegSize(bytes)
    else None

  private def u8(b: Array[Byte], i: Int): Int  = b(i) & 0xff
  private def be16(b: Array[Byte], i: Int): Int = (u8(b, i) << 8) | u8(b, i + 1)
  private def le16(b: Array[Byte], i: Int): Int = (u8(b, i + 1) << 8) | u8(b, i)
  private def be32(b: Array[Byte], i: Int): Int =
    (u8(b, i) << 24) | (u8(b, i + 1) << 16) | (u8(b, i + 2) << 8) | u8(b, i + 3)

  private def png(b: Array[Byte]): Boolean =
    b.length >= 24 && u8(b, 0) == 0x89 && b(1) == 'P' && b(2) == 'N' && b(3) == 'G'

  /** IHDR is always the first chunk: 8-byte signature, 4-byte length, "IHDR", width, height. */
  private def pngSize(b: Array[Byte]): Option[(Int, Int)] = Some((be32(b, 16), be32(b, 20)))

  private def gif(b: Array[Byte]): Boolean =
    b.length >= 10 && b(0) == 'G' && b(1) == 'I' && b(2) == 'F'

  private def gifSize(b: Array[Byte]): Option[(Int, Int)] = Some((le16(b, 6), le16(b, 8)))

  private def jpeg(b: Array[Byte]): Boolean =
    b.length >= 4 && u8(b, 0) == 0xff && u8(b, 1) == 0xd8

  /** Walk the segment chain to the first SOFn frame header (height then width, 16-bit big-endian).
    * SOF4/SOF8/SOF12 are not frame headers (DHT / JPG / DAC) and must be skipped. */
  private def jpegSize(b: Array[Byte]): Option[(Int, Int)] =
    @annotation.tailrec
    def go(i: Int): Option[(Int, Int)] =
      if i + 9 >= b.length || u8(b, i) != 0xff then None
      else
        val marker = u8(b, i + 1)
        val len    = be16(b, i + 2)
        if len < 2 then None
        else if (marker >= 0xc0 && marker <= 0xcf) && marker != 0xc4 && marker != 0xc8 && marker != 0xcc
        then Some((be16(b, i + 7), be16(b, i + 5)))
        else go(i + 2 + len)
    go(2)

/** Constants of the ingest surface, shared by the endpoint definitions and the handlers. */
object Ingest:
  /** [[ApiError.code]] for "this exact file is already stored under another citekey" → HTTP 409. */
  val CodeConflict = "conflict"

  /** Assets are content-addressed in practice (a re-conversion rewrites body AND names together), so
    * they can be cached forever. */
  val AssetCacheControl = "public, max-age=31536000, immutable"

  val KindIngest = "ingest"
  val KindReocr  = "reocr"

/** The lifecycle of an OCR job. `queued → running → done|failed` and nothing else; a restart turns
  * every `running` row into `failed: interrupted`, because nothing survives the process. */
enum JobState:
  case Queued, Running, Done, Failed

  def wire: String = toString.toLowerCase
  def isTerminal: Boolean = this == Done || this == Failed

object JobState:
  val Interrupted = "interrupted"

  def parse(s: String): Option[JobState] =
    values.find(_.wire == s.trim.toLowerCase)

  /** The legal transitions, as a total function: `None` means "refuse, the job has moved on". */
  def next(from: JobState, to: JobState): Option[JobState] = (from, to) match
    case (Queued, Running)  => Some(Running)
    case (Queued, Failed)   => Some(Failed)   // cancelled before a worker picked it up
    case (Running, Done)    => Some(Done)
    case (Running, Failed)  => Some(Failed)
    case _                  => None

/** What `histdb ingest` uploads about the file, beside the bytes themselves. Query parameters rather
  * than a multipart part list: tapir's netty server has no multipart request-body support (it throws
  * `UnsupportedOperationException`), so the upload is a raw body plus typed parameters. */
final case class IngestParams(
    citekey: String,
    filename: Option[String] = None,
    quality: Boolean = false,
    force: Boolean = false,
    title: Option[String] = None,
    authors: List[String] = Nil,
    year: Option[Int] = None,
    doi: Option[String] = None,
    provenance: Option[String] = None
)

/** Rendering of the ingest surface — kept out of [[Markdown]] so concurrent streams do not all
  * append to the same file. Same contract: every surface answers Markdown. */
object IngestMarkdown:

  def job(j: JobInfo): String =
    val lines = List(
      Some(s"- **status:** ${j.status}"),
      j.citekey.map(c => s"- **citekey:** $c"),
      j.progress.filter(_.nonEmpty).map(p => s"- **progress:** $p"),
      j.error.filter(_.nonEmpty).map(e => s"- **error:** $e"),
      j.sha256.map(s => s"- **sha256:** ${s.take(12)}…"),
      Some(s"- **updated:** ${j.updatedAt}")
    ).flatten
    s"### job ${j.id} · ${j.kind}\n\n${lines.mkString("\n")}"

  def jobs(js: Vector[JobInfo]): String =
    if js.isEmpty then "_No jobs._"
    else
      val rows = js.map(j =>
        s"| ${j.id} | ${j.kind} | ${j.citekey.getOrElse("")} | ${j.status} | " +
          s"${j.progress.getOrElse("")}${j.error.map(e => s" — $e").getOrElse("")} |")
      (List(s"### ${js.size} job(s)", "", "| id | kind | citekey | status | detail |", "|---|---|---|---|---|") ++ rows)
        .mkString("\n")

  def assets(citekey: String, as: Vector[AssetInfo]): String =
    if as.isEmpty then s"_No assets stored for **$citekey**._"
    else
      val rows = as.map { a =>
        val size = (a.width, a.height) match
          case (Some(w), Some(h)) => s"$w×$h"
          case _                  => "—"
        s"| ${a.name} | ${a.mime} | $size | ${a.bytes} | `${AssetLinks.url(citekey, a.name)}` |"
      }
      (List(s"### ${as.size} asset(s) · $citekey", "",
        "| name | mime | size | bytes | url |", "|---|---|---|---:|---|") ++ rows).mkString("\n")

// ── wire DTOs (declared last: see AGENTS.md on `derives KindlingsCodecAsObject` ordering) ─────────

/** One image as it travels on the wire — from the converter, and into `POST /api/sources/{ck}/assets`. */
final case class AssetInput(
    name: String,
    mime: String,
    base64: String,
    width: Option[Int] = None,
    height: Option[Int] = None,
    sha256: Option[String] = None
) derives KindlingsCodecAsObject
object AssetInput:
  given Schema[AssetInput] = KindlingsSchema.derived[AssetInput].schema

/** A stored asset, without the bytes (the listing endpoint / `histdb assets`). */
final case class AssetInfo(
    name: String,
    mime: String,
    bytes: Int,
    sha256: String,
    width: Option[Int] = None,
    height: Option[Int] = None
) derives KindlingsCodecAsObject
object AssetInfo:
  given Schema[AssetInfo] = KindlingsSchema.derived[AssetInfo].schema

/** How many assets a write stored. */
final case class AssetsStored(citekey: String, stored: Int) derives KindlingsCodecAsObject
object AssetsStored:
  given Schema[AssetsStored] = KindlingsSchema.derived[AssetsStored].schema

/** One OCR job. `status` is a [[JobState]] spelling; `progress` is free text for the CLI's line. */
final case class JobInfo(
    id: String,
    kind: String,
    status: String,
    citekey: Option[String] = None,
    progress: Option[String] = None,
    error: Option[String] = None,
    sha256: Option[String] = None,
    createdAt: String = "",
    updatedAt: String = ""
) derives KindlingsCodecAsObject:
  def state: JobState        = JobState.parse(status).getOrElse(JobState.Failed)
  def isTerminal: Boolean    = state.isTerminal
  def succeeded: Boolean     = state == JobState.Done
object JobInfo:
  given Schema[JobInfo] = KindlingsSchema.derived[JobInfo].schema

/** `POST /api/ingest` / `POST /api/sources/{ck}/reocr` — 202 with the job to poll. */
final case class IngestAccepted(jobId: String, citekey: String, status: String, sha256: Option[String] = None)
    derives KindlingsCodecAsObject
object IngestAccepted:
  given Schema[IngestAccepted] = KindlingsSchema.derived[IngestAccepted].schema

/** The converter sidecar's `/convert` response (see `converter/app.py`). */
final case class ConvertedImage(
    name: String,
    mime: String,
    base64: String,
    width: Option[Int] = None,
    height: Option[Int] = None,
    sha256: Option[String] = None
) derives KindlingsCodecAsObject
object ConvertedImage:
  given Schema[ConvertedImage] = KindlingsSchema.derived[ConvertedImage].schema

final case class ConvertedDoc(
    markdown: String,
    pages: Int = 0,
    images: List[ConvertedImage] = Nil,
    filename: Option[String] = None
) derives KindlingsCodecAsObject
object ConvertedDoc:
  given Schema[ConvertedDoc] = KindlingsSchema.derived[ConvertedDoc].schema
