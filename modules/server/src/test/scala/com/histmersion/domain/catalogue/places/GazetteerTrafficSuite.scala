package com.histmersion
package domain.catalogue.places
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.server.ServerPlugins

import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.{HttpTransport, PluginGazetteers, Registries}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import com.histmersion.api.instance.Version
import com.histmersion.api.catalogue.Place
import com.histmersion.plugin.Plugin
import com.histmersion.config.AppConfig
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The recorded traffic and results of the network gazetteers (Cliopatria, AWMC, ChronOntology): each download answered
  * by a loopback server from a fixture (Cliopatria's zipped, as its upstream ships it) — the request it sent, every
  * place it made (the parents included, in order) and its dry run; then the failures (an error answer, a zip with no
  * usable file, a body that is not JSON). No database and no network. The transcript is `traffic/gazetteers.txt`,
  * compared byte for byte (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites it): moving the gazetteers into plugins changes no
  * request and no place.
  *
  * The headers that only the HTTP client chooses (`Host`, `Connection`, `Accept`, `Content-Length`, an HTTP/2
  * upgrade's) are left out of the transcript; `HISTMERSION_TRAFFIC_RAW=1` prints every request with all of them.
  */
class GazetteerTrafficSuite extends munit.FunSuite:

  private val loop = GazetteerLoopback()
  override def afterAll(): Unit = loop.stop()

  private def fixture(name: String): Array[Byte] =
    Files.readAllBytes(Path.of(s"modules/plugins/gazetteers/src/test/resources/gazetteers/$name"))

  private val Cliopatria = Registries.GazetteerSourcesKey("cliopatria")
  private val Awmc = Registries.GazetteerSourcesKey("awmc")
  private val Chronontology = Registries.GazetteerSourcesKey("chronontology")

  private val Transport = Set("host", "connection", "accept", "content-length", "upgrade", "http2-settings")

  private lazy val cfg = AppConfig.load()

  /** The stock generation's gazetteer sources (the first-party plugins, found on the class path as the server finds
    * them).
    */
  private lazy val sources = ServerPlugins.offline.registries.gazetteerSources

  /** The address a source downloads: what it asks a client that answers nothing. */
  private def sourceUrl(name: Registries.GazetteerSourcesKey): String =
    val asked = java.util.concurrent.ConcurrentLinkedQueue[String]()
    val probe = new HttpTransport:
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
      def get(url: HttpTransport.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) = {
        asked.add(url.value); Left(Problem.Unclassified(Legacy.Text("probe")))
      }
    val _ = ImportRunner.places(sources(name), PluginGazetteers.http(probe))
    asked.peek()

  // ── the code under test: the plugins' sources run by the host's runner ──────────────────────

  /** The host's gazetteer client, its requests sent to the loopback server instead of the source's host. */
  private def http: Plugin.Http =
    val wire = PluginGazetteers.transport(cfg.places)
    PluginGazetteers.http(new HttpTransport:
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
      def get(url: HttpTransport.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) =
        wire.get(HttpTransport.Url(loop.local(url.value)), headers))

  /** Every place a download of `name` makes, in the order the runner upserts them. */
  private def places(name: Registries.GazetteerSourcesKey): Either[Problem, List[Place.Input]] =
    ImportRunner.places(sources(name), http).map(_._1)

  private def dryRun(name: Registries.GazetteerSourcesKey): Either[Problem, Place.ImportResult] =
    ImportRunner.fetchAndImport(
      sources(name),
      http,
      null,
      null,
      dryRun = GazetteerControllers.DryRun(true),
      GeocodeSettings.ToleranceKm(0.0)
    )

  // ── the transcript ──────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def normalised(s: String, name: Registries.GazetteerSourcesKey): String =
    val real = java.net.URI.create(sourceUrl(name))
    s.replace(loop.origin, "<origin>")
      .replace(s"${real.getScheme}://${real.getRawAuthority}", "<origin>")
      .replace(Version.version.value, "<version>")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def requests(name: Registries.GazetteerSourcesKey): String =
    loop
      .take()
      .map { r =>
        if sys.env.get("HISTMERSION_TRAFFIC_RAW").contains("1") then
          println(s"[$name] ${r.method} ${r.target}\n" + r.headers.sorted.map((k, v) => s"    $k: $v").mkString("\n"))
        s"${r.method} ${r.target}\n" +
          r.headers
            .filterNot((k, _) => Transport(k.toLowerCase(java.util.Locale.ROOT)))
            .sorted
            .map((k, v) => s"  $k: ${normalised(v, name)}\n")
            .mkString
      }
      .mkString

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def run(sb: StringBuilder, name: Registries.GazetteerSourcesKey, served: String, body: Array[Byte]): Unit =
    sb ++= s"## $name: $served\nsource: ${sourceUrl(name)}\n"
    loop.answerWith(200, body)
    val made = places(name)
    sb ++= requests(name)
    made match
      case Left(e)   => sb ++= s"places: failed: ${normalised(e.sentence, name)}\n"
      case Right(ps) => sb ++= s"places: ${ps.size}\n" + ps.map(p => s"  $p\n").mkString
    val dry = dryRun(name)
    sb ++= requests(name)
    sb ++= s"dry run: ${dry.fold(e => s"failed: ${normalised(e.sentence, name)}", _.toString)}\n"

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
    run(sb, Cliopatria, "cliopatria.geojson, zipped", zipped("cliopatria.geojson"))
    run(sb, Cliopatria, "cliopatria-more.geojson, zipped", zipped("cliopatria-more.geojson"))
    run(sb, Awmc, "awmc.geojson", fixture("awmc.geojson"))
    run(sb, Awmc, "awmc-more.geojson", fixture("awmc-more.geojson"))
    run(sb, Chronontology, "chronontology.json", fixture("chronontology.json"))
    run(sb, Chronontology, "chronontology-more.json", fixture("chronontology-more.json"))
    run(
      sb,
      Cliopatria,
      "a zip with no usable file",
      GazetteerLoopback.zip(List("__MACOSX/" -> Array.emptyByteArray, "data/" -> Array.emptyByteArray))
    )
    run(sb, Awmc, "a body that is not JSON", "<html>moved</html>".getBytes(UTF_8))
    run(sb, Chronontology, "a JSON body of another shape", """{"error":"busy"}""".getBytes(UTF_8))
    // an error answer: the download says which, and nothing is read
    sb ++= "## chronontology: HTTP 503\n"
    loop.answerWith(503, "busy".getBytes(UTF_8))
    val failed = places(Chronontology)
    sb ++= requests(Chronontology)
    sb ++= s"places: ${failed.fold(e => s"failed: ${normalised(e.sentence, Chronontology)}", _.size.toString)}\n"
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
