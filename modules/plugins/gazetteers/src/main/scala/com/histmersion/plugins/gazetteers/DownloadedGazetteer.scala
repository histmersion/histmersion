package com.histmersion.plugins.gazetteers

import com.histmersion.plugin.*
import io.circe.JsonObject

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.ZipInputStream

/** A gazetteer that is one file at a fixed address: downloaded whole through the host's client, unpacked when it is a
  * zip (its first file that is neither a directory nor macOS's `__MACOSX` metadata), read as UTF-8 and parsed.
  */
trait DownloadedGazetteer extends GazetteerSource:
  /** The dataset's canonical address. */
  def url: com.histmersion.plugin.Plugin.Http.GetUrl

  /** Pure: the dataset's places from its text, in the order they are stored. */
  def read(text: com.histmersion.plugins.gazetteers.DownloadedGazetteer.Text): Either[com.histmersion.plugins.gazetteers.DownloadedGazetteer.Problem, List[GazetteerPlace]]

  def fetch(http: Plugin.Http): Either[com.histmersion.plugins.gazetteers.DownloadedGazetteer.Problem, List[GazetteerPlace]] = download(http).flatMap(read)

  /** The dataset's text: one request, answered 200, unpacked when the address names a zip. */
  def download(http: Plugin.Http): Either[com.histmersion.plugins.gazetteers.DownloadedGazetteer.Problem, com.histmersion.plugins.gazetteers.DownloadedGazetteer.Text] =
    http.fetch(url) match
      case Left(e)                     => Left(s"download failed: $e")
      case Right(r) if r.status != 200 => Left(s"HTTP ${r.status} from $url")
      case Right(r)                    =>
        if url.endsWith(".zip") then DownloadedGazetteer.unzip(r.body) else Right(String(r.body, UTF_8))

  /** A property's text: the first of `keys` that holds a non-empty string. */
  protected def str(o: JsonObject, keys: String*): Option[com.histmersion.plugins.gazetteers.DownloadedGazetteer.Text] =
    keys.iterator.flatMap(k => o(k).flatMap(_.asString).filter(_.nonEmpty)).nextOption()

  /** A name as an id part: lower case, every run of other characters than `a-z0-9` one `_`, none at the ends. */
  protected def slugify(name: com.histmersion.plugin.GazetteerName.Name): com.histmersion.plugins.gazetteers.DownloadedGazetteer.Slug =
    name.toLowerCase.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "")

  /** The period of two years in either order; `None` when one is beyond what a [[Year]] holds. */
  protected def span(a: com.histmersion.plugins.gazetteers.DownloadedGazetteer.Year, b: com.histmersion.plugins.gazetteers.DownloadedGazetteer.Year): Option[Period] =
    Option
      .when(a.isValidInt && b.isValidInt)(())
      .flatMap(_ => Period.from(Year(a.min(b).toInt), Year(a.max(b).toInt)).toOption)

object DownloadedGazetteer:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Year = Year.T
  object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The first usable file of a zip archive, as UTF-8 text. */


  def unzip(data: com.histmersion.plugin.FetchedFile.Bytes): Either[com.histmersion.plugins.gazetteers.DownloadedGazetteer.Problem, com.histmersion.plugins.gazetteers.DownloadedGazetteer.Text] =
    try
      val zis = ZipInputStream(ByteArrayInputStream(data))
      @annotation.tailrec
      def usable(e: java.util.zip.ZipEntry): Option[java.util.zip.ZipEntry] =
        if e == null then None
        else if e.isDirectory || e.getName.startsWith("__MACOSX") then usable(zis.getNextEntry)
        else Some(e)
      usable(zis.getNextEntry) match
        case None    => Left("zip contains no usable file")
        case Some(_) => Right(String(zis.readAllBytes(), UTF_8))
    catch case e: Exception => Left(s"unzip failed: ${e.getMessage}")
