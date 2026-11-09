package com.histmersion
package plugins.gazetteers
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.plugin.*
import io.circe.JsonObject

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.ZipInputStream
import io.github.iltotore.iron.constraint.any.Pure

/** A gazetteer that is one file at a fixed address: downloaded whole through the host's client, unpacked when it is a
  * zip (its first file that is neither a directory nor macOS's `__MACOSX` metadata), read as UTF-8 and parsed.
  */
trait DownloadedGazetteer extends GazetteerSource:
  /** The dataset's canonical address. */
  def url: Plugin.Http.Url

  /** Pure: the dataset's places from its text, in the order they are stored. */
  def read(text: DownloadedGazetteer.Text): Either[Problem, List[GazetteerPlace]]

  def fetch(http: Plugin.Http): Either[Problem, List[GazetteerPlace]] = download(http).flatMap(read)

  /** The dataset's text: one request, answered 200, unpacked when the address names a zip. */
  def download(http: Plugin.Http): Either[Problem, DownloadedGazetteer.Text] =
    http.fetch(url) match
      case Left(e) => Left(Problem.Unclassified(Legacy.Text(s"download failed: ${e.problem.legacyText}")))
      case Right(r) if r.status.value != 200 =>
        Left(Problem.Unclassified(Legacy.Text(s"HTTP ${r.status.value} from ${url.value}")))
      case Right(r) =>
        if url.value.endsWith(".zip") then DownloadedGazetteer.unzip(r.body)
        else Right(DownloadedGazetteer.Text(String(r.body.value, UTF_8)))

  /** A property's text: the first of `keys` that holds a non-empty string. */
  protected def str(o: JsonObject, keys: String*): Option[DownloadedGazetteer.Text] =
    keys.iterator
      .flatMap(k => o(k).flatMap(_.asString).filter(_.nonEmpty))
      .nextOption()
      .map(DownloadedGazetteer.Text(_))

  /** A name as an id part: lower case, every run of other characters than `a-z0-9` one `_`, none at the ends. */
  protected def slugify(name: GazetteerName.Name): DownloadedGazetteer.Slug =
    DownloadedGazetteer.Slug(name.value.toLowerCase.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", ""))

  /** The period of two years in either order; `None` when one is beyond what a [[Year]] holds. */
  protected def span(a: DownloadedGazetteer.Year, b: DownloadedGazetteer.Year): Option[Period] =
    Option
      .when(a.value.isValidInt && b.value.isValidInt)(())
      .flatMap(_ => Period.from(Year(a.value.min(b.value).toInt), Year(a.value.max(b.value).toInt)).toOption)

object DownloadedGazetteer:
  type Slug = Slug.T
  object Slug extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Year = Year.T
  object Year extends Newtype[Long, Pure]

  /** The first usable file of a zip archive, as UTF-8 text. */

  def unzip(data: Plugin.HttpResponse.Body): Either[Problem, Text] =
    try
      val zis = ZipInputStream(ByteArrayInputStream(data.value))
      @annotation.tailrec
      def usable(e: java.util.zip.ZipEntry): Option[java.util.zip.ZipEntry] =
        if e == null then None
        else if e.isDirectory || e.getName.startsWith("__MACOSX") then usable(zis.getNextEntry)
        else Some(e)
      usable(zis.getNextEntry) match
        case None    => Left(Problem.Unclassified(Legacy.Text("zip contains no usable file")))
        case Some(_) => Right(Text(String(zis.readAllBytes(), UTF_8)))
    catch case e: Exception => Left(Problem.Unclassified(Legacy.Text(s"unzip failed: ${e.getMessage}")))
