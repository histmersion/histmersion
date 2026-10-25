package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.values.SourceStatus

/** `edges` connects stored sources only (V026): the migration's data move, the write-path routing
  * into `unresolved_edges`, promotion when the target arrives, and the graph/neighbors reads built on
  * that invariant. Against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. */
class EdgeRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg              = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d); d

  private val Prefix = "zzedge"

  private def source(ck: String, edges: List[Edge] = Nil, topics: List[String] = Nil) = Source(
    citekey = ck, title = s"Title $ck", authors = List("A"), doi = Some(s"10.1/$ck"), year = Some(2020),
    venue = None, status = SourceStatus.Current, topics = topics, regions = Nil, tags = Nil,
    period = None, summary = None, citedByCount = None, influentialCitations = None,
    provenance = None, calibreId = None, edges = edges)

  private def put(s: Source): Unit = SourceRepo.upsert(ds, s, cfg.limits.chunkChars)

  override def beforeEach(context: BeforeEach): Unit =
    connect(ds):
      sql"DELETE FROM sources WHERE citekey LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM articles WHERE slug LIKE ${Prefix + "%"}".update.run()

  test("V026 moves topic-slug references into topics, parks other dangling edges, and adds the FKs"):
    // replayed in a scratch schema holding the PRE-V026 shape, so the real data move is what runs
    val script = scala.io.Source.fromResource("db/migration/V026__edges_between_sources.sql").mkString
    val schema = "zz_v026_replay"
    connect(ds):
      val con = summon[DbCon].connection
      val st  = con.createStatement()
      try
        st.execute(s"DROP SCHEMA IF EXISTS $schema CASCADE; CREATE SCHEMA $schema; SET search_path TO $schema")
        st.execute(
          """CREATE TABLE sources (citekey text PRIMARY KEY, topics text[] NOT NULL DEFAULT '{}');
             CREATE TABLE edges (src text NOT NULL, dst text NOT NULL, kind text NOT NULL,
                                 PRIMARY KEY (src, dst, kind));
             INSERT INTO sources VALUES ('a2020_x', '{Bronze_Age_Collapse}'), ('b2021_y', '{}');
             INSERT INTO edges VALUES
               ('a2020_x', 'b2021_y',             'references'),  -- a real citation: stays
               ('a2020_x', 'bronze_age_collapse', 'references'),  -- a topic already present: dropped
               ('a2020_x', 'sea_peoples',         'references'),  -- a topic: moves into topics
               ('b2021_y', 'sea_peoples',         'references'),
               ('a2020_x', 'c2019_z',             'references'),  -- a work not ingested yet: parked
               ('a2020_x', 'sea_peoples',         'challenges'),  -- typed, dangling: parked, not a topic
               ('ghost',   'a2020_x',             'references')   -- out of a non-source: dropped""")
        st.execute(script)
        def rows(q: String): List[String] =
          val rs = st.executeQuery(q)
          Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1)).toList
        assertEquals(rows("SELECT src || '>' || dst || ':' || kind FROM edges ORDER BY 1"),
          List("a2020_x>b2021_y:references"))
        assertEquals(rows("SELECT src || '>' || dst || ':' || kind FROM unresolved_edges ORDER BY 1"),
          List("a2020_x>c2019_z:references", "a2020_x>sea_peoples:challenges"))
        assertEquals(rows("SELECT array_to_string(topics, ',') FROM sources ORDER BY citekey"),
          List("Bronze_Age_Collapse,sea_peoples", "sea_peoples"))
        // the invariant holds from now on
        intercept[java.sql.SQLException](st.execute("INSERT INTO edges VALUES ('a2020_x', 'nope', 'cites')"))
      finally
        st.execute(s"RESET search_path; DROP SCHEMA IF EXISTS $schema CASCADE")

  test("an edge to a source not stored yet is parked, listed as unresolved, and promoted when it arrives"):
    val a = s"${Prefix}a2020_x"; val b = s"${Prefix}b2021_y"
    put(source(a, edges = List(Edge(a, b, "challenges"))))
    // the domain read still carries it — a patch or an export must not lose it
    assertEquals(SourceRepo.load(ds, a).get.edges, List(Edge(a, b, "challenges")))
    assertEquals(SourceRepo.neighbors(ds, a).map(n => (n.citekey, n.target)),
      Vector(b -> NeighborTarget.Unresolved))
    put(source(b))
    assertEquals(SourceRepo.neighbors(ds, a).map(n => (n.direction, n.citekey, n.target, n.title)),
      Vector(("out", b, NeighborTarget.Source, Some(s"Title $b"))))
    assertEquals(SourceRepo.neighbors(ds, b).map(n => (n.direction, n.citekey)), Vector("in" -> a))
    val parked = connect(ds)(sql"SELECT count(*)::int FROM unresolved_edges WHERE src = $a".query[Int].run().head)
    assertEquals(parked, 0)

  test("a parked edge naming a compiled article is classified as an article"):
    val a    = s"${Prefix}a2020_x"
    val slug = s"${Prefix}_topic"
    val _ = ArticleRepo.put(ds, Article(slug, "A topic", "concept", "# A topic", None, Nil, Nil, None))
    put(source(a, edges = List(Edge(a, slug, "updates"))))
    assertEquals(SourceRepo.neighbors(ds, a).map(n => (n.citekey, n.target, n.title)),
      Vector((slug, NeighborTarget.Article, Some("A topic"))))
    // `kinds` narrows every branch
    assertEquals(SourceRepo.neighbors(ds, a, List("refutes")), Vector.empty)

  test("edges/resolve promotes a unique short form and leaves an ambiguous one parked"):
    val a = s"${Prefix}a2020_x"
    put(source(s"${Prefix}c2019_full"))
    put(source(s"${Prefix}d2018_one")); put(source(s"${Prefix}d2018_two"))
    put(source(a, edges = List(Edge(a, s"${Prefix}c2019", "references"), Edge(a, s"${Prefix}d2018", "references"))))
    assert(SourceRepo.resolveEdges(ds) >= 1)
    assertEquals(SourceRepo.neighbors(ds, a).map(n => (n.citekey, n.target)), Vector(
      s"${Prefix}c2019_full" -> NeighborTarget.Source, s"${Prefix}d2018" -> NeighborTarget.Unresolved))

  test("graph: every link joins two nodes of the result, whatever the depth cut-off"):
    val (a, b, c, d) = (s"${Prefix}a2020_x", s"${Prefix}b2021_y", s"${Prefix}c2019_z", s"${Prefix}d2018_w")
    List(b, c, d).foreach(k => put(source(k)))
    put(source(a, edges = List(Edge(a, b, "references"), Edge(a, "not_a_source_topic", "references"))))
    put(source(c, edges = List(Edge(c, a, "challenges"), Edge(c, d, "references"))))
    val g1 = SourceRepo.graph(ds, a, 1)
    assertEquals(g1.nodes.map(_.id).toSet, Set(a, b, c))
    assertEquals(g1.links.map(l => (l.source, l.target)).toSet, Set(a -> b, c -> a))
    val ids = g1.nodes.map(_.id).toSet
    assert(g1.links.forall(l => ids(l.source) && ids(l.target)), g1)
    val g2 = SourceRepo.graph(ds, a, 2)
    assertEquals(g2.nodes.map(_.id).toSet, Set(a, b, c, d))
    assertEquals(g2.nodes.find(_.id == d).map(_.depth), Some(2))
    assertEquals(SourceRepo.graph(ds, a, 0).links, Nil)
