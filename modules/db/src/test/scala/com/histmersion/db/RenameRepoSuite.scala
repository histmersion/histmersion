package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.values.{Confidence, SourceStatus}

/** Renaming a citekey (V027): the foreign keys cascade, every column without one is rewritten, finding ids move,
  * `[[links]]` in articles (versioned) and source notes follow, parked edges waiting for the new name are promoted, and
  * the old citekey keeps resolving as an alias. Against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise.
  */
class RenameRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d); d

  private val Prefix = "zzrn"
  private val Old = "zzrn2025_book"
  private val New = "zzrn2011_book"
  private val Other = "zzrnb2020_other"
  private val Waiter = "zzrnc2021_waiter"
  private val Slug = "zzrn_article"
  private val Place = "zzrn_place"
  private val Event = "zzrn_event"

  private def source(
      ck: String,
      edges: List[Edge] = Nil,
      notes: Option[String] = None,
      summary: Option[String] = None,
      findings: Int = 0
  ) = Source(
    citekey = ck,
    title = s"Title $ck",
    authors = List("A"),
    doi = None,
    year = Some(2011),
    venue = None,
    status = SourceStatus.Current,
    topics = Nil,
    regions = Nil,
    tags = Nil,
    period = None,
    summary = summary,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = Some("1693"),
    ocrMarkdown = Some(s"# $ck\n\nbody text of $ck"),
    findings = (0 until findings).toList.map(i =>
      Finding(
        s"$ck#$i",
        ck,
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
    notes = notes
  )

  private def put(s: Source): Unit = SourceRepo.upsert(ds, s, cfg.limits.chunkChars)

  private def clean(): Unit =
    connect(ds):
      sql"DELETE FROM source_aliases WHERE alias LIKE ${Prefix + "%"} OR citekey LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM alias_tombstones WHERE alias LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM articles WHERE slug LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM events WHERE id LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM places WHERE id LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM jobs WHERE id LIKE ${Prefix + "%"}".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def count(q: Frag): Int = connect(ds)(q.query[Int].run().head)

  test("V027: every foreign key into sources, findings and assets cascades on update, and keeps its delete action"):
    connect(ds):
      val fks = sql"""SELECT c.conrelid::regclass::text, c.conname, c.confupdtype::text, c.confdeltype::text
                      FROM pg_constraint c
                      WHERE c.contype = 'f'
                        AND c.confrelid IN ('sources'::regclass, 'findings'::regclass, 'assets'::regclass)"""
        .query[(String, String, String, String)]
        .run()
        .toList
      val tables = fks.map(_._1).toSet
      // the live FKs the rename relies on — a missing one would make the UPDATE fail or orphan rows
      List(
        "findings",
        "assessments",
        "attestations",
        "source_chunks",
        "assets",
        "asset_links",
        "unresolved_edges",
        "edges",
        "finding_places",
        "source_aliases"
      )
        .foreach(t => assert(tables(t), s"no FK from $t: $fks"))
      fks.foreach((t, n, upd, _) => assertEquals(upd, "c", s"$t.$n does not cascade on update"))
      // ON DELETE CASCADE survived the redefinition
      fks.foreach((t, n, _, del) => assertEquals(del, "c", s"$t.$n lost its ON DELETE CASCADE"))
      assertEquals(fks.count(_._1 == "edges"), 2) // src and dst

  test("V027 replayed over pre-V027 data: rows are kept, and an UPDATE of the key cascades through them"):
    val script = scala.io.Source.fromResource("db/migration/V027__citekey_rename.sql").mkString
    val schema = "zz_v027_replay"
    connect(ds):
      val st = summon[DbCon].connection.createStatement()
      try
        st.execute(s"DROP SCHEMA IF EXISTS $schema CASCADE; CREATE SCHEMA $schema; SET search_path TO $schema, public")
        st.execute("""CREATE TABLE sources (citekey text PRIMARY KEY);
             CREATE TABLE findings (id text PRIMARY KEY,
                                    citekey text NOT NULL REFERENCES sources(citekey) ON DELETE CASCADE);
             CREATE TABLE finding_places (finding_id text NOT NULL REFERENCES findings(id) ON DELETE CASCADE,
                                          place_id text NOT NULL);
             CREATE TABLE assets (citekey text NOT NULL REFERENCES sources(citekey) ON DELETE CASCADE,
                                  name text NOT NULL, PRIMARY KEY (citekey, name));
             CREATE TABLE asset_links (citekey text NOT NULL, asset_name text NOT NULL,
                                       FOREIGN KEY (citekey, asset_name) REFERENCES assets(citekey, name) ON DELETE CASCADE);
             INSERT INTO sources VALUES ('a2020_x'), ('b2021_y');
             INSERT INTO findings VALUES ('a2020_x#0', 'a2020_x'), ('b2021_y#0', 'b2021_y');
             INSERT INTO finding_places VALUES ('a2020_x#0', 'p1');
             INSERT INTO assets VALUES ('a2020_x', 'f.png');
             INSERT INTO asset_links VALUES ('a2020_x', 'f.png');""")
        st.execute(script)
        def rows(q: String): List[String] =
          val rs = st.executeQuery(q)
          Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1)).toList
        assertEquals(
          rows("SELECT id || '@' || citekey FROM findings ORDER BY id"),
          List("a2020_x#0@a2020_x", "b2021_y#0@b2021_y")
        )
        st.execute("UPDATE sources SET citekey = 'a1999_x' WHERE citekey = 'a2020_x'")
        st.execute("UPDATE findings SET id = 'a1999_x#0' WHERE id = 'a2020_x#0'")
        assertEquals(
          rows("SELECT id || '@' || citekey FROM findings ORDER BY id"),
          List("a1999_x#0@a1999_x", "b2021_y#0@b2021_y")
        )
        assertEquals(rows("SELECT finding_id FROM finding_places"), List("a1999_x#0"))
        assertEquals(rows("SELECT citekey FROM asset_links"), List("a1999_x"))
        // the delete action is still CASCADE
        st.execute("DELETE FROM sources WHERE citekey = 'a1999_x'")
        assertEquals(rows("SELECT count(*) FROM findings"), List("1"))
        assertEquals(rows("SELECT count(*) FROM asset_links"), List("0"))
        assertEquals(rows("SELECT count(*) FROM source_aliases"), List("0"))
      finally st.execute(s"RESET search_path; DROP SCHEMA IF EXISTS $schema CASCADE")

  test("a rename moves every table, finding id, link and parked edge, and leaves an alias"):
    // ── fixtures: the renamed source, a source citing it, one waiting for the NEW name ──
    put(source(Old, findings = 2, edges = List(Edge(Old, Other, "challenges")), notes = Some(s"Self link [[$Old]].")))
    put(
      source(
        Other,
        edges = List(Edge(Other, Old, "references")),
        notes = Some(s"See [[$Old|Beckman]] and the short form [[zzrn2025]], not [[${Old}x]]."),
        summary = Some(s"Cites [[$Old#ch-2]].")
      )
    )
    put(source(Waiter, edges = List(Edge(Waiter, New, "references"), Edge(Waiter, "zzrn2011", "updates"))))
    assertEquals(count(sql"SELECT count(*)::int FROM unresolved_edges WHERE src = $Waiter"), 2)
    val body =
      s"""# Article
         |
         |Exact [[$Old]], labelled [[$Old|the book]], anchored [[$Old#ch-1]], short [[zzrn2025]],
         |escaped \\[\\[$Old\\]\\], embedded ![[$Old]] and unrelated [[$Other]].""".stripMargin
    val _ = ArticleRepo.put(ds, Article(Slug, "Zz", "concept", body, Some(s"About [[$Old]]."), Nil, Nil, None))
    val versionsBefore = ArticleRepo.listVersions(ds, Slug).size
    connect(ds):
      sql"""INSERT INTO places (id, label, kind, source) VALUES ($Place, 'Zz place', 'site', $Old)""".update.run()
      sql"INSERT INTO finding_places (finding_id, place_id) VALUES (${Old + "#0"}, $Place)".update.run()
      sql"INSERT INTO assessments (citekey, verdict) VALUES ($Old, 'current')".update.run()
      sql"""INSERT INTO attestations (citekey, target_type, target_id) VALUES
              ($Old, 'finding', ${Old + "#1"}),
              ($Other, 'edge', ${s"$Other|$Old|references"})""".update.run()
      sql"""INSERT INTO assets (citekey, name, mime, bytes, sha256)
            VALUES ($Old, 'fig1.png', 'image/png', '\\x00'::bytea, 'x')""".update.run()
      sql"""INSERT INTO asset_links (citekey, asset_name, relation, target_type, target_id) VALUES
              ($Old, 'fig1.png', 'illustrates', 'finding', ${Old + "#0"}),
              ($Old, 'fig1.png', 'page_of', 'source', $Old)""".update.run()
      sql"INSERT INTO events (id, label, citekey) VALUES ($Event, 'Zz event', $Old)".update.run()
      sql"INSERT INTO event_datings (event_id, citekey) VALUES ($Event, $Old)".update.run()
      sql"INSERT INTO event_edges (src_id, dst_id, kind, citekey) VALUES ($Event, $Event, 'during', $Old)".update.run()
      sql"INSERT INTO event_participants (event_id, actor, citekey) VALUES ($Event, 'Hattusili', $Old)".update.run()
      sql"INSERT INTO jobs (id, kind, citekey, status) VALUES ('zzrn_job', 'ingest', $Old, 'done')".update.run()
      sql"INSERT INTO place_types (place_id, type_label, citekey) VALUES ($Place, 'capital', $Old)".update.run()
      // role `core`: the `extent` footprint is the place's own, rebuilt by a trigger (V024)
      sql"""INSERT INTO place_footprints (place_id, role, geom, citekey)
            VALUES ($Place, 'core', ST_GeomFromText('POINT(32 39)', 4326), $Old)""".update.run()

    val counts = SourceRepo.rename(ds, Old, New).fold(e => fail(e.message), identity)

    // the source and everything with a foreign key
    assert(SourceRepo.get(ds, Old).isEmpty)
    val (detail, findings) = SourceRepo.get(ds, New).get
    assertEquals(detail.citekey, New)
    assertEquals(findings.size, 2)
    assertEquals(counts.findings, 2)
    connect(ds):
      assertEquals(
        sql"SELECT id FROM findings WHERE citekey = $New ORDER BY id".query[String].run().toList,
        List(s"$New#0", s"$New#1")
      )
      assertEquals(
        sql"SELECT finding_id FROM finding_places WHERE place_id = $Place".query[String].run().toList,
        List(s"$New#0")
      )
      assertEquals(
        sql"SELECT citekey FROM assessments WHERE citekey IN ($Old, $New)".query[String].run().toList,
        List(New)
      )
      assert(sql"SELECT 1 FROM source_chunks WHERE citekey = $New".query[Int].run().nonEmpty)
      assert(sql"SELECT 1 FROM source_chunks WHERE citekey = $Old".query[Int].run().isEmpty)
      assertEquals(
        sql"SELECT citekey FROM assets WHERE name = 'fig1.png' AND citekey IN ($Old, $New)"
          .query[String]
          .run()
          .toList,
        List(New)
      )
      assertEquals(
        sql"""SELECT citekey || ':' || target_type || ':' || target_id FROM asset_links
                         WHERE citekey IN ($Old, $New) ORDER BY target_type""".query[String].run().toList,
        List(s"$New:finding:$New#0", s"$New:source:$New")
      )
      assertEquals(
        sql"""SELECT citekey || ':' || target_id FROM attestations
                         WHERE citekey IN ($Old, $New, $Other) ORDER BY target_type""".query[String].run().toList,
        List(s"$Other:$Other|$New|references", s"$New:$New#1")
      )
      // the columns without a key
      List(
        sql"SELECT citekey FROM events WHERE id = $Event",
        sql"SELECT citekey FROM event_datings WHERE event_id = $Event",
        sql"SELECT citekey FROM event_edges WHERE src_id = $Event",
        sql"SELECT citekey FROM event_participants WHERE event_id = $Event",
        sql"SELECT citekey FROM jobs WHERE id = 'zzrn_job'",
        sql"SELECT citekey FROM place_types WHERE place_id = $Place",
        sql"SELECT citekey FROM place_footprints WHERE place_id = $Place",
        sql"SELECT source FROM places WHERE id = $Place"
      ).foreach(q => assertEquals(q.query[String].run().toList, List(New), q.sqlString))
    assertEquals(
      counts.columns.map(_.table).toSet,
      Set(
        "attestations.target_id (finding)",
        "attestations.target_id (edge, event_dating)",
        "asset_links.target_id (finding)",
        "asset_links.target_id (source)",
        "article_links.target",
        "events.citekey",
        "event_datings.citekey",
        "event_edges.citekey",
        "event_participants.citekey",
        "jobs.citekey",
        "place_types.citekey",
        "place_footprints.citekey",
        "places.source"
      )
    )

    // edges in both directions followed the key; the parked ones waiting for the new name arrived
    assertEquals(
      SourceRepo.neighbors(ds, New).map(n => (n.direction, n.kind, n.citekey)).toSet,
      Set(
        ("out", "challenges", Other),
        ("in", "references", Other),
        ("in", "references", Waiter),
        ("in", "updates", Waiter)
      )
    )
    assertEquals(counts.edgesPromoted, 2)
    assertEquals(count(sql"SELECT count(*)::int FROM unresolved_edges WHERE src = $Waiter"), 0)

    // the article: rewritten through the write path (a version recorded), labels/anchors/escapes kept
    val art = ArticleRepo.load(ds, Slug).get
    assertEquals(
      art.body,
      s"""# Article
         |
         |Exact [[$New]], labelled [[$New|the book]], anchored [[$New#ch-1]], short [[$New]],
         |escaped \\[\\[$New\\]\\], embedded ![[$Old]] and unrelated [[$Other]].""".stripMargin
    )
    assertEquals(art.summary, Some(s"About [[$New]]."))
    assertEquals(ArticleRepo.listVersions(ds, Slug).size, versionsBefore + 1)
    assertEquals(ArticleRepo.getVersion(ds, Slug, ArticleRepo.listVersions(ds, Slug).head.id).map(_.body), Some(body))
    assertEquals(ArticleRepo.backlinksForSource(ds, New).map(_.id), List(Slug))
    assertEquals(ArticleRepo.sourcesCited(ds, Slug).map(_.citekey).toSet, Set(New, Other))
    assertEquals(counts.articles, List(Slug))

    // source notes and summaries, the renamed source's own included; a longer key is left alone
    assertEquals(
      SourceRepo.load(ds, Other).flatMap(_.notes),
      Some(s"See [[$New|Beckman]] and the short form [[$New]], not [[${Old}x]].")
    )
    assertEquals(SourceRepo.load(ds, Other).flatMap(_.summary), Some(s"Cites [[$New#ch-2]]."))
    assertEquals(SourceRepo.load(ds, New).flatMap(_.notes), Some(s"Self link [[$New]]."))
    assertEquals(counts.sources.toSet, Set(New, Other))
    // 5 in the article body (not the embed) + 1 in its summary + 3 in Other + 1 in New
    assertEquals(counts.links, 10)

    // the alias
    assertEquals(SourceRepo.aliases(ds).filter(_.alias.startsWith(Prefix)), List(SourceAlias(Old, New)))
    assertEquals(SourceRepo.canonical(ds, Old), Some(New))
    assertEquals(SourceRepo.canonical(ds, New), Some(New))
    assertEquals(SourceRepo.canonical(ds, "zzrn_nothing"), None)
    assertEquals(SourceRepo.resolveAliases(ds, List(Old, New)), Map(Old -> New, New -> New))

  test("after a rename the old citekey keeps working as a link target, an edge target and a patch target"):
    put(source(Old)); put(source(Other))
    val _ = SourceRepo.rename(ds, Old, New).fold(e => fail(e.message), identity)
    // an article written LATER with the old key links the source, recorded under the new key
    val _ = ArticleRepo.put(ds, Article(Slug, "Zz", "concept", s"Late [[$Old]].", None, Nil, Nil, None))
    assertEquals(ArticleRepo.backlinksForSource(ds, New).map(_.id), List(Slug))
    // an edge written later to the old key is a real edge to the new one, not a parked one
    put(source(Other, edges = List(Edge(Other, Old, "challenges"))))
    assertEquals(
      SourceRepo.neighbors(ds, New).map(n => (n.direction, n.citekey, n.target)),
      Vector(("in", Other, NeighborTarget.Source))
    )
    // a patch by the old key patches the renamed source
    assert(SourceRepo.patch(ds, Old, SourcePatch(summary = Some("patched")), cfg.limits.chunkChars).isRight)
    assertEquals(SourceRepo.load(ds, New).flatMap(_.summary), Some("patched"))
    // …but creating a source under the old key is refused rather than shadowing the alias
    val e = intercept[IllegalStateException](put(source(Old)))
    assert(e.getMessage.contains(s"renamed to '$New'"), e.getMessage)
    // the alias follows a second rename, and renaming back to a key the source had is allowed
    val third = "zzrn1999_book"
    assert(SourceRepo.rename(ds, New, third).isRight)
    assertEquals(SourceRepo.canonical(ds, Old), Some(third))
    assert(SourceRepo.rename(ds, third, Old).isRight, "renaming back must be allowed")
    assertEquals(SourceRepo.canonical(ds, Old), Some(Old))
    assertEquals(
      SourceRepo.aliases(ds).filter(_.alias.startsWith(Prefix)).toSet,
      Set(SourceAlias(New, Old), SourceAlias(third, Old))
    )

  test("renaming back leaves a tombstone for the dropped alias; renaming away again clears it (#827)"):
    put(source(Old))
    def tombstones = connect(ds)(
      sql"""SELECT a.alias, s.citekey FROM alias_tombstones a
                                       JOIN sources s ON s.uid = a.source_uid WHERE a.alias LIKE ${Prefix + "%"}"""
        .query[(String, String)]
        .run()
        .toSet
    )
    assert(SourceRepo.rename(ds, Old, New).isRight)
    assertEquals(tombstones, Set.empty[(String, String)])
    assert(SourceRepo.rename(ds, New, Old).isRight)
    assertEquals(tombstones, Set(Old -> Old), "the alias Old was deleted: its tombstone names the source")
    assertEquals(SourceRepo.aliases(ds).filter(_.alias.startsWith(Prefix)).map(_.alias), List(New))
    assert(SourceRepo.rename(ds, Old, New).isRight)
    assertEquals(tombstones, Set(New -> New), "Old is an alias again; New was dropped by this rename back")

  test("refusals leave everything as it was"):
    put(source(Old, findings = 1)); put(source(Other))
    def refused(from: String, to: String, code: Option[String], says: String): Unit =
      SourceRepo.rename(ds, from, to) match
        case Right(_) => fail(s"$from → $to should be refused")
        case Left(e)  =>
          assertEquals(e.code, code, e.message)
          assert(e.message.contains(says), e.message)
    refused(Old, Old, Some(Write.CodeInvalid), "already the citekey")
    refused(Old, "Not A Key", Some(Write.CodeInvalid), "invalid citekey")
    refused(Old, "zzrn-2011", Some(Write.CodeInvalid), "LINK-shaped")
    refused("zzrn2000_missing", New, Some(Write.CodeNotFound), "zzrn2000_missing")
    refused(Old, Other, Some(Write.CodeInvalid), "already the citekey of another source")
    // a new key that is an OLD key of another source
    assert(SourceRepo.rename(ds, Other, "zzrnb2019_other").isRight)
    refused(Old, Other, Some(Write.CodeInvalid), "old citekey of 'zzrnb2019_other'")
    // renaming an old key names what it points to
    refused(Other, New, Some(Write.CodeInvalid), "rename 'zzrnb2019_other' instead")
    // a finding id under the new name already taken (by a source that broke the convention)
    connect(ds)(
      sql"""INSERT INTO findings (id, citekey, claim) VALUES (${New + "#9"}, 'zzrnb2019_other', 'x')""".update.run()
    )
    refused(Old, New, Some(Write.CodeInvalid), s"finding id '$New#9'")
    // nothing moved
    assertEquals(SourceRepo.get(ds, Old).map(_._1.citekey), Some(Old))
    assertEquals(SourceRepo.aliasOf(ds, Old), None)
