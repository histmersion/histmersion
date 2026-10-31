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
  def url: String

  /** Pure: the dataset's places from its text, in the order they are stored. */
  def read(text: String): Either[String, List[GazetteerPlace]]

  def fetch(http: PluginHttp): Either[String, List[GazetteerPlace]] = download(http).flatMap(read)

  /** The dataset's text: one request, answered 200, unpacked when the address names a zip. */
  def download(http: PluginHttp): Either[String, String] =
    http.fetch(url) match
      case Left(e)                     => Left(s"download failed: $e")
      case Right(r) if r.status != 200 => Left(s"HTTP ${r.status} from $url")
      case Right(r)                    =>
        if url.endsWith(".zip") then DownloadedGazetteer.unzip(r.body) else Right(String(r.body, UTF_8))

  /** A property's text: the first of `keys` that holds a non-empty string. */
  protected def str(o: JsonObject, keys: String*): Option[String] =
    keys.iterator.flatMap(k => o(k).flatMap(_.asString).filter(_.nonEmpty)).nextOption()

  /** A name as an id part: lower case, every run of other characters than `a-z0-9` one `_`, none at the ends. */
  protected def slugify(name: String): String =
    name.toLowerCase.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "")

  /** The period of two years in either order; `None` when one is beyond what a [[Year]] holds. */
  protected def span(a: Long, b: Long): Option[Period] =
    Option.when(a.isValidInt && b.isValidInt)(()).flatMap(_ =>
      Period.from(Year(a.min(b).toInt), Year(a.max(b).toInt)).toOption
    )

object DownloadedGazetteer:
  /** The first usable file of a zip archive, as UTF-8 text. */
  def unzip(data: Array[Byte]): Either[String, String] =
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
