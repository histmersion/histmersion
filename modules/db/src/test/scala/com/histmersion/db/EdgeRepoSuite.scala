package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.citations.CitationFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.Migrate
import com.histmersion.api.library.{Edge, NeighborTarget, Source}
import com.histmersion.api.research.Article
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year

/** `edges` connects stored sources only (V026): the migration's data move, the write-path routing into
  * `unresolved_edges`, promotion when the target arrives, and the graph/neighbors reads built on that invariant.
  * Against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise.
  */
class EdgeRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private val Prefix = "zzedge"

  private def source(ck: String, edges: List[Edge] = Nil, topics: List[Source.Topics] = Nil) = Source(
    citekey = Citekey(ck),
    title = com.histmersion.api.library.Title(s"Title $ck"),
    authors = (List("A")).map(com.histmersion.api.library.AuthorName.apply),
    doi = (Some(s"10.1/$ck")).map(Doi.apply),
    year = (Some(2020)).map(Year.apply),
    venue = None,
    status = Source.Status.Current,
    topics = topics,
    regions = Nil,
    tags = Nil,
    period = None,
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    edges = edges
  )

  private def put(s: Source): Unit = SourceFixtures.upsert(ds, s, cfg.limits.chunkChars)

  override def beforeEach(context: BeforeEach): Unit =
    connect(ds):
      sql"DELETE FROM sources WHERE citekey LIKE ${Prefix + "%"}".update.run()
      sql"DELETE FROM articles WHERE slug LIKE ${Prefix + "%"}".update.run()

  test("an edge to a source not stored yet is parked, listed as unresolved, and promoted when it arrives"):
    val a = s"${Prefix}a2020_x"; val b = s"${Prefix}b2021_y"
    put(source(a, edges = List(Edge(Citekey(a), Citekey(b), Edge.Kind.Challenges))))
    // the domain read still carries it — a patch or an export must not lose it
    assertEquals(
      SourceFixtures.load(ds, com.histmersion.domain.library.Citekey.assume(a)).get.edges,
      List(Edge(Citekey(a), Citekey(b), Edge.Kind.Challenges))
    )
    assertEquals(
      CitationFixtures.neighbors(ds, com.histmersion.domain.library.Citekey.assume(a)).map(n => (n.citekey, n.target)),
      Vector(b -> NeighborTarget.Unresolved)
    )
    put(source(b))
    assertEquals(
      CitationFixtures
        .neighbors(ds, com.histmersion.domain.library.Citekey.assume(a))
        .map(n => (n.direction, n.citekey, n.target, n.title)),
      Vector(("out", b, NeighborTarget.Source, Some(s"Title $b")))
    )
    assertEquals(
      CitationFixtures
        .neighbors(ds, com.histmersion.domain.library.Citekey.assume(b))
        .map(n => (n.direction, n.citekey)),
      Vector("in" -> a)
    )
    val parked = connect(ds)(sql"SELECT count(*)::int FROM unresolved_edges WHERE src = $a".query[Int].run().head)
    assertEquals(parked, 0)

  test("a parked edge naming a compiled article is classified as an article"):
    val a = s"${Prefix}a2020_x"
    val slug = s"${Prefix}_topic"
    val _ = ArticleFixtures.put(
      ds,
      Article(
        Article.Slug(slug),
        Article.Title("A topic"),
        Article.KindValue("concept"),
        Article.Body("# A topic"),
        None,
        Nil,
        Nil,
        None
      )
    )
    put(source(a, edges = List(Edge(Citekey(a), Citekey(slug), Edge.Kind.Updates))))
    assertEquals(
      CitationFixtures
        .neighbors(ds, com.histmersion.domain.library.Citekey.assume(a))
        .map(n => (n.citekey, n.target, n.title)),
      Vector((slug, NeighborTarget.Article, Some("A topic")))
    )
    // `kinds` narrows every branch
    assertEquals(
      CitationFixtures.neighbors(
        ds,
        com.histmersion.domain.library.Citekey.assume(a),
        List(com.histmersion.domain.library.Edge.Kind.Wire("refutes"))
      ),
      Vector.empty
    )

  test("edges/resolve promotes a unique short form and leaves an ambiguous one parked"):
    val a = s"${Prefix}a2020_x"
    put(source(s"${Prefix}c2019_full"))
    put(source(s"${Prefix}d2018_one")); put(source(s"${Prefix}d2018_two"))
    put(
      source(
        a,
        edges = List(
          Edge(Citekey(a), Citekey(s"${Prefix}c2019"), Edge.Kind.References),
          Edge(Citekey(a), Citekey(s"${Prefix}d2018"), Edge.Kind.References)
        )
      )
    )
    assert(SourceFixtures.resolveEdges(ds).value >= 1)
    assertEquals(
      CitationFixtures.neighbors(ds, com.histmersion.domain.library.Citekey.assume(a)).map(n => (n.citekey, n.target)),
      Vector(s"${Prefix}c2019_full" -> NeighborTarget.Source, s"${Prefix}d2018" -> NeighborTarget.Unresolved)
    )

  test("graph: every link joins two nodes of the result, whatever the depth cut-off"):
    val (a, b, c, d) = (s"${Prefix}a2020_x", s"${Prefix}b2021_y", s"${Prefix}c2019_z", s"${Prefix}d2018_w")
    List(b, c, d).foreach(k => put(source(k)))
    put(
      source(
        a,
        edges = List(
          Edge(Citekey(a), Citekey(b), Edge.Kind.References),
          Edge(Citekey(a), Citekey("not_a_source_topic"), Edge.Kind.References)
        )
      )
    )
    put(
      source(
        c,
        edges =
          List(Edge(Citekey(c), Citekey(a), Edge.Kind.Challenges), Edge(Citekey(c), Citekey(d), Edge.Kind.References))
      )
    )
    val g1 = CitationFixtures.graph(ds, com.histmersion.domain.library.Citekey.assume(a), Source.Graph.Depth(1))
    assertEquals(g1.nodes.map(_.id).toSet, Set(a, b, c))
    assertEquals(g1.links.map(l => (l.source, l.target)).toSet, Set(a -> b, c -> a))
    val ids = g1.nodes.map(_.id).toSet
    assert(g1.links.forall(l => ids(l.source) && ids(l.target)), g1)
    val g2 = CitationFixtures.graph(ds, com.histmersion.domain.library.Citekey.assume(a), Source.Graph.Depth(2))
    assertEquals(g2.nodes.map(_.id).toSet, Set(a, b, c, d))
    assertEquals(g2.nodes.find(_.id.value == d).map(_.depth), Some(2))
    assertEquals(
      CitationFixtures.graph(ds, com.histmersion.domain.library.Citekey.assume(a), Source.Graph.Depth(0)).links,
      Nil
    )
