package com.histmersion.plugin.testkit

import com.histmersion.pluginhost.{HttpAnswer, HttpTransport}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.Locale
import scala.jdk.CollectionConverters.*

/** One recorded exchange: the request's method and URL, and the answer — its status, the headers kept
  * and the file its body is in — or the error the request ended with. */
private[testkit] final case class RecordedExchange(method: String, url: String, status: Option[Int],
    headers: List[(String, String)], body: Option[String], error: Option[String])

private[testkit] object RecordedExchange:
  val Suffix = ".http"

  /** The answer headers worth keeping: what a plugin or the host's policy reads. Everything else — and
    * so every cookie and credential — is left out. */
  val KeptHeaders: List[String] = List("content-type", "content-disposition", "location", "retry-after", "cf-mitigated",
    "mediawiki-api-error", "link", "last-modified", "etag")

  /** Pure: the exchange as its file holds it. */
  def render(e: RecordedExchange): String =
    (List(s"${e.method} ${e.url}") ++ e.status.map(s => s"status: $s") ++ e.headers.map((k, v) => s"$k: $v") ++
      e.body.map(b => s"body: $b") ++ e.error.map(m => s"error: $m")).mkString("", "\n", "\n")

  /** Pure: the exchange from its file. Blank lines and lines starting with `#` (a note on how the body
    * was trimmed) are skipped. */
  def parse(text: String): Either[String, RecordedExchange] =
    val lines = text.linesIterator.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#")).toList
    lines match
      case Nil => Left("empty")
      case request :: fields =>
        request.split(" ", 2) match
          case Array(method, url) =>
            val pairs = fields.map(_.split(":", 2)).collect { case Array(k, v) => k.trim.toLowerCase(Locale.ROOT) -> v.trim }
            def field(k: String) = pairs.collectFirst { case (`k`, v) => v }
            val status = field("status").map(s => s.toIntOption.toRight(s"status '$s' is not a number"))
            status match
              case Some(Left(e)) => Left(e)
              case _ =>
                val code = status.flatMap(_.toOption)
                if code.isEmpty && field("error").isEmpty then Left("neither a status nor an error")
                else Right(RecordedExchange(method.toUpperCase(Locale.ROOT), url, code,
                  pairs.filterNot((k, _) => Set("status", "body", "error")(k)), field("body"), field("error")))
          case _ => Left(s"'$request' is not '<METHOD> <URL>'")

  /** Pure: the extension a body file gets from its content type. */
  def extension(contentType: Option[String]): String =
    val t = contentType.getOrElse("").toLowerCase(Locale.ROOT)
    if t.contains("json") then "json"
    else if t.contains("html") then "html"
    else if t.contains("xml") then "xml"
    else if t.contains("pdf") then "pdf"
    else if t.contains("epub") then "epub"
    else if t.startsWith("text/") then "txt"
    else if t.startsWith("image/") then t.stripPrefix("image/").takeWhile(_.isLetterOrDigit)
    else "bin"

  /** Pure: whether a body of this type is text, so a secret in it can be replaced. */
  def isText(contentType: Option[String]): Boolean =
    contentType.map(_.toLowerCase(Locale.ROOT)).exists(t =>
      t.startsWith("text/") || t.contains("json") || t.contains("xml") || t.contains("javascript"))

  /** Pure: the host part of a URL, as a file name may hold it. */
  def hostOf(url: String): String =
    "(?i)^[a-z][a-z0-9+.-]*://([^/?#:]+)".r.findFirstMatchIn(url).map(_.group(1).toLowerCase(Locale.ROOT))
      .getOrElse("request").replaceAll("[^a-z0-9.-]", "_")

/** Replaces the value of every secret the harness knows with `{secret}`. Values shorter than four
  * characters are left alone: they would match inside ordinary words. */
private[testkit] final class Scrubber(secrets: Set[String]):
  private val values = secrets.filter(_.length >= 4).toList.sortBy(-_.length)
  def apply(text: String): String = values.foldLeft(text)((t, v) => t.replace(v, Scrubber.Placeholder))
  def bytes(body: Array[Byte]): Array[Byte] =
    if values.isEmpty then body
    else
      val text = new String(body, UTF_8)
      val clean = apply(text)
      if clean == text then body else clean.getBytes(UTF_8)

private[testkit] object Scrubber:
  val Placeholder = "{secret}"

/** The wire of one harness: every request it makes, in order, and — in [[HttpMode.Replay]] — those it
  * had no recording of. Each channel's transport (the acquisition client's, a media collection's, a
  * catalogue's) goes through it. Calls may come from several threads at once (a plugin's fetcher), so
  * its state is guarded by `this`. */
