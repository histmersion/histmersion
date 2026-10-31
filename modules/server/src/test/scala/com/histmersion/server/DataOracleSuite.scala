package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.*
import com.histmersion.values.{Confidence, PlaceKind, SourceStatus}
import com.augustnagro.magnum.*
import io.circe.{parser, Json}
import ox.supervised

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*

/** What data management answers over HTTP, on a crafted pair of collections ("work" and "home"), recorded on the code
  * before data management moved into its own area: the whole bundle of work and the part changed since a moment (every
  * entry, every line); work's bundle imported into an empty collection (the result and every row it wrote); home, which
  * imported work once, merging work's bundle after both went on (changed findings, a renamed citekey, a paragraph both
  * edited, a deleted event, a deleted goal): the plan, the decisions, the apply job's result, the merges, the ledger
  * and every row home holds afterwards; a backup of work restored into an empty database (every table the same), the
  * backups list and its pruning; the lint report of every rule and the safe fixes on a copy; every source's and
  * article's Markdown note; About's counts and storage.
  *
  * Record ids are random, so a uid is written as the record it names (`source:<citekey>`, …), an instance id as the
  * collection's name, a timestamp as `TS`, and a size the disk decides as `N`. `HISTMERSION_UPDATE_ORACLE=1` rewrites
  * the files. Skipped without HISTMERSION_TEST_DB_URL or the PostgreSQL client tools.
  */
