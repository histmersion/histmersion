package com.histmersion.server.importers

import com.histmersion.api.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

/** The recorded traffic and results of the network gazetteers (Cliopatria, AWMC, ChronOntology): each download
  * answered by a loopback server from a fixture (Cliopatria's zipped, as its upstream ships it) — the request it sent,
  * every place it made (the parents included, in order) and its dry run; then the failures (an error answer, a zip
  * with no usable file, a body that is not JSON). No database and no network. The transcript is
  * `traffic/gazetteers.txt`, compared byte for byte (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites it): moving the
  * gazetteers into plugins changes no request and no place.
  *
  * The headers that only the HTTP client chooses (`Host`, `Connection`, `Accept`, `Content-Length`, an HTTP/2
  * upgrade's) are left out of the transcript; `HISTMERSION_TRAFFIC_RAW=1` prints every request with all of them.
  */
class GazetteerTrafficSuite extends munit.FunSuite:

  private val loop = GazetteerLoopback()
  override def afterAll(): Unit = loop.stop()

  private def fixture(name: String): Array[Byte] =
    Files.readAllBytes(Path.of(s"modules/server/src/test/resources/gazetteers/$name"))

  private val Transport = Set("host", "connection", "accept", "content-length", "upgrade", "http2-settings")

  /** The source's real address. */
  private def sourceUrl(name: String): String = GazetteerImporter.byName(name).get.fetchUrl

  // ── the code under test: today's importers and runner ─────────────────────────────────────────

  /** Every place a download of `name` makes, in the order the runner upserts them. */
  private def places(name: String): Either[String, List[PlaceInput]] =
    val imp = GazetteerImporter.byName(name).get
    for
      json <- ImportRunner.download(loop.local(imp.fetchUrl))
      items <- imp.transform(json)
    yield
      val parents = if name == "cliopatria" then CliopatriaImporter.parentConcepts(items) else Nil
      parents ++ items

  private def dryRun(name: String): Either[String, PlaceImportResult] =
    ImportRunner.fetchAndImport(
      GazetteerImporter.byName(name).get,
      null,
      dryRun = true,
      0.0,
      Some(loop.local(sourceUrl(name)))
    )

  // ── the transcript ──────────────────────────────────────────────────────────────────────────

  private def normalised(s: String, name: String): String =
    val real = java.net.URI.create(sourceUrl(name))
    s.replace(loop.origin, "<origin>")
      .replace(s"${real.getScheme}://${real.getRawAuthority}", "<origin>")
      .replace(Version.version, "<version>")

  private def requests(name: String): String =
    loop.take().map { r =>
      if sys.env.get("HISTMERSION_TRAFFIC_RAW").contains("1") then
        println(s"[$name] ${r.method} ${r.target}\n" + r.headers.sorted.map((k, v) => s"    $k: $v").mkString("\n"))
      s"${r.method} ${r.target}\n" +
        r.headers
          .filterNot((k, _) => Transport(k.toLowerCase(java.util.Locale.ROOT)))
          .sorted
          .map((k, v) => s"  $k: ${normalised(v, name)}\n")
          .mkString
    }.mkString

  private def run(sb: StringBuilder, name: String, served: String, body: Array[Byte]): Unit =
    sb ++= s"## $name: $served\nsource: ${sourceUrl(name)}\n"
    loop.answerWith(200, body)
    val made = places(name)
    sb ++= requests(name)
    made match
      case Left(e)   => sb ++= s"places: failed: ${normalised(e, name)}\n"
      case Right(ps) => sb ++= s"places: ${ps.size}\n" + ps.map(p => s"  $p\n").mkString
    val dry = dryRun(name)
    sb ++= requests(name)
    sb ++= s"dry run: ${dry.fold(e => s"failed: ${normalised(e, name)}", _.toString)}\n"

  private def zipped(name: String): Array[Byte] =
    GazetteerLoopback.zip(
      List(
        "__MACOSX/._cliopatria.geojson" -> "mac".getBytes(UTF_8),
        "cliopatria/" -> Array.emptyByteArray,
        "cliopatria.geojson" -> fixture(name),
        "README.md" -> "not read".getBytes(UTF_8)
      )
    )

  private def transcript: String =
    val sb = StringBuilder()
    run(sb, "cliopatria", "cliopatria.geojson, zipped", zipped("cliopatria.geojson"))
    run(sb, "cliopatria", "cliopatria-more.geojson, zipped", zipped("cliopatria-more.geojson"))
    run(sb, "awmc", "awmc.geojson", fixture("awmc.geojson"))
    run(sb, "awmc", "awmc-more.geojson", fixture("awmc-more.geojson"))
    run(sb, "chronontology", "chronontology.json", fixture("chronontology.json"))
    run(sb, "chronontology", "chronontology-more.json", fixture("chronontology-more.json"))
    run(
      sb,
      "cliopatria",
      "a zip with no usable file",
      GazetteerLoopback.zip(List("__MACOSX/" -> Array.emptyByteArray, "data/" -> Array.emptyByteArray))
    )
    run(sb, "awmc", "a body that is not JSON", "<html>moved</html>".getBytes(UTF_8))
    run(sb, "chronontology", "a JSON body of another shape", """{"error":"busy"}""".getBytes(UTF_8))
    // an error answer: the download says which, and nothing is read
    sb ++= "## chronontology: HTTP 503\n"
    loop.answerWith(503, "busy".getBytes(UTF_8))
    val failed = places("chronontology")
    sb ++= requests("chronontology")
    sb ++= s"places: ${failed.fold(e => s"failed: ${normalised(e, "chronontology")}", _.size.toString)}\n"
    sb.result()

  private val transcriptFile = Path.of("modules/server/src/test/resources/traffic/gazetteers.txt")

  test("the gazetteers send exactly the recorded requests and make exactly the recorded places"):
    val now = transcript
    if sys.env.get("HISTMERSION_UPDATE_TRAFFIC").contains("1") then
      Files.createDirectories(transcriptFile.getParent)
      Files.writeString(transcriptFile, now, UTF_8)
    val recorded = Files.readString(transcriptFile, UTF_8)
    if recorded != now then
      val (a, b) = (recorded.linesIterator.toVector, now.linesIterator.toVector)
      val at = a.indices.find(i => b.lift(i) != Some(a(i))).getOrElse(a.size)
      fail(
        s"the gazetteers differ from $transcriptFile at line ${at + 1}:\n  recorded: ${a.lift(at).getOrElse("<end>")}\n" +
          s"  now:      ${b.lift(at).getOrElse("<end>")}"
      )
