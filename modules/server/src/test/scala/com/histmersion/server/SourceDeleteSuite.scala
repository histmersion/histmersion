package com.histmersion.server

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.citations.CitationFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.api.{GeoPoint, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.{Db, Migrate}

import com.augustnagro.magnum.*
import java.nio.file.{Files, Path}
import com.histmersion.api.library.Confidence
import com.histmersion.db.*
import com.histmersion.api.automation.Goal
import com.histmersion.api.backup.Backup
import com.histmersion.api.catalogue.{Period, Place}
import com.histmersion.api.library.{DeletePart, Edge, Finding, Source}
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.api.research.Article
import com.histmersion.api.Api
import com.histmersion.api.library.DeleteBlocker

/** Deleting a source (#969), against a real Postgres: the dry run lists everything and changes nothing; an article
  * citing the source (by its citekey, an old citekey or a short form), an event sourced to it, or a job still writing
  * it refuses the delete; the applied delete empties every table that held the source, parks other sources' edges to
  * it, drops its old citekeys, leaves a tombstone, logs itself, keeps Calibre's id in the answer and removes the stored
  * originals after the commit. Skipped without HISTMERSION_TEST_DB_URL.
  */
class SourceDeleteSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Prefix = "zzdel"
  private val Old = "zzdel2019_book" // the citekey it had before a rename: an alias
  private val Del = "zzdel2020_book"
  private val Other = "zzdelb2021_other"
  private val Twin = "zzdelc2022_twin" // holds the same file as Del in one test
  private val Slug = "zzdel_article"
  private val Event = "zzdel_event"
  private val Goal = "zzdel_goal"
  private val Place = "zzdel_place"
  private val Sha = "zzdelsha256000000000000000000000000000000000000000000000000000000"

  private lazy val spool = Files.createTempDirectory("histmersion-del-spool")
  private lazy val acquire = Files.createTempDirectory("histmersion-del-acquire")

  private lazy val cfg =
    val b = AppConfig.load()
    b.modify(
      _.db).setTo(b.db.modify(_.url).setTo(testDb.get)).modify(_.converter).setTo(b.converter.modify(_.spoolDir).setTo(spool.toString)).modify(_.acquire).setTo(b.acquire.modify(_.spoolDir).setTo(acquire.toString)
    )
  private lazy val deps =
    val ds = testPool(cfg.db)
    Migrate.run(ds)
    Deps(cfg, ds, testQueryPool(cfg.db, cfg.query))
  private lazy val ds = deps.ds
  private lazy val h = ServerWiring(deps, NoConverter)

  private object NoConverter extends ConverterClient:
    def health: Either[String, String] = Right("ok")
    def convert(
        file: com.histmersion.util.storage.Blob,
        filename: String,
        quality: Boolean
    ): Either[String, ConvertedDoc] = Left("not used")

  private def source(ck: String, edges: List[Edge] = Nil, findings: Int = 0, calibre: Option[String] = None) = Source(
    citekey = com.histmersion.api.library.Citekey(ck),
    title = com.histmersion.api.library.Title(s"Title $ck"),
    authors = (List("A")).map(com.histmersion.api.library.AuthorName.apply),
    doi = None,
    year = (Some(2020)).map(com.histmersion.api.catalogue.Year.apply),
    venue = None,
    status = com.histmersion.api.library.Source.Status.Current,
    topics = Nil,
    regions = Nil,
    tags = Nil,
    period = None,
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = calibre,
    ocrMarkdown = Some(s"# $ck\n\nbody text of $ck"),
    findings = (0 until findings).toList.map(i =>
      Finding(
        s"$ck#$i",
        com.histmersion.api.library.Citekey(ck),
        s"claim $i",
        Some("Anatolia"),
        Some(GeoPoint(32.0, 39.0)),
        Some(Period(-1300, -1200)),
        None,
        Nil,
        Some(Confidence.High)
      )
    ),
    edges = edges,
    notes = None
  )

  private def put(s: Source): Unit = SourceFixtures.upsert(ds, s, cfg.limits.chunkChars)

  private def clean(): Unit =
    connect(ds):
      sql"DELETE FROM source_aliases WHERE alias LIKE ${Prefix + "%"} OR citekey LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM articles WHERE slug LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM events WHERE id LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM goals WHERE slug LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM places WHERE id LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM jobs WHERE id LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM unresolved_edges WHERE dst LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE ${"%" + Prefix + "%"}".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def count(q: Frag): Int = connect(ds)(q.query[Int].run().head)

  /** The whole picture: Del (renamed from Old) with findings, figures, attestations, an assessment, a dating, a goal
    * link, a coverage reason, a queue mention and both stored originals; Other citing it with an edge, attesting its
    * finding and that edge, and linking a figure to its finding.
    */
  private def seed(): Unit =
    put(
      source(
        Old,
        findings = 2,
        calibre = Some("1693"),
        edges = List(Edge(com.histmersion.api.library.Citekey(Old), com.histmersion.api.library.Citekey(Other), "challenges"), Edge(com.histmersion.api.library.Citekey(Old), com.histmersion.api.library.Citekey("zzdelmissing2001_x"), "references"))
      )
    )
    put(source(Other, edges = Nil))
    SourceFixtures.rename(ds, Old, Del).fold(e => fail(e.message), _ => ())
    put(source(Other, edges = List(Edge(com.histmersion.api.library.Citekey(Other), com.histmersion.api.library.Citekey(Del), "references"))))
    connect(ds):
      sql"UPDATE sources SET sha256 = $Sha WHERE citekey = $Del".update.run()
      sql"INSERT INTO places (id, label, kind) VALUES ($Place, 'Zz place', 'site')".update.run()
      sql"INSERT INTO finding_places (finding_id, place_id) VALUES (${Del + "#0"}, $Place)".update.run()
      sql"INSERT INTO assessments (citekey, verdict) VALUES ($Del, 'current')".update.run()
      sql"""INSERT INTO attestations (citekey, target_type, target_id) VALUES
              ($Del, 'finding', ${Del + "#1"}),
              ($Other, 'finding', ${Del + "#0"}),
              ($Other, 'edge', ${s"$Other|$Del|references"}),
              ($Other, 'edge', ${s"$Del|$Other|challenges"})""".update.run()
      sql"""INSERT INTO assets (citekey, name, mime, bytes, sha256) VALUES
              ($Del, 'fig1.png', 'image/png', '\\x00'::bytea, 'x'),
              ($Other, 'fig2.png', 'image/png', '\\x00'::bytea, 'y')""".update.run()
      sql"""INSERT INTO asset_links (citekey, asset_name, relation, target_type, target_id) VALUES
              ($Del, 'fig1.png', 'page_of', 'source', $Del),
              ($Other, 'fig2.png', 'illustrates', 'finding', ${Del + "#0"})""".update.run()
      sql"INSERT INTO events (id, label) VALUES ($Event, 'Zz event')".update.run()
      sql"INSERT INTO event_datings (event_id, citekey) VALUES ($Event, $Del)".update.run()
      sql"INSERT INTO goals (slug, title) VALUES ($Goal, 'Zz goal')".update.run()
      sql"INSERT INTO goal_links (goal_slug, kind, ref) VALUES ($Goal, 'source', $Del)".update.run()
      sql"""INSERT INTO queue_items (list, text, status, reason)
            VALUES ('acquire', 'zzdel the book', 'done', ${s"ingested as [[$Del]]"})""".update.run()
    // an article that leaves it out, with a reason — and cites Other only
    val _ = ArticleFixtures.put(
      ds,
      Article(
        Slug,
        "Zz",
        "concept",
        s"# Zz\n\nOnly [[$Other]].",
        None,
        Nil,
        Nil,
        None,
        reasons = Map(Del -> "off topic", Other -> "kept")
      )
    )
    Files.writeString(spool.resolve(s"$Sha.pdf"), "%PDF original")
    List(Del, Old).foreach { k =>
      Files.createDirectories(acquire.resolve(k)); Files.writeString(acquire.resolve(k).resolve("paper.pdf"), "%PDF")
    }

  private def uid(ck: String): String =
    connect(ds)(sql"SELECT uid::text FROM sources WHERE citekey = $ck".query[String].run().head)

  test(
    "the dry run lists every part, the parked edges, the aliases, the files and the Calibre id — and changes nothing"
  ):
    seed()
    val d = h.library.sources.delete(Del, Source.DeleteRequest()).fold(e => fail(e.message), identity)
    assert(!d.applied && !d.refused, d.toString)
    assertEquals(d.title, s"Title $Old")
    import com.histmersion.api.library.DeletePart.*
    assertEquals(
      d.counts.map(c => c.part -> c.rows).toMap,
      Map(
        // the findings also land in whatever gazetteer places other suites seeded, so count them here
        Findings -> 2,
        FindingPlaces -> count(sql"""SELECT count(*)::int FROM finding_places fp
        JOIN findings f ON f.id = fp.finding_id WHERE f.citekey = $Del"""),
        EdgesOut -> 1,
        ParkedOut -> 1,
        EdgesIn -> 1,
        Chunks -> d.count(Chunks),
        Assets -> 1,
        AssetLinks -> 1,
        AssetLinksTo -> 1,
        Attestations -> 1,
        AttestationsOf -> 2,
        Assessments -> 1,
        Aliases -> 1,
        Identifiers -> 1,
        EventDatings -> 1,
        GoalLinks -> 1,
        CoverageReasons -> 1
      )
    )
    assert(d.count(Chunks) > 0 && d.count(FindingPlaces) >= 1)
    assertEquals(d.aliases, List(Old))
    assertEquals(d.edgesIn, List(Edge(com.histmersion.api.library.Citekey(Other), com.histmersion.api.library.Citekey(Del), "references")))
    assertEquals(d.coverage, List(Slug))
    assertEquals(d.calibreId, Some("1693"))
    assertEquals(d.mentions.size, 1)
    assertEquals(
      d.files.toSet,
      Set(
        spool.resolve(s"$Sha.pdf"),
        acquire.resolve(Del).resolve("paper.pdf"),
        acquire.resolve(Del),
        acquire.resolve(Old).resolve("paper.pdf"),
        acquire.resolve(Old)
      ).map(_.toString)
    )
    // nothing moved
    assert(SourceFixtures.load(ds, Del).isDefined)
    assert(Files.exists(spool.resolve(s"$Sha.pdf")))
    assertEquals(count(sql"SELECT count(*)::int FROM edges WHERE dst = $Del"), 1)

  test("refused while an article cites it — by its citekey, an old citekey or a short form — with no override"):
    seed()
    List(s"See [[$Del]].", s"See [[$Old|the book]].", s"See [[zzdel2020]] (short).").zipWithIndex.foreach { (body, i) =>
      val slug = s"${Slug}_cites$i"
      val _ = ArticleFixtures.put(ds, Article(slug, s"Cites $i", "concept", s"# C\n\n$body", None, Nil, Nil, None))
      val dry = h.library.sources.delete(Del, Source.DeleteRequest()).fold(e => fail(e.message), identity)
      assertEquals(dry.blockers.map(b => (b.kind, b.id)), List((DeleteBlocker.Kind.Article, slug)), body)
      val e = h.library.sources
        .delete(Del, Source.DeleteRequest(apply = true))
        .swap
        .getOrElse(fail("the delete was not refused"))
      assert(e.message.contains(s"cited by article $slug") && e.message.contains("no override"), e.message)
      assert(SourceFixtures.load(ds, Del).isDefined, "a refused delete deleted")
      ArticleFixtures.delete(ds, slug): Unit
    }

  test("refused while an event is sourced to it, or a job is still writing it"):
    seed()
    connect(ds):
      sql"UPDATE events SET citekey = $Del WHERE id = $Event".update.run()
      sql"INSERT INTO jobs (id, kind, citekey, status) VALUES ('zzdel_job', 'ingest', $Old, 'running')".update.run()
    val d = h.library.sources.delete(Del, Source.DeleteRequest()).fold(e => fail(e.message), identity)
    assertEquals(d.blockers.map(_.kind), List(DeleteBlocker.Kind.Event, DeleteBlocker.Kind.Job))
    assert(h.library.sources.delete(Del, Source.DeleteRequest(apply = true)).isLeft)
    assert(SourceFixtures.load(ds, Del).isDefined)

  test(
    "an old citekey follows the alias (#1799): the dry run names the source under its current key; an unknown one is not deleted"
  ):
    seed()
    assertEquals(h.library.sources.delete(Old, Source.DeleteRequest()).map(_.citekey), Right(Del))
    assert(
      h.library.sources
        .delete("zzdel1999_nothing", Source.DeleteRequest())
        .left
        .exists(_.code.contains(Write.CodeNotFound))
    )
    assert(SourceFixtures.load(ds, Del).isDefined)

  test(
    "--apply: every table is emptied of it, incoming edges are parked, aliases go, a tombstone and a log entry stay, the files go"
  ):
    seed()
    val theUid = uid(Del)
    val d = h.library.sources.delete(Del, Source.DeleteRequest(apply = true)).fold(e => fail(e.message), identity)
    assert(d.applied && d.filesFailed.isEmpty, d.toString)
    assertEquals(d.calibreId, Some("1693"), "Calibre's id is reported, never touched")

    assert(SourceFixtures.load(ds, Del).isEmpty)
    def none(q: Frag) = assertEquals(count(q), 0, q.sqlString)
    none(sql"SELECT count(*)::int FROM sources WHERE citekey IN ($Del, $Old)")
    none(sql"SELECT count(*)::int FROM findings WHERE citekey = $Del")
    none(sql"SELECT count(*)::int FROM finding_places WHERE place_id = $Place")
    none(sql"SELECT count(*)::int FROM edges WHERE src = $Del OR dst = $Del")
    none(sql"SELECT count(*)::int FROM unresolved_edges WHERE src = $Del")
    none(sql"SELECT count(*)::int FROM source_chunks WHERE citekey = $Del")
    none(sql"SELECT count(*)::int FROM assets WHERE citekey = $Del")
    none(sql"SELECT count(*)::int FROM asset_links WHERE citekey = $Del OR target_id LIKE ${Del + "%"}")
    none(sql"SELECT count(*)::int FROM attestations WHERE citekey = $Del")
    none(sql"SELECT count(*)::int FROM assessments WHERE citekey = $Del")
    none(sql"SELECT count(*)::int FROM source_aliases WHERE alias = $Old OR citekey = $Del")
    none(sql"SELECT count(*)::int FROM source_identifiers WHERE source_uid = $theUid::uuid")
    none(sql"SELECT count(*)::int FROM event_datings WHERE citekey = $Del")
    none(sql"SELECT count(*)::int FROM goal_links WHERE ref = $Del")
    assertEquals(SourceFixtures.aliasOf(ds, Old), None, "the old citekey no longer resolves")

    // other sources' data: the edge is parked, not lost; the attestation of that edge stays, the
    // attestations of its finding and of its own edge go; Other's own figure stays
    assertEquals(
      connect(ds)(sql"SELECT src, kind FROM unresolved_edges WHERE dst = $Del".query[(String, String)].run().toList),
      List((Other, "references"))
    )
    assertEquals(
      connect(ds)(sql"SELECT target_id FROM attestations WHERE citekey = $Other".query[String].run().toList),
      List(s"$Other|$Del|references")
    )
    assertEquals(count(sql"SELECT count(*)::int FROM assets WHERE citekey = $Other"), 1)
    // the article stays; only the reason keyed by the deleted source went
    assertEquals(ArticleFixtures.load(ds, Slug).map(_.reasons), Some(Map(Other -> "kept")))
    // the queue item that mentions it is left as it is
    assertEquals(count(sql"SELECT count(*)::int FROM queue_items WHERE reason = ${s"ingested as [[$Del]]"}"), 1)

    // a tombstone, so a bundle merge does not bring it back (V031) — its findings' too
    assertEquals(
      connect(ds)(sql"SELECT kind FROM tombstones WHERE uid = $theUid::uuid".query[String].run().toList),
      List("source")
    )
    // the log entry
    val log = connect(ds)(
      sql"SELECT text, topic FROM queue_items WHERE id = ${d.logEntry.get}".query[(String, Option[String])].run().head
    )
    assert(log._1.startsWith(s"deleted $Del (Title $Old): 2 findings"), log._1)
    assert(log._1.contains("edges in (parked)") && log._1.contains("Calibre entry 1693 kept"), log._1)
    assertEquals(log._2, Some(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString))
    // the stored originals, after the commit
    assert(!Files.exists(spool.resolve(s"$Sha.pdf")))
    assert(!Files.exists(acquire.resolve(Del)) && !Files.exists(acquire.resolve(Old)))

    // ingested again under the same citekey, the parked edge comes back
    put(source(Del))
    assertEquals(CitationFixtures.neighbors(ds, Del).map(n => (n.direction, n.citekey)).toList, List(("in", Other)))

  test("a stored original another source still holds stays"):
    seed()
    put(source(Twin))
    connect(ds)(sql"UPDATE sources SET sha256 = $Sha WHERE citekey = $Twin".update.run(): Unit)
    val d = h.library.sources.delete(Del, Source.DeleteRequest(apply = true)).fold(e => fail(e.message), identity)
    assert(!d.files.contains(spool.resolve(s"$Sha.pdf").toString), d.files.toString)
    assert(Files.exists(spool.resolve(s"$Sha.pdf")))

  test("the delete is a write: refused while a restore holds the maintenance lock; the dry run is not"):
    seed()
    var out: Either[Api.Error, Source.Deletion] = Left(Api.Error("never ran")) // set inside the lock, read after it
    var dry: Either[Api.Error, Source.Deletion] = Left(Api.Error("never ran"))
    val _ = h.maintenance.exclusively("test-restore") {
      out = h.library.sources.delete(Del, Source.DeleteRequest(apply = true))
      dry = h.library.sources.delete(Del, Source.DeleteRequest())
      Right(())
    }
    assertEquals(out.swap.toOption.flatMap(_.code), Some(Backup.CodeMaintenance))
    assert(dry.isRight)
    assert(SourceFixtures.load(ds, Del).isDefined)
