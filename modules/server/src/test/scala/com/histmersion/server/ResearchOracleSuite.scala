package com.histmersion
package server
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson.*

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.augustnagro.magnum.*
import ox.supervised

import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import javax.sql.DataSource
import com.histmersion.db.*
import com.histmersion.config.DbConfig
import com.histmersion.config.BackupsConfig
import com.histmersion.config.BundlesConfig
import com.histmersion.config.ConverterConfig
import com.histmersion.config.AcquireConfig
import com.histmersion.config.InitConfig
import com.histmersion.config.RuntimeConfig
import com.histmersion.config.BasemapConfig
import com.histmersion.config.ServerConfig
import com.histmersion.util.data.exchange.BundleFormat
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{LintRule, tolerated}

/** What research answers, on a crafted collection, recorded on the code before the articles moved into their own
  * domain:
  *
  *   - `articles.txt`: articles put (each kind, a filter with extra queries, reasons, critique reasons) and refused (a
  *     bad slug, a bad kind, a reason for no citekey, a strict put over a formatting error, an unknown key), every read
  *     of one (in full, brief, each MCP part), the list under each filter (kind, text, a source tag, a title, a status,
  *     deep time, pages) over REST and MCP, patched field by field (title, summary, body, `append` with and without a
  *     section, tags, regions, period, kind, filter and its clearing, reasons, critique reasons) and the `ifUpdatedAt`
  *     refusals, the versions and one version, the coverage with its generated sections and its refusal, the review, a
  *     draft checked, the lint and its repair, the export, the slugs, a source's backlinks, an id's resolution, a note
  *     imported (stale, renamed, fresh), a rename with its rewritten links and an old slug that still answers, a
  *     delete; a cited source renamed and an uncited one deleted as research sees them; the article page (whole, a
  *     fragment, highlighted, unknown, the index redirect); every row research holds after each part;
  *   - `lint.txt`: a collection on which every article rule finds something (filler, an uncited paragraph, escaped
  *     links, a thin summary, the formatting, a dangling link, an unpaired critique), the report of each rule, the safe
  *     fixes and the pairing repair, and the rows after;
  *   - `exchange.txt`: a collection's whole bundle and the part since a shared history, imported into an empty
  *     collection and into one holding the history, and merged into one that went on too (an article edited in the same
  *     paragraph on both sides, one renamed on one side, one added on each, reasons changed): the bundles' research
  *     lines, every round of the plan, the apply, the ledger and research's rows of each collection.
  *
  * Uids are written as the record they name, timestamps as `TS`, durations as `N`, today's date as `DAY`.
  * `HISTMERSION_UPDATE_ORACLE=1` rewrites the files. Skipped without HISTMERSION_TEST_DB_URL.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ResearchOracleSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(10, "min")

  private val oracleDir = Path.of("modules/server/src/test/resources/oracle/research")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private val tmp = Files.createTempDirectory("histmersion-research-oracle")
  private lazy val base = AppConfig.load()
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val scratch = ScratchDatabases("histmersion_s11o", base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  // ── the server ─────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def cfgFor(name: String): AppConfig =
    base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(scratch.urlOf(s"histmersion_s11o_${name}_test")))
      .modify(_.backups)
      .setTo(base.backups.modify(_.dir).setTo(BackupsConfig.Dir(tmp.resolve(s"$name-backups").toString)))
      .modify(_.bundles)
      .setTo(base.bundles.modify(_.dir).setTo(BundlesConfig.Dir(tmp.resolve(s"$name-bundles").toString)))
      .modify(_.converter)
      .setTo(base.converter.modify(_.spoolDir).setTo(ConverterConfig.SpoolDir(tmp.resolve(s"$name-spool").toString)))
      .modify(_.acquire)
      .setTo(base.acquire.modify(_.spoolDir).setTo(AcquireConfig.SpoolDir(tmp.resolve(s"$name-acquire").toString)))
      .modify(_.init)
      .setTo(base.init.modify(_.auto).setTo(InitConfig.Auto(false)))
      .modify(_.runtime)
      .setTo(base.runtime.modify(_.dataDir).setTo(RuntimeConfig.DataDir(tmp.resolve(s"$name-data").toString)))
      .modify(_.basemap)
      .setTo(base.basemap.modify(_.dir).setTo(BasemapConfig.Dir(tmp.resolve(s"$name-basemap").toString)))

  private def withServer[A](name: String)(body: String => A): A =
    supervised {
      val b = Composition
        .server(cfgFor(name), ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), OfflineConverter())
        .start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
  private def enc(s: String) = URLEncoder.encode(s, UTF_8)

  private def raw(req: HttpRequest.Builder): HttpResponse[Array[Byte]] =
    http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray())

  private def get(url: String): String =
    val r = raw(HttpRequest.newBuilder(URI.create(url)))
    s"${r.statusCode} ${String(r.body, UTF_8)}"

  /** A redirect's status and where it points (the client does not follow it). */
  private def head(url: String): String =
    val r = raw(HttpRequest.newBuilder(URI.create(url)))
    s"${r.statusCode} ${r.headers().firstValue("location").orElse("-")}"

  private def send(method: String, url: String, body: String, contentType: String = "application/json"): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", contentType)
        .method(method, HttpRequest.BodyPublishers.ofString(body))
    )
    s"${r.statusCode} ${String(r.body, UTF_8)}"

  private def post(url: String, body: String = "", contentType: String = "application/json"): String =
    send("POST", url, body, contentType)

  private def ok(answer: String): String =
    assert(answer.startsWith("200 "), answer.take(800))
    answer.drop(4)

  private def field(json: String, name: String): String =
    s""""$name"\\s*:\\s*"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1)).getOrElse(fail(s"no $name in $json"))

  /** An MCP tool's answer, its Markdown text. */
  private def tool(url: String, name: String, args: Json): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(s"$url/mcp"))
        .header("content-type", "application/json")
        .header("accept", "application/json, text/event-stream")
        .POST(
          HttpRequest.BodyPublishers.ofString(
            Json
              .obj(
                "jsonrpc" -> 2.asJson,
                "id" -> 1.asJson,
                "method" -> "tools/call".asJson,
                "params" -> Json.obj("name" -> name.asJson, "arguments" -> args)
              )
              .noSpaces
          )
        )
    )
    val text = String(r.body, UTF_8)
    val payload = text.linesIterator.find(_.startsWith("data:")).map(_.stripPrefix("data:").trim).getOrElse(text)
    RawJson
      .parse(payload)
      .toOption
      .flatMap(_.cursor.downField("result").downField("content").downArray.downField("text").as[String].toOption)
      .getOrElse(s"${r.statusCode} $text")

  // ── the crafted collection ─────────────────────────────────────────────────────────────────

  private def edge(dst: String, kind: String): Json = Json.obj("dst" -> dst.asJson, "kind" -> kind.asJson)

  private def source(
      title: String,
      year: Int,
      authors: List[String],
      tags: List[String],
      topics: List[String],
      status: String = "current",
      edges: List[Json] = Nil,
      from: Long = -1700,
      to: Long = -1100
  ): String =
    Json
      .obj(
        "title" -> title.asJson,
        "authors" -> authors.asJson,
        "year" -> year.asJson,
        "venue" -> "Journal of Zr Studies".asJson,
        "status" -> status.asJson,
        "topics" -> topics.asJson,
        "regions" -> List("Zrland").asJson,
        "tags" -> tags.asJson,
        "periodStart" -> from.asJson,
        "periodEnd" -> to.asJson,
        "summary" -> s"A study of $title.".asJson,
        "body" -> s"# $title\n\nThe walls of Zrtown and its harbour, as $title tells it.\n".asJson,
        "edges" -> Json.fromValues(edges)
      )
      .noSpaces

  private def article(
      title: String,
      kind: String,
      body: String,
      summary: Option[String] = None,
      tags: List[String] = Nil,
      regions: List[String] = Nil,
      period: Option[(Long, Long)] = None,
      filter: Option[Json] = None,
      reasons: List[(String, String)] = Nil
  ): String =
    Json
      .fromFields(
        List("title" -> title.asJson, "kind" -> kind.asJson, "body" -> body.asJson) ++
          summary.map("summary" -> _.asJson) ++
          Option.when(tags.nonEmpty)("tags" -> tags.asJson) ++
          Option.when(regions.nonEmpty)("regions" -> regions.asJson) ++
          period.toList.flatMap((a, b) => List("periodStart" -> a.asJson, "periodEnd" -> b.asJson)) ++
          filter.map("filter" -> _) ++
          Option.when(reasons.nonEmpty)(
            "reasons" -> Json.fromValues(reasons.map((c, r) => Json.obj("citekey" -> c.asJson, "reason" -> r.asJson)))
          )
      )
      .noSpaces

  /** The sources the articles cite: two that disagree (a challenge and a refutation), one superseded, one only a filter
    * returns, one nobody cites.
    */
  private def craftSources(url: String, step: (String, String) => String): Unit =
    val _ = step(
      "put alpha",
      send(
        "PUT",
        s"$url/api/sources/zralpha1990_walls",
        source(
          "The Walls of Zrtown",
          1990,
          List("Zrauthor, Ann", "Zrwriter, Bo"),
          List("zr-walls", "zr-oracle"),
          List("bronze age", "fortification")
        )
      )
    )
    val _ = step(
      "put beta (challenges alpha)",
      send(
        "PUT",
        s"$url/api/sources/zrbeta2001_harbour",
        source(
          "The Harbour of Zrtown",
          2001,
          List("Zrwriter, Bo"),
          List("zr-trade", "zr-oracle"),
          List("bronze age", "trade"),
          status = "contested",
          edges = List(edge("zralpha1990_walls", "challenges"))
        )
      )
    )
    val _ = step(
      "put gamma (superseded by alpha, refutes beta)",
      send(
        "PUT",
        s"$url/api/sources/zrgamma1975_kings",
        source(
          "Kings of Zrland",
          1975,
          List("Zrauthor, Ann"),
          List("zr-kings", "zr-oracle"),
          List("kingship"),
          status = "superseded",
          edges = List(edge("zralpha1990_walls", "superseded_by"), edge("zrbeta2001_harbour", "refutes")),
          from = -2000,
          to = -1600
        )
      )
    )
    val _ = step(
      "put delta (a filter returns it, nobody cites it)",
      send(
        "PUT",
        s"$url/api/sources/zrdelta2010_survey",
        source("A Survey of Zrland", 2010, List("Zrother, Cy"), List("zr-oracle"), List("bronze age", "survey"))
      )
    )
    val _ = step(
      "put eps (to be deleted)",
      send(
        "PUT",
        s"$url/api/sources/zreps1999_lost",
        source("A Lost Report", 1999, List("Zrother, Cy"), List("zr-lost"), List("archive"))
      )
    )

  private val WallsBody =
    """## The question
      |
      |When were the walls of Zrtown raised? The walls rose around 1500 BC [[zralpha1990_walls]]; the harbour
      |trade paid for them [[zrbeta2001_harbour]].
      |
      |## What the sources say
      |
      |An older reading put the walls under the kings of Zrland [[zrgamma1975_kings]].
      |
      |## Unresolved
      |
      |Whether the gate is older than the walls [[zralpha1990_walls]].
      |""".stripMargin

  /** Writes, each recorded with its answer, then every read, list, patch and the rest. */
  private def articles(url: String, ds: DataSource): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    craftSources(url, step)
    val _ = step(
      "put walls (a concept with a filter, extra queries, a reason)",
      send(
        "PUT",
        s"$url/api/articles/zr_walls",
        article(
          "The walls of Zrtown",
          "concept",
          WallsBody,
          summary = Some("When the walls of Zrtown rose [[zralpha1990_walls]]."),
          tags = List("zr-art", "walls"),
          regions = List("Zrland"),
          period = Some((-1600L, -1400L)),
          filter = Some(
            Json.obj("tags" -> List("zr-oracle").asJson, "extraQueries" -> List("harbour", "kings of zrland").asJson)
          ),
          reasons = List("zrdelta2010_survey" -> "A survey of the whole region, no finding on the walls.")
        )
      )
    )
    val _ = step(
      "put harbour (a myth linking walls)",
      send(
        "PUT",
        s"$url/api/articles/zr_harbour",
        article(
          "The harbour myth",
          "myth",
          "## The story\n\nThe harbour was founded by a hero [[zrbeta2001_harbour]]. See [[zr_walls]] for the walls.\n",
          summary = Some("A founding myth [[zrbeta2001_harbour]]."),
          tags = List("zr-art", "myth"),
          period = Some((-1300L, -1200L))
        )
      )
    )
    val _ = step(
      "put index (links both)",
      send(
        "PUT",
        s"$url/api/articles/zr_index",
        article("Zr index", "index", "- [[zr_walls]]\n- [[zr_harbour]]\n- [[zr_missing]]\n", tags = List("zr-art"))
      )
    )
    val _ = step(
      "put kings (a timeline citing the superseded source)",
      send(
        "PUT",
        s"$url/api/articles/zr_kings",
        article(
          "Kings of Zrland, a timeline",
          "timeline",
          "## Timeline\n\nThe kings ruled from Zrtown from 2000 BC [[zrgamma1975_kings]].\n",
          summary = Some("The kings [[zrgamma1975_kings]]."),
          period = Some((-2000L, -1600L))
        )
      )
    )
    val _ = step(
      "put lost (cites the source to be deleted)",
      send(
        "PUT",
        s"$url/api/articles/zr_lost",
        article("A lost report", "report", "## Report\n\nThe report is lost [[zreps1999_lost]].\n")
      )
    )
    val refusals = List(
      ("a bad slug", "Bad%20Slug", article("x", "concept", "x")),
      ("a bad kind", "zr_bad", article("x", "Not A Kind!", "x")),
      ("an unknown key", "zr_bad", """{"title":"x","kind":"concept","body":"x","nope":1}"""),
      ("a reason for no citekey", "zr_bad", article("x", "concept", "x", reasons = List("Not a key!" -> "why"))),
      ("an overlong reason", "zr_bad", article("x", "concept", "x", reasons = List("zralpha1990_walls" -> ("w" * 501))))
    )
    refusals.foreach((n, slug, body) => step(s"put refused: $n", send("PUT", s"$url/api/articles/$slug", body)))
    val _ = step(
      "put refused: strict, a formatting error",
      send("PUT", s"$url/api/articles/zr_bad?strict=true", article("x", "concept", "<div> never closed\n"))
    )
    val _ = step(
      "tool put_article (a culture)",
      tool(
        url,
        "put_article",
        Json.obj(
          "slug" -> "zr_culture".asJson,
          "article" -> RawJson
            .parse(
              article(
                "The Zr culture",
                "culture",
                "## Overview\n\nThe Zr culture built walls [[zralpha1990_walls]].\n",
                tags = List("zr-art")
              )
            )
            .fold(e => fail(e.toString), identity)
        )
      )
    )
    val _ = step(
      "tool put_article refused (an unknown key)",
      tool(
        url,
        "put_article",
        Json.obj("slug" -> "zr_x".asJson, "article" -> Json.obj("title" -> "x".asJson, "nope" -> 1.asJson))
      )
    )
    // the reads of one article
    List(
      "zr_walls",
      "zr_walls?brief=true",
      "zr_harbour",
      "zr_index",
      "ZR_WALLS",
      "zr_nothing"
    ).foreach(s => step(s"GET /api/articles/$s", get(s"$url/api/articles/$s")))
    List(None, Some("body"), Some("summary"), Some("coverage"), Some("cited"), Some("backlinks"), Some("body,cited"))
      .foreach(p =>
        step(
          s"tool get_article ${p.getOrElse("(all)")}",
          tool(url, "get_article", Json.fromFields(List("slug" -> "zr_walls".asJson) ++ p.map("parts" -> _.asJson)))
        )
      )
    val _ = step("tool get_article unknown", tool(url, "get_article", Json.obj("slug" -> "zr_nothing".asJson)))
    // the list under each filter
    List(
      "",
      "?kind=myth",
      "?kind=concept",
      "?text=walls",
      "?text=hero",
      "?tag=zr-trade",
      "?tag=zr-kings",
      "?title=harbour",
      "?status=superseded",
      "?q=walls",
      "?from=-2000&to=-1700",
      "?topic=trade",
      "?limit=2",
      "?limit=2&offset=2"
    ).foreach(q => step(s"GET /api/articles$q", get(s"$url/api/articles$q")))
    List(
      Json.obj(),
      Json.obj("kind" -> "myth".asJson),
      Json.obj("query" -> "walls".asJson),
      Json.obj("tag" -> "zr-trade".asJson),
      Json.obj("title" -> "harbour".asJson),
      Json.obj("limit" -> 2.asJson, "offset" -> 1.asJson)
    ).foreach(a => step(s"tool list_articles ${a.noSpaces}", tool(url, "list_articles", a)))
    // patched field by field
    def at(slug: String): String = field(ok(get(s"$url/api/articles/$slug?brief=true")), "updatedAt")
    def patch(name: String, slug: String, body: String, strict: Boolean = false): String =
      step(
        s"patch $slug: $name",
        send("PATCH", s"$url/api/articles/$slug${if strict then "?strict=true" else ""}", body)
      )
    val _ = patch("title", "zr_harbour", """{"title":"The harbour myth of Zrtown"}""")
    val _ = patch("summary", "zr_harbour", """{"summary":"The founding myth of the harbour [[zrbeta2001_harbour]]."}""")
    val _ = patch(
      "body",
      "zr_harbour",
      Json
        .obj(
          "body" -> "## The story\n\nA hero founded the harbour [[zrbeta2001_harbour]]. See [[zr_walls]].\n".asJson
        )
        .noSpaces
    )
    val _ = patch(
      "append",
      "zr_harbour",
      Json.obj("append" -> "## Unresolved\n\nWho the hero was [[zrbeta2001_harbour]].\n".asJson).noSpaces
    )
    val _ = patch(
      "append to a section",
      "zr_harbour",
      Json
        .obj(
          "append" -> "A second version names no hero [[zrbeta2001_harbour]].".asJson,
          "section" -> "The story".asJson
        )
        .noSpaces
    )
    val _ = patch(
      "append to a missing section",
      "zr_harbour",
      Json.obj("append" -> "x [[zrbeta2001_harbour]].".asJson, "section" -> "No such".asJson).noSpaces
    )
    val _ = patch("tags add", "zr_harbour", """{"tags":{"add":["founding"]}}""")
    val _ = patch("tags remove", "zr_harbour", """{"tags":{"remove":["myth"]}}""")
    val _ = patch("tags replace", "zr_harbour", """{"tags":{"replace":["zr-art","founding","harbour"]}}""")
    val _ = patch("regions", "zr_harbour", """{"regions":["Zrland","Zrcoast"]}""")
    val _ = patch("period", "zr_harbour", """{"period":{"start":-1350,"end":-1150}}""")
    val _ = patch("kind", "zr_harbour", """{"kind":"concept"}""")
    val _ = patch("kind back", "zr_harbour", """{"kind":"myths"}""")
    val _ = patch("filter", "zr_harbour", """{"filter":{"tags":["zr-trade"],"from":-1500,"to":-1000}}""")
    val _ = patch(
      "reasons",
      "zr_walls",
      """{"reasons":[{"citekey":"[[ZRDELTA2010_SURVEY]]","reason":"Region-wide; no walls."}]}"""
    )
    val _ = patch(
      "critique reasons",
      "zr_walls",
      """{"critiqueReasons":[{"src":"zrgamma1975_kings","dst":"zrbeta2001_harbour","reason":"The refutation is about trade, not the walls."}]}"""
    )
    val _ =
      patch("refused: a stale ifUpdatedAt", "zr_harbour", """{"title":"x","ifUpdatedAt":"2000-01-01T00:00:00Z"}""")
    val _ = patch("refused: a malformed ifUpdatedAt", "zr_harbour", """{"title":"x","ifUpdatedAt":"yesterday"}""")
    val _ = patch(
      "with the current ifUpdatedAt",
      "zr_harbour",
      s"""{"title":"The harbour myth","ifUpdatedAt":"${at("zr_harbour")}"}"""
    )
    val _ =
      patch("refused: strict, a formatting error", "zr_harbour", """{"append":"<div> never closed"}""", strict = true)
    val _ = patch("refused: an unknown key", "zr_harbour", """{"nope":1}""")
    val _ = patch("refused: a bad kind", "zr_harbour", """{"kind":"Not A Kind!"}""")
    val _ = patch("unknown article", "zr_nothing", """{"title":"x"}""")
    val _ = patch("filter cleared", "zr_harbour", """{"filter":{}}""")
    val _ = step(
      "tool patch_article (append)",
      tool(
        url,
        "patch_article",
        Json.obj(
          "slug" -> "zr_kings".asJson,
          "patch" -> Json.obj("append" -> "## Unresolved\n\nThe end of the dynasty [[zrgamma1975_kings]].\n".asJson)
        )
      )
    )
    val _ = step(
      "tool patch_article refused (stale)",
      tool(
        url,
        "patch_article",
        Json.obj(
          "slug" -> "zr_kings".asJson,
          "patch" -> Json.obj("title" -> "x".asJson, "ifUpdatedAt" -> "2000-01-01T00:00:00Z".asJson)
        )
      )
    )
    // versions
    val versions = step("GET versions of harbour", get(s"$url/api/articles/zr_harbour/versions"))
    val firstVersion =
      "\"id\"\\s*:\\s*(\\d+)".r.findAllMatchIn(versions).map(_.group(1)).toList.lastOption.getOrElse("0")
    val _ = step("GET the first version", get(s"$url/api/articles/zr_harbour/versions/$firstVersion"))
    val _ = step("GET an unknown version", get(s"$url/api/articles/zr_harbour/versions/999999"))
    val _ = step("GET versions of an unknown article", get(s"$url/api/articles/zr_nothing/versions"))
    val _ = step("tool article_versions", tool(url, "article_versions", Json.obj("slug" -> "zr_harbour".asJson)))
    val _ = step(
      "tool article_version",
      tool(url, "article_version", Json.obj("slug" -> "zr_harbour".asJson, "versionId" -> firstVersion.toLong.asJson))
    )
    // coverage, review, a draft checked, lint, export
    List("zr_walls", "zr_harbour", "zr_kings", "zr_nothing").foreach(s =>
      step(s"GET coverage of $s", get(s"$url/api/articles/$s/coverage"))
    )
    val _ = step("tool article_coverage", tool(url, "article_coverage", Json.obj("slug" -> "zr_walls".asJson)))
    val _ =
      step("tool article_coverage (no filter)", tool(url, "article_coverage", Json.obj("slug" -> "zr_kings".asJson)))
    List("zr_walls", "zr_nothing").foreach(s => step(s"GET review of $s", get(s"$url/api/articles/$s/review")))
    val _ = step("tool article_review", tool(url, "article_review", Json.obj("slug" -> "zr_walls".asJson)))
    val checks = List(
      "edits" -> """{"edits":[{"op":"replace","anchor":"around 1500 BC","text":"around 1490 BC"}]}""",
      "an anchor not found" -> """{"edits":[{"op":"delete","anchor":"no such passage"}]}""",
      "a whole draft" -> Json
        .obj("body" -> "## Overview\n\nThe walls [[zralpha1990_walls]]. Filler: it is important to note.\n".asJson)
        .noSpaces,
      "a draft as an index" -> """{"body":"- [[zr_walls]]","kind":"index"}""",
      "refused: both" -> """{"edits":[{"op":"delete","anchor":"x"}],"body":"y"}""",
      "refused: neither" -> "{}"
    )
    checks.foreach((n, b) => step(s"check walls: $n", post(s"$url/api/articles/zr_walls/check", b)))
    val _ = step(
      "check a new article's draft",
      post(s"$url/api/articles/zr_new/check", """{"body":"## Overview\n\nNew [[zralpha1990_walls]]."}""")
    )
    val _ = step(
      "check an unknown article's edits",
      post(s"$url/api/articles/zr_new/check", """{"edits":[{"op":"delete","anchor":"x"}]}""")
    )
    val _ = step(
      "tool check_edits",
      tool(
        url,
        "check_edits",
        Json.obj(
          "slug" -> "zr_walls".asJson,
          "edits" -> Json.arr(Json.obj("op" -> "delete".asJson, "anchor" -> "## Unresolved".asJson))
        )
      )
    )
    val _ = step(
      "put messy (formatting to repair)",
      send(
        "PUT",
        s"$url/api/articles/zr_messy",
        article(
          "Messy",
          "concept",
          "## Overview\n\nA list:\n* one [[zralpha1990_walls]]\n* two\n\n\n\nEnd \\[\\[zralpha1990_walls\\]\\].   \n"
        )
      )
    )
    val _ = step("lint messy", get(s"$url/api/articles/zr_messy/lint"))
    val _ = step("lint/fix messy", post(s"$url/api/articles/zr_messy/lint/fix"))
    val _ = step("lint messy after", get(s"$url/api/articles/zr_messy/lint"))
    val _ = step("lint unknown", get(s"$url/api/articles/zr_nothing/lint"))
    List("zr_walls", "zr_index", "zr_nothing").foreach(s => step(s"export $s", get(s"$url/api/articles/$s/export")))
    val _ = step("tool export_article", tool(url, "export_article", Json.obj("slug" -> "zr_walls".asJson)))
    val _ = step("article slugs", get(s"$url/api/article-slugs"))
    List("zralpha1990_walls", "zrdelta2010_survey", "zrnone2000_x").foreach(ck =>
      step(s"backlinks of $ck", get(s"$url/api/sources/$ck/articles"))
    )
    List("zr_walls", "zralpha1990_walls", "zr_nothing").foreach(id =>
      step(s"resolve $id", get(s"$url/api/resolve/$id"))
    )
    val note = ok(get(s"$url/api/articles/zr_kings/export"))
    val noteText = RawJson.parse(note).toOption.flatMap(_.asString).getOrElse(note)
    val _ = step(
      "import a note: stale (an mtime older than the row)",
      post(s"$url/api/articles/import", Json.obj("text" -> noteText.asJson, "mtime" -> 1000L.asJson).noSpaces)
    )
    val _ = step(
      "import a note: forced",
      post(
        s"$url/api/articles/import",
        Json
          .obj(
            "text" -> noteText.replace("The kings ruled", "The kings reigned").asJson,
            "mtime" -> 1000L.asJson,
            "force" -> true.asJson
          )
          .noSpaces
      )
    )
    val _ = step(
      "import a note: new, no frontmatter",
      post(s"$url/api/articles/import", Json.obj("text" -> "just text".asJson).noSpaces)
    )
    val _ = step(
      "import a note: a new article",
      post(
        s"$url/api/articles/import",
        Json
          .obj(
            "text" -> "---\ntitle: Imported\nkind: concept\n---\n\n## Overview\n\nImported [[zralpha1990_walls]].\n".asJson,
            "slug" -> "zr_imported".asJson
          )
          .noSpaces
      )
    )
    log ++= s"== rows before the renames\n${rows(ds, names(ds))}"
    // renames: an article, a cited source; a source deleted
    val _ = step("rename walls", post(s"$url/api/articles/zr_walls/rename", """{"to":"zr_city_walls"}"""))
    val _ = step("rename refused: taken", post(s"$url/api/articles/zr_city_walls/rename", """{"to":"zr_harbour"}"""))
    val _ = step("rename refused: invalid", post(s"$url/api/articles/zr_city_walls/rename", """{"to":"Bad Slug"}"""))
    val _ = step("rename refused: unknown", post(s"$url/api/articles/zr_nothing/rename", """{"to":"zr_other"}"""))
    val _ = step("GET by the old slug", get(s"$url/api/articles/zr_walls?brief=true"))
    val _ = patch("by the old slug", "zr_walls", """{"tags":{"add":["renamed"]}}""")
    val _ = step("GET the index after the rename", get(s"$url/api/articles/zr_index?brief=true"))
    val _ = step(
      "rename alpha (cited)",
      post(s"$url/api/sources/zralpha1990_walls/rename", """{"newCitekey":"zralpha1991_walls"}""")
    )
    val _ = step("GET walls after the source's rename", get(s"$url/api/articles/zr_city_walls?brief=true"))
    val _ = step("delete eps refused (cited)", post(s"$url/api/sources/zreps1999_lost/delete", """{"apply":true}"""))
    val _ = step("delete the citing article", send("DELETE", s"$url/api/articles/zr_lost", ""))
    val _ = step("delete it again", send("DELETE", s"$url/api/articles/zr_lost", ""))
    val _ = step("delete eps", post(s"$url/api/sources/zreps1999_lost/delete", """{"apply":true}"""))
    log ++= s"== rows after the renames and deletes\n${rows(ds, names(ds))}"
    // the page
    List(
      "article/zr_city_walls",
      "article/zr_city_walls?fragment=true",
      "article/zr_city_walls?fragment=true&q=walls",
      "article/zr_walls?fragment=true",
      "article/zr_index?fragment=true",
      "article/zr_nothing",
      "article/zr_nothing?fragment=true"
    ).foreach(p => step(s"page $p", get(s"$url/$p")))
    val _ = step("page articles (the redirect)", head(s"$url/articles"))
    log.result()

  // ── the article lint rules ────────────────────────────────────────────────────────────────

  private val ArticleRules =
    List(
      "dangling-article-links",
      "article-filler",
      "article-uncited-paragraph",
      "article-thin-summary",
      "article-critique-pairing",
      "article-markdown"
    )

  private def craftChecks(url: String): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    craftSources(url, step)
    val _ = step(
      "put filler",
      send(
        "PUT",
        s"$url/api/articles/zr_filler",
        article(
          "Filler",
          "concept",
          "## Overview\n\nIt is important to note that the walls rose [[zralpha1990_walls]]. Needless to say, they fell.\n\nA paragraph with no citation at all.\n",
          summary = Some("Short.")
        )
      )
    )
    val _ = step(
      "put escaped (escaped links, a dangling one)",
      send(
        "PUT",
        s"$url/api/articles/zr_escaped",
        article(
          "Escaped",
          "concept",
          "## Overview\n\nThe walls \\[\\[zralpha1990_walls\\]\\] and [[zr_nowhere]] and [[zralpha1990_walls]].\n",
          summary = Some(
            "A summary long enough to pass the thin-summary rule of the article lint, about the walls [[zralpha1990_walls]]."
          )
        )
      )
    )
    val _ = step(
      "put unpaired (cites alpha without its critic beta)",
      send(
        "PUT",
        s"$url/api/articles/zr_unpaired",
        article(
          "Unpaired",
          "concept",
          "## Overview\n\nThe walls rose around 1500 BC [[zralpha1990_walls]].\n\n## Elsewhere\n\nThe harbour traded tin [[zrbeta2001_harbour]], which Zrauthor (1975) disputes.\n",
          summary = Some(
            "A summary long enough to pass the thin-summary rule of the article lint, about the walls [[zralpha1990_walls]]."
          )
        )
      )
    )
    val _ = step(
      "put format (markdown problems)",
      send(
        "PUT",
        s"$url/api/articles/zr_format",
        article(
          "Format",
          "concept",
          "## Overview\n\n* one [[zralpha1990_walls]]\n* two [[zralpha1990_walls]]\n\n\n\nTrailing   \n",
          summary = Some(
            "A summary long enough to pass the thin-summary rule of the article lint, about the walls [[zralpha1990_walls]]."
          )
        )
      )
    )
    log.result()

  // ── the exchange ───────────────────────────────────────────────────────────────────────────

  private def craftExchange(url: String): String =
    val log = StringBuilder()
    def step(name: String, answer: String): String =
      log ++= s"== $name\n$answer\n"
      answer
    craftSources(url, step)
    val _ = step(
      "put walls",
      send(
        "PUT",
        s"$url/api/articles/zr_walls",
        article(
          "The walls of Zrtown",
          "concept",
          WallsBody,
          summary = Some("When the walls rose [[zralpha1990_walls]]."),
          tags = List("zr-art"),
          filter = Some(Json.obj("tags" -> List("zr-oracle").asJson)),
          reasons = List("zrdelta2010_survey" -> "Region-wide.")
        )
      )
    )
    val _ = step(
      "put harbour",
      send(
        "PUT",
        s"$url/api/articles/zr_harbour",
        article("Harbour", "myth", "## The story\n\nA hero [[zrbeta2001_harbour]]. See [[zr_walls]].\n")
      )
    )
    val _ = step(
      "put kings",
      send(
        "PUT",
        s"$url/api/articles/zr_kings",
        article("Kings", "timeline", "## Timeline\n\nThe kings [[zrgamma1975_kings]].\n")
      )
    )
    log.result()

  private def editExchange(lib: String, home: String): String =
    val log = StringBuilder()
    def step(name: String, answer: String): Unit = log ++= s"== $name\n$answer\n"
    step(
      "lib: walls, the first paragraph",
      send(
        "PATCH",
        s"$lib/api/articles/zr_walls",
        Json.obj("body" -> WallsBody.replace("around 1500 BC", "around 1490 BC, as lib reads it,").asJson).noSpaces
      )
    )
    step(
      "home: walls, the same paragraph",
      send(
        "PATCH",
        s"$home/api/articles/zr_walls",
        Json.obj("body" -> WallsBody.replace("around 1500 BC", "around 1510 BC, as home reads it,").asJson).noSpaces
      )
    )
    step(
      "home: walls, another reason",
      send(
        "PATCH",
        s"$home/api/articles/zr_walls",
        """{"reasons":[{"citekey":"zrdelta2010_survey","reason":"Home's reason."}]}"""
      )
    )
    step(
      "lib: harbour, another paragraph only",
      send(
        "PATCH",
        s"$lib/api/articles/zr_harbour",
        Json.obj("append" -> "## Unresolved\n\nWho [[zrbeta2001_harbour]].\n".asJson).noSpaces
      )
    )
    step("lib: rename kings", post(s"$lib/api/articles/zr_kings/rename", """{"to":"zr_dynasty"}"""))
    step("home: patch kings", send("PATCH", s"$home/api/articles/zr_kings", """{"tags":{"add":["zr-home"]}}"""))
    step(
      "lib: a new article",
      send(
        "PUT",
        s"$lib/api/articles/zr_lib_only",
        article("Lib's", "concept", "## Overview\n\nLib [[zralpha1990_walls]].\n")
      )
    )
    step(
      "home: a new article",
      send(
        "PUT",
        s"$home/api/articles/zr_home_only",
        article("Home's", "concept", "## Overview\n\nHome [[zralpha1990_walls]].\n")
      )
    )
    log.result()

  /** Both sides after the shared history, for the links' merge: lib renames a cited source and adds a source and an
    * article citing it; home adds another work under the same citekey and an article citing it and the old name.
    */
  private def editLinks(lib: String, home: String): String =
    val log = StringBuilder()
    def step(name: String, answer: String): Unit = log ++= s"== $name\n$answer\n"
    step(
      "lib: rename delta (cited by walls' reasons)",
      post(s"$lib/api/sources/zrdelta2010_survey/rename", """{"newCitekey":"zrdelta2011_survey"}""")
    )
    step(
      "lib: put zeta",
      send(
        "PUT",
        s"$lib/api/sources/zrzeta2020_tin",
        source("Tin of Zrland", 2020, List("Zrlib, Lu"), List("zr-tin"), List("trade"))
      )
    )
    step(
      "lib: an article citing zeta and the renamed delta",
      send(
        "PUT",
        s"$lib/api/articles/zr_tin",
        article(
          "Tin",
          "concept",
          "## Overview\n\nTin came by sea [[zrzeta2020_tin]], as the survey shows [[zrdelta2011_survey]].\n\n" +
            "## Elsewhere\n\nSee [[zr_walls]] and [[zr_kings]].\n"
        )
      )
    )
    step(
      "home: put another zeta (another work, the same citekey)",
      send(
        "PUT",
        s"$home/api/sources/zrzeta2020_tin",
        source("Copper of Zrland", 2020, List("Zrhome, Mo"), List("zr-copper"), List("mining"))
      )
    )
    step(
      "home: an article citing its zeta and delta's old name",
      send(
        "PUT",
        s"$home/api/articles/zr_copper",
        article(
          "Copper",
          "concept",
          "## Overview\n\nCopper was mined inland [[zrzeta2020_tin]] [[zrdelta2010_survey]].\n"
        )
      )
    )
    log.result()

  private def bundleText(bundle: Array[Byte], names: Map[String, String]): String =
    val in = java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(bundle))
    val out = StringBuilder()
    @scala.annotation.tailrec
    def loop(): Unit =
      val header = in.readNBytes(512)
      if header.length == 512 && header.exists(_ != 0) then
        val name = String(header.take(100), UTF_8).takeWhile(_ != 0)
        val prefix = String(header.slice(345, 500), UTF_8).takeWhile(_ != 0)
        val size = java.lang.Long.parseLong(String(header.slice(124, 136), UTF_8).trim.takeWhile(_ != 0), 8)
        val data = in.readNBytes(size.toInt)
        val _ = in.skip((512 - size % 512) % 512)
        val path = if prefix.isEmpty then name else s"$prefix/$name"
        if path.endsWith(".ndjson") && ResearchEntries.exists(path.contains) then
          val lines = String(data, UTF_8).linesIterator.toList
          out ++= s"== $path (${lines.size} lines)\n"
          lines.foreach(l => out ++= xjson(l, names).linesIterator.mkString(" ") + "\n")
        else if path.endsWith(".ndjson") then out ++= s"== $path\n"
        loop()
    loop()
    out.toString

  private val ResearchEntries = List("article", "review")

  private def postBytes(url: String, body: Array[Byte]): String =
    val r = raw(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
    )
    s"${r.statusCode} ${String(r.body, UTF_8)}"

  private def bytes(url: String): Array[Byte] =
    val r = raw(HttpRequest.newBuilder(URI.create(url)))
    assertEquals(r.statusCode, 200, String(r.body, UTF_8).take(500))
    r.body

  private def awaitApply(url: String, job: String, left: Int = 600): String =
    val j = get(s"$url/api/merge/apply/$job")
    if j.contains("\"done\"") || j.contains("\"failed\"") || left == 0 then j
    else { Thread.sleep(100); awaitApply(url, job, left - 1) }

  /** A merge of `bundle` into the collection at `url`: the first plan, every open issue answered keep-right (else its
    * suggestion), the plan again, the apply, the merges, the merge and the ledger.
    */
  private def mergeInto(url: String, bundle: Array[Byte]): List[(String, String)] =
    val up = ok(postBytes(s"$url/api/bundle/upload", bundle))
    val sha = field(up, "sha256")
    val p0 = ok(post(s"$url/api/merge/plan", s"""{"bundle":"$sha"}"""))
    val open = RawJson
      .parse(p0)
      .toOption
      .flatMap(_.cursor.downField("issues").focus)
      .flatMap(_.asArray)
      .getOrElse(Vector.empty)
      .flatMap { i =>
        val options = i.cursor.downField("options").as[List[String]].getOrElse(Nil)
        val suggested = i.cursor.downField("suggested").as[String].toOption
        val choice =
          if options.isEmpty || options.contains("keep-right") then "keep-right" else suggested.getOrElse(options.head)
        i.cursor
          .downField("id")
          .as[String]
          .toOption
          .filter(_ => i.cursor.downField("decision").focus.forall(_.isNull))
          .map(_ -> choice)
      }
      .sorted
    val resolutions =
      s"""{"resolve":[${open.map((id, d) => s"""{"issue":"$id","decision":"$d"}""").mkString(",")}]}"""
    val p1 = ok(post(s"$url/api/merge/plan", s"""{"bundle":"$sha","resolutions":$resolutions}"""))
    val hash = field(p1, "planHash")
    val job = ok(post(s"$url/api/merge/apply", s"""{"bundle":"$sha","planHash":"$hash","resolutions":$resolutions}"""))
    val done = awaitApply(url, field(job, "job"))
    val ml = get(s"$url/api/merges")
    val id = field(ml, "id")
    List(
      "the first plan" -> p0,
      "the plan, every issue answered" -> p1,
      "applied" -> done,
      "merges" -> ml,
      "the merge" -> get(s"$url/api/merges/$id"),
      "the ledger" -> get(s"$url/api/merges/ledger?limit=1000")
    )

  // ── normalisation ──────────────────────────────────────────────────────────────────────────

  private val UuidRe =
    "\\b[A-Z]-[0-9a-f]{8}\\b|[0-9a-f]{64}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r
  private val TimeRe =
    "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}(?::?\\d{2})?)?".r
  private val DayRe = "\\b\\d{4}-\\d{2}-\\d{2}\\b".r

  private val BundleRe = "app\\.bundle\\.js\\?v=[0-9a-f]*".r
  private val DurationRe = "(\"(?:durationMs|elapsedMs|tookMillis|ageSeconds)\" ?: ?)\\d+".r
  private val MsRe = "· \\d+ ms".r

  /** uid → the record it names. */
  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  private def names(ds: DataSource): Map[String, String] =
    val queries = List(
      "SELECT uid::text, 'source:' || citekey FROM sources",
      "SELECT uid::text, 'article:' || slug FROM articles",
      "SELECT uid::text, 'tombstone:' || kind FROM tombstones",
      "SELECT id::text, 'instance' FROM instance"
    )
    def run(q: String): List[(String, String)] =
      scala.util.Try(connect(ds)(sql"${SqlLiteral(q)}".query[(String, String)].run().toList)).getOrElse(Nil)
    queries.flatMap(run).distinctBy(_._1).toMap

  /** uid → the record it names, over every database the exchange test made. */
  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  private def xnames(dss: List[(String, DataSource)]): Map[String, String] =
    val queries = List(
      "SELECT uid::text, 'source:' || citekey FROM sources",
      "SELECT origin::text, 'origin-of:' || citekey FROM sources WHERE origin IS NOT NULL",
      "SELECT uid::text, 'article:' || slug FROM articles",
      "SELECT uid::text, 'tombstone:' || kind FROM tombstones",
      "SELECT alias_uid::text, 'alias-of:' || uid::text FROM uid_aliases"
    )
    def run(ds: DataSource, q: String): List[(String, String)] =
      scala.util.Try(connect(ds)(sql"${SqlLiteral(q)}".query[(String, String)].run().toList)).getOrElse(Nil)
    val records = dss.flatMap((_, ds) => queries.flatMap(run(ds, _)))
    val instances =
      dss.flatMap((n, ds) => run(ds, "SELECT id::text, 'x' FROM instance").map((id, _) => id -> s"instance:$n"))
    val merges = dss.flatMap((n, ds) =>
      run(ds, "SELECT id::text, 'x' FROM merges ORDER BY created_at").zipWithIndex.map { case ((id, _), i) =>
        id -> s"merge:$n#${i + 1}"
      }
    )
    (instances ++ records ++ merges).distinctBy(_._1).toMap

  private def normalise(text: String, names: Map[String, String]): String =
    val t1 = BundleRe.replaceAllIn(text.replace(tmp.toString, "TMP"), "app.bundle.js?v=BUILD")
    val t2 = UuidRe.replaceAllIn(
      t1,
      m =>
        scala.util.matching.Regex
          .quoteReplacement(names.getOrElse(m.matched, if m.matched.length != 36 then m.matched else "UUID"))
    )
    val t3 = DurationRe.replaceAllIn(TimeRe.replaceAllIn(t2, "TS"), "$1N")
    MsRe.replaceAllIn(DayRe.replaceAllIn(t3, "DAY"), "· N ms")

  private def json(text: String, names: Map[String, String]): String =
    RawJson.parse(text) match
      case Right(j) => normalise(j.sortedKeys.spaces2, names)
      case Left(_)  => normalise(text, names)

  /** A recorded answer: its status, then its body as sorted JSON when it is JSON. */
  private def answers(text: String, names: Map[String, String]): String =
    text.linesIterator
      .map { l =>
        val m = "^(\\d{3}) (.*)$".r.findFirstMatchIn(l)
        m.fold(normalise(l, names))(m => s"${m.group(1)} ${json(m.group(2), names)}")
      }
      .mkString("", "\n", "\n")

  private val PlanRe = "plan [0-9a-f]{8}\\b".r
  private val ShortRe = "(?<![-\\w])[0-9a-f]{8}(?![-\\w])".r
  private val StampRe = "\\d{8}T\\d{6}Z(?:-\\d+)?".r
  private val Measured = Set("bytes", "files", "durationMs", "elapsedMs", "tookMillis", "ageSeconds", "totalBytes")
  private def digest(key: String): Boolean = key.toLowerCase.matches(".*(rev|hash|sha256)(_?(after|before))?")
  private val IssueArrays =
    Set("issues", "resolve", "resolutions", "resolutionErrors", "problems", "decisions", "remembered")

  private def stamps(t: String): String =
    ShortRe.replaceAllIn(PlanRe.replaceAllIn(StampRe.replaceAllIn(t, "STAMP"), "plan PLAN"), "SHORT")

  private def xjson(text: String, names: Map[String, String]): String =
    def walk(j: Json): Json =
      j.arrayOrObject(
        j,
        a => Json.fromValues(a.map(walk)),
        o =>
          Json.fromFields(o.toList.map { (k, v) =>
            if Measured(k) && v.isNumber then k -> Json.fromString("N")
            else if digest(k) && v.isString then k -> Json.fromString("DIGEST")
            else k -> walk(v)
          })
      )
    def sortIssues(j: Json): Json =
      j.arrayOrObject(
        j,
        a => Json.fromValues(a.map(sortIssues)),
        o =>
          Json.fromFields(o.toList.map { (k, v) =>
            val inner = sortIssues(v)
            if IssueArrays(k) then k -> inner.mapArray(_.sortBy(_.sortedKeys.spaces2)) else k -> inner
          })
      )
    val t = RawJson.parse(text) match
      case Right(j) =>
        val n = normalise(walk(j).sortedKeys.spaces2, names)
        RawJson.parse(n).fold(_ => n, sortIssues(_).sortedKeys.spaces2)
      case Left(_) => normalise(text, names)
    stamps(t)

  /** An issue id → `<letter>-issue:<kind>:<label>` (a second issue of the same kind and label numbered by its text). */
  private def issueNames(plans: List[String]): Map[String, String] =
    val issues = plans
      .flatMap(p => RawJson.parse(p.dropWhile(_ != '{')).toOption)
      .flatMap(_.cursor.downField("issues").focus.flatMap(_.asArray).getOrElse(Vector.empty))
      .flatMap { i =>
        val c = i.cursor
        for
          id <- c.downField("id").as[String].toOption
          kind = c.downField("kind").as[String].getOrElse("?")
          label = c
            .downField("right")
            .downField("label")
            .as[String]
            .toOption
            .orElse(c.downField("left").downField("label").as[String].toOption)
            .getOrElse("?")
        yield (id, s"${id.take(1)}-issue:$kind:$label", i.cursor.downField("title").as[String].getOrElse(""))
      }
      .distinctBy(_._1)
    issues
      .groupBy(_._2)
      .toList
      .flatMap((name, same) =>
        if same.size == 1 then same.map(i => i._1 -> name)
        else same.sortBy(_._3).zipWithIndex.map((i, n) => i._1 -> s"$name#${n + 1}")
      )
      .toMap

  /** Research's tables and the rows of other owners that name an article, every row, normalised. */
  private val Tables = List(
    "articles" -> "slug, title, kind, summary, body, tags, regions, period::text, filter, coverage_reasons, uid",
    "article_versions" -> "slug, title, kind, summary, body",
    "article_links" -> "*",
    "article_aliases" -> "*",
    "article_critique_reasons" -> "slug, src, dst, reason, set_by",
    "review_ledgers" -> "*",
    "alias_tombstones" -> "*",
    "tombstones" -> "kind, uid",
    "uid_aliases" -> "*",
    "sources" -> "citekey",
    "source_aliases" -> "alias, citekey"
  )

  private def columnsOf(ds: DataSource, table: String, wanted: String): String =
    if wanted == "*" then "*"
    else
      val have = connect(ds)(
        sql"SELECT column_name FROM information_schema.columns WHERE table_name = $table".query[String].run().toSet
      )
      wanted
        .split(",")
        .map(_.trim)
        .filter(c => have(c.takeWhile(ch => ch.isLetterOrDigit || ch == '_')))
        .mkString(", ")

  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  private def rows(
      ds: DataSource,
      names: Map[String, String],
      render: (String, Map[String, String]) => String = json
  ): String =
    Tables
      .map { (t, cols) =>
        val cs = columnsOf(ds, t, cols)
        val rs =
          if cs.isEmpty then Nil
          else
            connect(ds)(
              sql"""SELECT to_jsonb(x)::text FROM (SELECT ${SqlLiteral(cs)} FROM "${SqlLiteral(t)}") x"""
                .query[String]
                .run()
                .toList
            )
        val normed = rs.map(r => render(r, names).linesIterator.map(_.trim).mkString(" ")).sorted
        (s"## $t ${normed.size} rows" :: normed).mkString("\n")
      }
      .mkString("", "\n", "\n")

  private def compare(name: String, actual: String): Unit =
    val file = oracleDir.resolve(name)
    if update then
      Files.createDirectories(oracleDir)
      val _ = Files.writeString(file, actual, UTF_8)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_ORACLE=1")
      assertNoDiff(actual, Files.readString(file, UTF_8))

  // ── the tests ──────────────────────────────────────────────────────────────────────────────

  test("articles over REST and MCP, their versions, coverage, review, lint, export and page, as recorded"):
    val ds = scratch("art")
    val text = withServer("art")(url => articles(url, ds))
    val n = names(ds)
    compare("articles.txt", answers(text, n) + s"# the rows\n${rows(ds, n)}")

  test("the article lint rules, their report and their fixes, as recorded"):
    val ds = scratch("lint")
    val text = withServer("lint") { url =>
      val crafted = craftChecks(url)
      val each = ArticleRules.map(r => s"== lint $r\n${get(s"$url/api/lint?rule=${enc(r)}")}\n").mkString
      val all = s"== lint\n${get(s"$url/api/lint")}\n"
      val fixes = ArticleRules
        .map(r =>
          s"== lint fix $r\n${post(s"$url/api/lint/fix?rule=${enc(r)}")}\n== lint $r after\n${get(s"$url/api/lint?rule=${enc(r)}")}\n"
        )
        .mkString
      val fixAll =
        s"== lint fix (every safe rule)\n${post(s"$url/api/lint/fix")}\n== lint after\n${get(s"$url/api/lint")}\n"
      crafted + each + all + fixes + fixAll
    }
    val n = names(ds)
    compare("lint.txt", answers(text, n) + s"# the rows\n${rows(ds, n)}")

  test("articles through the exchange: bundles, imports and a merge with a conflicting paragraph, as recorded"):
    val lib = scratch("xlib")
    val home = scratch("xhome")
    val copy = scratch("xcopy")
    val inc = scratch("xinc")
    val all = List("lib" -> lib, "home" -> home, "copy" -> copy, "inc" -> inc)
    val crafted = withServer("xlib")(craftExchange)
    val first = withServer("xlib")(url => bytes(s"$url/api/bundle"))
    val homeFirst = withServer("xhome")(url => ok(postBytes(s"$url/api/bundle/import", first)))
    val incFirst = withServer("xinc")(url => ok(postBytes(s"$url/api/bundle/import", first)))
    val since = connect(lib)(
      sql"SELECT to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')".query[String].run().head
    )
    Thread.sleep(20)
    val edited = withServer("xlib")(l => withServer("xhome")(h => editExchange(l, h)))
    val (whole, part) = withServer("xlib")(url => (bytes(s"$url/api/bundle"), bytes(s"$url/api/bundle?since=$since")))
    val imported = withServer("xcopy")(url => ok(postBytes(s"$url/api/bundle/import", whole)))
    val incremental = withServer("xinc")(url => ok(postBytes(s"$url/api/bundle/import", part)))
    val merged = withServer("xhome")(url => mergeInto(url, whole))
    def shaOf(b: Array[Byte]): String = BundleFormat.sha256Hex(b).value
    val n = issueNames(merged.map(_._2)) ++ xnames(all) ++
      Map(shaOf(whole) -> "BUNDLE-WHOLE", shaOf(first) -> "BUNDLE-FIRST", shaOf(part) -> "BUNDLE-PART")
    def lines(text: String): String =
      text.linesIterator
        .map { l =>
          "^(\\d{3}) (.*)$".r
            .findFirstMatchIn(l)
            .fold(xjson(l, n))(m => s"${m.group(1)} ${xjson(m.group(2), n)}")
        }
        .mkString("", "\n", "\n")
    def researchRows(ds: DataSource) = rows(ds, n, xjson)
    compare(
      "exchange.txt",
      s"# lib, crafted\n${lines(crafted)}# home and inc imported lib\n${xjson(homeFirst, n)}\n${xjson(incFirst, n)}\n" +
        s"# both went on\n${lines(edited)}" +
        s"# lib, whole\n${bundleText(whole, n)}# lib, since the shared history\n${bundleText(part, n)}" +
        s"# the whole bundle imported into an empty collection\n${xjson(imported, n)}\n${researchRows(copy)}" +
        s"# the part since imported into inc\n${xjson(incremental, n)}\n${researchRows(inc)}" +
        merged.map((k, v) => s"# home merges lib: $k\n${lines(v)}").mkString +
        s"# home's rows\n${researchRows(home)}# lib's rows\n${researchRows(lib)}"
    )

  test("article links followed through a merge: a cited source renamed on one side, a citekey taken on both"):
    val lib = scratch("llib")
    val home = scratch("lhome")
    val all = List("lib" -> lib, "home" -> home)
    val crafted = withServer("llib")(craftExchange)
    val first = withServer("llib")(url => bytes(s"$url/api/bundle"))
    val homeFirst = withServer("lhome")(url => ok(postBytes(s"$url/api/bundle/import", first)))
    val edited = withServer("llib")(l => withServer("lhome")(h => editLinks(l, h)))
    val whole = withServer("llib")(url => bytes(s"$url/api/bundle"))
    val merged = withServer("lhome")(url => mergeInto(url, whole))
    val notes = withServer("lhome") { url =>
      val slugs = connect(home)(sql"SELECT slug FROM articles ORDER BY slug".query[String].run().toList)
      slugs.map(s => s"== the mirror's note of $s\n${get(s"$url/api/articles/$s/export")}\n").mkString
    }
    def shaOf(b: Array[Byte]): String = BundleFormat.sha256Hex(b).value
    val n = issueNames(merged.map(_._2)) ++ xnames(all) ++
      Map(shaOf(whole) -> "BUNDLE-WHOLE", shaOf(first) -> "BUNDLE-FIRST")
    def lines(text: String): String =
      text.linesIterator
        .map { l =>
          "^(\\d{3}) (.*)$".r
            .findFirstMatchIn(l)
            .fold(xjson(l, n))(m => s"${m.group(1)} ${xjson(m.group(2), n)}")
        }
        .mkString("", "\n", "\n")
    compare(
      "links.txt",
      s"# lib, crafted\n${lines(crafted)}# home imported lib\n${xjson(homeFirst, n)}\n" +
        s"# both went on\n${lines(edited)}# lib, whole\n${bundleText(whole, n)}" +
        merged.map((k, v) => s"# home merges lib: $k\n${lines(v)}").mkString +
        s"# home's notes\n${normalise(notes, n)}# home's rows\n${rows(home, n, xjson)}# lib's rows\n${rows(lib, n, xjson)}"
    )