class DataOracleSuite extends DbSuite with com.histmersion.db.Bounded:
  private val testDb = TestDb.url
  private val pgBinDir: Option[String] =
    def has(dir: String) = List("pg_dump", "pg_restore").forall(b => Files.isExecutable(Path.of(dir).resolve(b)))
    sys.env.get("HISTMERSION_PG_BIN_DIR").filter(d => d.nonEmpty && has(d))
  override def munitIgnore: Boolean = testDb.isEmpty || pgBinDir.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(10, "min")

  private val oracleDir = Path.of("modules/server/src/test/resources/oracle/data")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private val tmp = Files.createTempDirectory("histmersion-data-oracle")
  private lazy val base = AppConfig.load()
  private lazy val chunk = base.limits.chunkChars
  private lazy val scratch = ScratchDatabases("histmersion_s6o", base.db.copy(url = testDb.get))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  // ── the servers ────────────────────────────────────────────────────────────────────────────

  private def cfgFor(name: String): AppConfig =
    base.copy(
      db = base.db.copy(url = scratch.urlOf(s"histmersion_s6o_${name}_test")),
      backups =
        base.backups.copy(dir = tmp.resolve(s"$name-backups").toString, pgBinDir = pgBinDir.getOrElse(""), keep = 2),
      bundles = base.bundles.copy(dir = tmp.resolve(s"$name-bundles").toString),
      converter = base.converter.copy(spoolDir = tmp.resolve(s"$name-spool").toString),
      acquire = base.acquire.copy(spoolDir = tmp.resolve(s"$name-acquire").toString),
      init = base.init.copy(auto = false),
      runtime = base.runtime.copy(dataDir = tmp.resolve(s"$name-data").toString),
      basemap = base.basemap.copy(dir = tmp.resolve(s"$name-basemap").toString)
    )

  private def withServer[A](name: String)(body: String => A): A =
    supervised {
      val b = Composition.server(cfgFor(name), "127.0.0.1", 0, OfflineConverter()).start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
  private def bytes(url: String): Array[Byte] =
    val r = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray())
    assertEquals(r.statusCode, 200, String(r.body, UTF_8).take(500))
    r.body
  private def get(url: String): String = String(bytes(url), UTF_8)
  private def send(req: HttpRequest.Builder): String =
    val r = http.send(req.build(), HttpResponse.BodyHandlers.ofString())
    s"${r.statusCode} ${r.body}"
  private def postBytes(url: String, body: Array[Byte]): String =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
    )
  private def postJson(url: String, body: String): String =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
    )
  private def ok(answer: String): String =
    assert(answer.startsWith("200 "), answer.take(800))
    answer.drop(4)

  // ── the crafted pair ───────────────────────────────────────────────────────────────────────

  private def src(
      ck: String,
      title: String,
      year: Int,
      findings: List[String] = Nil,
      edges: List[(String, String)] = Nil,
      body: Option[String] = None,
      summary: Option[String] = None,
      status: SourceStatus = SourceStatus.Current
  ): Source =
    Source(
      ck,
      title,
      List("Doe, Jane"),
      None,
      Some(year),
      None,
      status,
      List("bronze age"),
      List("Anatolia"),
      List("zz-oracle"),
      None,
      summary,
      None,
      None,
      None,
      None,
      ocrMarkdown = body,
      findings = findings.zipWithIndex.map { (claim, i) =>
        Finding(
          s"$ck#${i + 1}",
          ck,
          claim,
          Some("Anatolia"),
          Some(GeoPoint(34.6 + i, 40.0)),
          Some(Period(-1400, -1200)),
          Some("LBA"),
          List("oracle"),
          Some(Confidence.High)
        )
      },
      edges = edges.map((dst, kind) => Edge(ck, dst, kind))
    )

  private def site(id: String, label: String, lon: Double, lat: Double): PlaceInput =
    PlaceInput(
      Place(
        id,
        label,
        PlaceKind.Site,
        Some(s"""{"type":"Point","coordinates":[$lon,$lat]}"""),
        Some(Period(-1650, -1180))
      ),
      List(PlaceName(label))
    )

  private def png(n: Byte): Array[Byte] = Array[Byte](0x89.toByte, 'P', 'N', 'G', 13, 10, 26, 10, n, n, n)

  /** Work's records: sources with findings, edges (one to a source nobody has), a body and a renamed citekey; two
    * articles; a goal; a place, a period and an event at the place; figures and a link; an attestation; work items.
    */
  private def craftWork(ds: DataSource): Unit =
    BundleRepo.setLabel(ds, Some("work"))
    SourceRepo.upsert(
      ds,
      src(
        "zzalpha2001_storm",
        "The Storm God",
        2001,
        findings = List("the storm god of Hatti is Tarhunna", "Hattusa burned around 1180 BC"),
        edges = List("zzbeta2005_kingdom" -> "references", "zzunknown1900_lost" -> "references"),
        body = Some("Alpha body. Tarhunna rules the storm. " * 20),
        summary = Some("as written at work")
      ),
      chunk
    )
    SourceRepo.upsert(
      ds,
      src(
        "zzbeta2005_kingdom",
        "The Kingdom",
        2005,
        findings = List("a kingdom in central Anatolia"),
        status = SourceStatus.Contested
      ),
      chunk
    )
    SourceRepo.upsert(ds, src("zzgamma1903_old", "An Old Book", 1903), chunk)
    val _ = SourceRepo.rename(ds, "zzgamma1903_old", "zzgamma1903_prolegomena")
    val _ = ArticleRepo.put(
      ds,
      Article("zz_storm", "Storm gods", "concept", "Intro [[zzalpha2001_storm]].\n\nMiddle.\n\nEnd.")
    )
    val _ = ArticleRepo.put(
      ds,
      Article("zz_storm", "Storm gods", "concept", "Intro [[zzalpha2001_storm]].\n\nMiddle part.\n\nEnd.")
    )
    val _ = ArticleRepo.put(ds, Article("zz_kingdom", "Kingdoms", "culture", "One [[zzbeta2005_kingdom]].\n\nTwo."))
    val _ = GoalRepo.put(ds, "zz_goal", GoalPut("Know the storm gods"))
    val _ = GoalRepo.put(ds, "zz_goal_gone", GoalPut("A goal deleted later"))
    val _ = PlaceRepo.upsertPlaces(ds, List(site("zz_site", "Storm hill", 34.6, 40.0)), Dataset.Local)
    val _ = PeriodRepo.upsert(
      ds,
      List(HistoricalPeriod("zz_period", "Storm period", Some("oracle"), Period(-1400, -1200))),
      Dataset.Local
    )
    EventRepo.put(
      ds,
      "zz_event",
      EventPut(
        "A storm at the hill",
        "battle",
        TimeSpan(Some(-1300), None, None, Some(-1290)),
        placeId = Some("zz_site")
      )
    )
    EventRepo.put(
      ds,
      "zz_event_gone",
      EventPut("An event deleted later", "battle", TimeSpan(Some(-1250), None, None, None))
    )
    val _ = AssetRepo.add(
      ds,
      "zzalpha2001_storm",
      List(
        AssetRepo.Stored("fig1.png", "image/png", png(1), None, None),
        AssetRepo.Stored("fig2.png", "image/png", png(2), None, None)
      )
    )
    val _ = AttestationRepo.create(
      ds,
      AttestationPut(
        "zzalpha2001_storm",
        "place",
        "zz_site",
        quote = Some("Alpha body"),
        charStart = Some(0),
        charEnd = Some(10)
      )
    )
    val _ = QueueRepo.add(ds, Queue.Kind.Tasks, Queue.QueueAdd("check the storm god", topic = Some("oracle")))
    val _ = QueueRepo.add(ds, Queue.Kind.Log, Queue.QueueAdd("crafted the oracle", topic = Some("oracle")))
    val _ = QueueRepo.add(ds, Queue.Kind.Acquire, Queue.QueueAdd("Doe, The Lost Book (1900)", topic = Some("oracle")))

  /** After home imported work once, both go on. */
  private def editBoth(work: DataSource, home: DataSource): Unit =
    // changed findings at work: one claim rewritten, one added
    SourceRepo.upsert(
      work,
      src(
        "zzalpha2001_storm",
        "The Storm God",
        2001,
        findings = List(
          "the storm god of Hatti is Tarhunna of Arinna",
          "Hattusa burned around 1180 BC",
          "a third claim, work's"
        ),
        edges = List("zzbeta2005_kingdom" -> "references", "zzunknown1900_lost" -> "references"),
        body = Some("Alpha body. Tarhunna rules the storm. " * 20),
        summary = Some("edited at work")
      ),
      chunk
    )
    // the same summary edited at home: a conflict
    val _ = SourceRepo.patch(home, "zzalpha2001_storm", SourcePatch(summary = Some("edited at home")), chunk)
    // a renamed citekey at work
    val _ = SourceRepo.rename(work, "zzbeta2005_kingdom", "zzbeta2005_hittites")
    // one paragraph both edited
    val _ = ArticleRepo.put(
      work,
      Article("zz_storm", "Storm gods", "concept", "Intro [[zzalpha2001_storm]].\n\nMiddle part, at work.\n\nEnd.")
    )
    val _ = ArticleRepo.put(
      home,
      Article("zz_storm", "Storm gods", "concept", "Intro [[zzalpha2001_storm]].\n\nMiddle part, at home.\n\nEnd.")
    )
    // edits in different places of the other article: a clean three-way merge
    val _ =
      ArticleRepo.put(work, Article("zz_kingdom", "Kingdoms", "culture", "One (work) [[zzbeta2005_kingdom]].\n\nTwo."))
    val _ =
      ArticleRepo.put(home, Article("zz_kingdom", "Kingdoms", "culture", "One [[zzbeta2005_kingdom]].\n\nTwo (home)."))
    // a deleted event and a deleted goal at work
    val _ = EventRepo.delete(work, "zz_event_gone")
    val _ = GoalRepo.delete(work, "zz_goal_gone")
    // something new at work
    SourceRepo.upsert(work, src("zzdelta2020_new", "A New Book", 2020, findings = List("new at work")), chunk)
    val _ = QueueRepo.add(work, Queue.Kind.Tasks, Queue.QueueAdd("a task work added", topic = Some("oracle")))

  // ── normalisation ──────────────────────────────────────────────────────────────────────────

  private val UuidRe =
    "\\b[A-Z]-[0-9a-f]{8}\\b|[0-9a-f]{64}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r
  private val TimeRe =
    "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}(?::?\\d{2})?)?".r
  private val StampRe = "\\d{8}T\\d{6}Z(?:-\\d+)?".r
  private val PlanRe = "plan [0-9a-f]{8}\\b".r
  private val ShortRe = "(?<![-\\w])[0-9a-f]{8}(?![-\\w])".r

  /** uid → the record it names, in every database the suite made. */
  private def names(dss: List[(String, DataSource)]): Map[String, String] =
    val queries = List(
      "SELECT uid::text, 'source:' || citekey FROM sources",
      "SELECT uid::text, 'finding:' || id FROM findings",
      "SELECT uid::text, 'article:' || slug FROM articles",
      "SELECT v.uid::text, 'version:' || a.slug || '@' || v.version FROM article_versions v JOIN articles a ON a.uid = v.article_uid",
      "SELECT uid::text, 'asset:' || coalesce(citekey, '') || '/' || name FROM assets",
      "SELECT uid::text, 'event:' || id FROM events",
      "SELECT uid::text, 'goal:' || slug FROM goals",
      "SELECT uid::text, 'place:' || id FROM places",
      "SELECT uid::text, 'period:' || id FROM periods",
      "SELECT uid::text, 'queue:' || list || ':' || text FROM queue_items",
      "SELECT uid::text, 'attestation:' || citekey || ':' || target_id FROM attestations",
      "SELECT uid::text, 'tombstone:' || kind FROM tombstones",
      "SELECT alias_uid::text, 'alias-of:' || uid::text FROM uid_aliases"
    )
    def run(ds: DataSource, q: String): List[(String, String)] =
      scala.util.Try(connect(ds)(Sql.raw(q).frag.query[(String, String)].run().toList)).getOrElse(Nil)
    val records = dss.flatMap((_, ds) => queries.flatMap(run(ds, _)))
    val instances =
      dss.flatMap((n, ds) => run(ds, "SELECT id::text, 'x' FROM instance").map((id, _) => id -> s"instance:$n"))
    val merges = dss.flatMap((n, ds) =>
      run(ds, "SELECT id::text, 'x' FROM merges ORDER BY created_at").zipWithIndex.map { case ((id, _), i) =>
        id -> s"merge:$n#${i + 1}"
      }
    )
    (records ++ instances ++ merges).distinctBy(_._1).toMap

  private def normalise(text: String, names: Map[String, String]): String =
    val roots = List(tmp.toString -> "TMP", sys.props("user.home") -> "HOME")
    val t1 = roots.foldLeft(text)((t, r) => t.replace(r._1, r._2))
    val t2 = UuidRe.replaceAllIn(
      t1,
      m =>
        scala.util.matching.Regex
          .quoteReplacement(names.getOrElse(m.matched, if m.matched.length != 36 then m.matched else "UUID"))
    )
    val t3 = TimeRe.replaceAllIn(t2, "TS")
    ShortRe.replaceAllIn(PlanRe.replaceAllIn(StampRe.replaceAllIn(t3, "STAMP"), "plan PLAN"), "SHORT")

  /** Sizes, durations and free space the disk and the clock decide. */
  private val Measured =
    Set(
      "bytes",
      "files",
      "durationMs",
      "elapsedMs",
      "tookMillis",
      "ageSeconds",
      "totalBytes",
      "freeBytes",
      "lastBackupAgeSeconds"
    )

  /** A line's revision and a bundle file's digest: hashes over its uids and clocks. */
  private def digest(key: String): Boolean = key.toLowerCase.matches(".*(rev|hash)(_?(after|before))?")

  /** A dump's digest: pg_dump writes the moment it ran into the archive. */
  private val DumpDigest = "\"sha256\"\\s*:\\s*\"[0-9a-f]{64}\"".r

  private def json(text: String, names: Map[String, String]): String =
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
    parser.parse(text) match
      case Right(j) => normalise(walk(j).spaces2SortKeys, names)
      case Left(_)  => normalise(text, names)

  /** Every entry of a bundle: its name and size; a line file's lines; a blob by its digest; the manifest without the
    * moment it was made and its id.
    */
  private def bundleText(bundle: Array[Byte], names: Map[String, String]): String =
    val in = java.util.zip.GZIPInputStream(ByteArrayInputStream(bundle))
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
        if path == "manifest.json" then
          val m = parser.parse(String(data, UTF_8)).fold(e => fail(e.toString), identity)
          val kept = m
            .mapObject(_.remove("createdAt").remove("bundleId"))
            .hcursor
            .downField("files")
            .withFocus(_.mapArray(_.map(_.mapObject(_.remove("sha256")))))
            .top
            .getOrElse(m)
          out ++= s"== $path\n${json(kept.noSpaces, names)}\n"
        else if path.endsWith(".ndjson") then
          val lines = String(data, UTF_8).linesIterator.toList
          out ++= s"== $path (${lines.size} lines)\n"
          lines.foreach(l => out ++= json(l, names).linesIterator.mkString(" ") + "\n")
        else out ++= s"== $path ${data.length} bytes\n"
        loop()
    loop()
    out.toString

  /** Every row of every table, as JSON, normalised; the tables the migrations fill whole are counted only. */
  private def rows(ds: DataSource, names: Map[String, String]): String =
    val tables = connect(ds)(
      sql"""SELECT table_name::text FROM information_schema.tables WHERE table_schema = 'public'
            AND table_type = 'BASE TABLE' ORDER BY 1""".query[String].run().toList
    )
    tables
      .map { t =>
        val rs = connect(ds)(Sql.raw(s"""SELECT to_jsonb(x)::text FROM "$t" x""").frag.query[String].run().toList)
        val normed = rs.map(r => json(r, names).linesIterator.map(_.trim).mkString(" ")).sorted
        if t == "flyway_schema_history" then s"## $t ${normed.size} rows"
        else if normed.size > 200 then
          s"## $t ${normed.size} rows, ${com.histmersion.files.BundleFormat.sha256Hex(normed.mkString("\n").getBytes(UTF_8)).take(16)}"
        else (s"## $t ${normed.size} rows" :: normed).mkString("\n")
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

  private def awaitApply(url: String, job: String, left: Int = 600): String =
    val j = get(s"$url/api/merge/apply/$job")
    if j.contains("\"done\"") || j.contains("\"failed\"") || left == 0 then j
    else { Thread.sleep(100); awaitApply(url, job, left - 1) }

  private def field(json: String, name: String): String =
    s""""$name"\\s*:\\s*"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1)).getOrElse(fail(s"no $name in $json"))

  test("bundle, import, merge, backup and restore, checks, the mirror's notes and storage, as recorded"):
    val work = scratch("work")
    val home = scratch("home")
    val copy = scratch("copy")
    val restored = scratch("restored")
    val fixed = scratch("fixed")
    craftWork(work)
    val all = List("work" -> work, "home" -> home, "copy" -> copy, "restored" -> restored, "fixed" -> fixed)

    // home imports work once: the shared history
    val first = withServer("work")(url => bytes(s"$url/api/bundle"))
    withServer("home")(url => ok(postBytes(s"$url/api/bundle/import", first)))
    val since = connect(work)(
      sql"SELECT to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')".query[String].run().head
    )
    Thread.sleep(20)
    editBoth(work, home)

    // ── the bundles of work, its import into an empty collection ──
    val (whole, part, lint, lintFixed, notes, about) = withServer("work") { url =>
      val w = bytes(s"$url/api/bundle")
      val p = bytes(s"$url/api/bundle?since=$since")
      val l = get(s"$url/api/lint")
      val n = List("zzalpha2001_storm", "zzbeta2005_hittites", "zzgamma1903_prolegomena", "zzdelta2020_new")
        .map(ck => s"== source $ck\n${get(s"$url/api/sources/$ck/export")}") ++
        List("zz_storm", "zz_kingdom").map(s => s"== article $s\n${get(s"$url/api/articles/$s/export")}")
      (w, p, l, "", n.mkString("\n"), get(s"$url/api/about"))
    }
    val imported = withServer("copy")(url => ok(postBytes(s"$url/api/bundle/import", whole)))

    // ── home merges work's bundle ──
    val (plan, planMd, applied, mergeList, mergeShow, ledger, session) = withServer("home") { url =>
      val up = ok(postBytes(s"$url/api/bundle/upload", whole))
      val sha = field(up, "sha256")
      val p0 = ok(postJson(s"$url/api/merge/plan", s"""{"bundle":"$sha"}"""))
      val open = parser
        .parse(p0)
        .toOption
        .flatMap(_.hcursor.downField("issues").focus)
        .flatMap(_.asArray)
        .getOrElse(Vector.empty)
        .flatMap(i =>
          i.hcursor
            .downField("id")
            .as[String]
            .toOption
            .filter(_ => i.hcursor.downField("decision").focus.forall(_.isNull))
        )
      val resolutions =
        s"""{"resolve":[${open.map(id => s"""{"issue":"$id","decision":"keep-right"}""").mkString(",")}]}"""
      val s = ok(
        send(
          HttpRequest
            .newBuilder(URI.create(s"$url/api/merge/sessions/$sha"))
            .header("content-type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(resolutions))
        )
      )
      val p1 = ok(postJson(s"$url/api/merge/plan", s"""{"bundle":"$sha","resolutions":$resolutions}"""))
      val decided = parser.decode[MergeResolutions](resolutions).fold(e => fail(e.toString), identity)
      val template = MergeResolutionsText.template(
        parser.decode[MergePlan](p1).fold(e => fail(e.toString), identity),
        decided
      )
      val md = template + "\n" + ok(
        send(
          HttpRequest
            .newBuilder(URI.create(s"$url/api/merge/resolutions"))
            .header("content-type", "text/plain")
            .POST(HttpRequest.BodyPublishers.ofString(template))
        )
      )
      val hash = field(p1, "planHash")
      val job =
        ok(postJson(s"$url/api/merge/apply", s"""{"bundle":"$sha","planHash":"$hash","resolutions":$resolutions}"""))
      val done = awaitApply(url, field(job, "job"))
      val ml = get(s"$url/api/merges")
      val id = field(ml, "id")
      (p1, md, done, ml, get(s"$url/api/merges/$id"), get(s"$url/api/merges/ledger?limit=1000"), s)
    }

    // ── a backup of work restored into an empty database; the backups kept ──
    val (backups, restoredResult) =
      val (dump, list) = withServer("work") { url =>
        val b1 = ok(postJson(s"$url/api/backup", ""))
        val bs = (1 to 2).map(_ => ok(postJson(s"$url/api/backup", ""))).toList
        (field(bs.last, "file"), (b1 :: bs).mkString("\n") + "\n" + get(s"$url/api/backups"))
      }
      Files.createDirectories(tmp.resolve("restored-backups"))
      val _ = Files.copy(tmp.resolve("work-backups").resolve(dump), tmp.resolve("restored-backups").resolve(dump))
      (list, withServer("restored")(url => ok(postJson(s"$url/api/restore", s"""{"file":"$dump"}"""))))
    val n = names(all)
    val sameAsWork = rows(work, n) == rows(restored, n)

    // ── the safe fixes, on a copy of work ──
    val fixedResult = withServer("fixed") { url =>
      ok(postBytes(s"$url/api/bundle/import", whole))
      ok(postJson(s"$url/api/lint/fix", ""))
    }
    def shaOf(b: Array[Byte]) = com.histmersion.files.BundleFormat.sha256Hex(b)
    val issueNames = parser
      .parse(plan)
      .toOption
      .flatMap(_.hcursor.downField("issues").focus)
      .flatMap(_.asArray)
      .getOrElse(Vector.empty)
      .flatMap { i =>
        val c = i.hcursor
        for
          id <- c.downField("id").as[String].toOption
          label <- c
            .downField("right")
            .downField("label")
            .as[String]
            .toOption
            .orElse(c.downField("left").downField("label").as[String].toOption)
        yield id -> s"${id.take(1)}-issue:$label"
      }
      .toMap
    val names2 = issueNames ++ names(all) ++ Map(
      shaOf(whole) -> "BUNDLE-WHOLE",
      shaOf(first) -> "BUNDLE-FIRST",
      shaOf(part) -> "BUNDLE-PART"
    )

    compare(
      "bundle.txt",
      s"# work, whole\n${bundleText(whole, names2)}# work, since the shared history\n${bundleText(part, names2)}"
    )
    compare("import.txt", s"# the result\n${json(imported, names2)}\n# the rows\n${rows(copy, names2)}")
    compare(
      "merge.txt",
      s"# the session\n${json(session, names2)}\n# the plan\n${json(plan, names2)}\n# the resolution file and how it reads back\n" +
        s"${json(planMd, names2)}\n# applied\n${json(applied, names2)}\n# merges\n${json(mergeList, names2)}\n" +
        s"# the merge\n${json(mergeShow, names2)}\n# the ledger\n${json(ledger, names2)}\n# home's rows\n${rows(home, names2)}"
    )
    compare(
      "backup.txt",
      s"# backups\n${backups.linesIterator.map(l => DumpDigest.replaceAllIn(json(l, names2), "\"sha256\" : \"DIGEST\"")).mkString("\n")}\n# restored\n${json(restoredResult, names2)}\n" +
        s"# every row of the restored database is work's: $sameAsWork\n"
    )
    compare(
      "lint.txt",
      s"# every rule\n${json(lint, names2)}\n# the safe fixes on a copy\n${json(fixedResult, names2)}\n"
    )
    compare("notes.txt", normalise(notes, names2))
    val aboutJson = parser.parse(about).fold(e => fail(e.toString), identity)
    val kept = Json.obj(
      "counts" -> aboutJson.hcursor.downField("counts").focus.getOrElse(Json.Null),
      "jobs" -> aboutJson.hcursor.downField("jobs").focus.getOrElse(Json.Null),
      "storage" -> aboutJson.hcursor.downField("storage").focus.getOrElse(Json.Null)
    )
    compare("storage.txt", json(kept.noSpaces, names2) + "\n")
    assert(sameAsWork, "the restored database differs from work")