private[testkit] final class Wire(mode: HttpMode, scrub: Scrubber):
  private val asked   = List.newBuilder[String]
  private val missing = List.newBuilder[String]
  // the next recording's number, and whether this harness has emptied the directory yet
  private var written = 0
  private var cleared = false
  private lazy val recorded: Map[(String, String), Vector[RecordedExchange]] = mode match
    case HttpMode.Replay(dir) => Wire.load(dir)
    case _                    => Map.empty
  private val served = scala.collection.mutable.Map.empty[(String, String), Int]

  def requests: List[String] = synchronized(asked.result())
  def unrecorded: List[String] = synchronized(missing.result())

  /** Fails the test when a replayed request had no recording. */
  def check(): Unit =
    mode match
      case HttpMode.Replay(dir) =>
        val urls = unrecorded
        if urls.nonEmpty then throw UnrecordedRequest(urls, dir)
      case _ => ()

  /** The transport of one channel: `live` is built only when a request goes to the network. */
  def transport(live: => HttpTransport): HttpTransport = new HttpTransport:
    private lazy val network = live
    def get(url: String, headers: Map[String, String]): Either[String, HttpAnswer] =
      val shown = scrub(url)
      synchronized(asked += s"GET $shown")
      mode match
        case HttpMode.Live => network.get(url, headers)
        case HttpMode.Record(dir) =>
          val answer = network.get(url, headers)
          record(dir, shown, answer)
          answer
        case HttpMode.Replay(dir) => replay(dir, shown)
        case HttpMode.Stub(answers) =>
          Right(answers.get(url).fold(HttpAnswer(404, Map.empty, s"no stub answer for $url".getBytes(UTF_8)))(a =>
            HttpAnswer(a.status, a.headers.map((k, v) => k.toLowerCase(Locale.ROOT) -> v), a.body)))

  private def record(dir: Path, url: String, answer: Either[String, HttpAnswer]): Unit = synchronized {
    if !cleared then
      Wire.clear(dir)
      cleared = true
    written += 1
    val base = f"$written%03d-${RecordedExchange.hostOf(url)}"
    val exchange = answer match
      case Left(error) => RecordedExchange("GET", url, None, Nil, None, Some(scrub(error)))
      case Right(a) =>
        val kept = RecordedExchange.KeptHeaders.flatMap(h => a.header(h).map(v => h -> scrub(v)))
        val contentType = a.header("content-type")
        val bodyFile = Option.when(a.body.nonEmpty)(s"$base.${RecordedExchange.extension(contentType)}")
        bodyFile.foreach(f => Files.write(dir.resolve(f),
          if RecordedExchange.isText(contentType) then scrub.bytes(a.body) else a.body))
        RecordedExchange("GET", url, Some(a.code), kept, bodyFile, None)
    Files.writeString(dir.resolve(base + RecordedExchange.Suffix), RecordedExchange.render(exchange), UTF_8)
  }

  private def replay(dir: Path, url: String): Either[String, HttpAnswer] = synchronized {
    recorded.get(("GET", url)) match
      case None =>
        missing += url
        Left(s"$url: no recording in $dir")
      case Some(answers) =>
        val n = served.getOrElse(("GET", url), 0)
        served(("GET", url)) = n + 1
        val e = answers(n.min(answers.size - 1))
        (e.status, e.error) match
          case (Some(code), _) =>
            val body = e.body.map(f => Files.readAllBytes(dir.resolve(f))).getOrElse(Array.emptyByteArray)
            Right(HttpAnswer(code, e.headers.toMap, body))
          case (None, error) => Left(error.getOrElse(s"$url: recorded with neither a status nor an error"))
  }

private[testkit] object Wire:
  /** Every exchange recorded in `dir`, by method and URL, in the order they were recorded. A file
    * that does not parse fails the test, naming it. */
  def load(dir: Path): Map[(String, String), Vector[RecordedExchange]] =
    if !Files.isDirectory(dir) then Map.empty
    else
      val files = Files.list(dir)
      val exchanges =
        try files.iterator.asScala.filter(_.getFileName.toString.endsWith(RecordedExchange.Suffix)).toList
            .sortBy(_.getFileName.toString)
        finally files.close()
      exchanges.map(f => RecordedExchange.parse(Files.readString(f, UTF_8)).fold(
        e => throw AssertionError(s"$f: not a recorded exchange: $e"), identity))
        .groupBy(e => (e.method, e.url)).view.mapValues(_.toVector).toMap

  /** Removes a previous recording's files: every exchange and the bodies they name. */
  def clear(dir: Path): Unit =
    Files.createDirectories(dir)
    val files = Files.list(dir)
    val exchanges =
      try files.iterator.asScala.filter(_.getFileName.toString.endsWith(RecordedExchange.Suffix)).toList
      finally files.close()
    exchanges.foreach { f =>
      RecordedExchange.parse(Files.readString(f, UTF_8)).toOption.flatMap(_.body).foreach(b => Files.deleteIfExists(dir.resolve(b)))
      Files.delete(f)
    }
