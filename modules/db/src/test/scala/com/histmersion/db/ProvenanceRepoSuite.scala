package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*

/** A retired provenance value (core.Provenance): V097 rewrites the stored rows and JSON copies, and
  * every write path stores the current name. Against a real Postgres (HISTMERSION_TEST_DB_URL);
  * skipped otherwise. */
class ProvenanceRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg              = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d); d

  /** The retired value, spelled as core.Provenance spells it. */
  private val retired = "756e6c6973746564".grouped(2).map(Integer.parseInt(_, 16).toChar).mkString
  private val Prefix  = "zzprov"

  private def source(ck: String, provenance: Option[String]) = Source(
    citekey = ck, title = s"Title $ck", authors = List("A"), doi = None, year = Some(2020),
    venue = None, status = SourceStatus.Current, topics = Nil, regions = Nil, tags = Nil,
    period = None, summary = None, citedByCount = None, influentialCitations = None,
    provenance = provenance, calibreId = None)

  override def beforeEach(context: BeforeEach): Unit =
    connect(ds)(sql"DELETE FROM sources WHERE citekey LIKE ${Prefix + "%"}".update.run())

  test("V097 rewrites the stored provenance and the JSON copies of it, and nothing else"):
    // replayed in a scratch schema holding the columns it touches, so the real script is what runs
    val script = scala.io.Source.fromResource("db/migration/V097__neutral_provenance.sql").mkString
    val schema = "zz_v097_replay"
    connect(ds):
      val con = summon[DbCon].connection
      val st  = con.createStatement()
      try
        st.execute(s"DROP SCHEMA IF EXISTS $schema CASCADE; CREATE SCHEMA $schema; SET search_path TO $schema")
        st.execute(
          s"""CREATE TABLE sources (citekey text PRIMARY KEY, source text);
             CREATE TABLE jobs (id text PRIMARY KEY, params jsonb NOT NULL DEFAULT '{}', detail jsonb, result jsonb);
             CREATE TABLE approvals (id int PRIMARY KEY, proposal jsonb NOT NULL);
             CREATE TABLE agent_steps (id int PRIMARY KEY, would_write jsonb);
             CREATE TABLE merges (id int PRIMARY KEY, report jsonb NOT NULL DEFAULT '{}');
             INSERT INTO sources VALUES ('a', '$retired'), ('b', ' ${retired.toUpperCase} '), ('c', 'calibre'),
               ('d', NULL), ('e', '${retired}_mirror');
             INSERT INTO jobs VALUES
               ('j1', '{"citekey": "a", "provenance": "$retired"}', NULL, '{"metadata": {"provenance": "$retired"}}'),
               ('j2', '{"provenance": "oa", "note": "$retired"}', '{"provenance": "$retired"}', NULL);
             INSERT INTO approvals VALUES (1, '{"metadata": {"provenance": "$retired", "title": "T"}}');
             INSERT INTO agent_steps VALUES (1, '[{"provenance": "$retired"}]'), (2, NULL);
             INSERT INTO merges VALUES (1, '{"sources": [{"provenance": "$retired"}, {"provenance": "manual"}]}');""")
        st.execute(script)
        def rows(q: String): List[String] =
          val rs = st.executeQuery(q)
          Iterator.continually(rs).takeWhile(_.next()).map(r => Option(r.getString(1)).getOrElse("NULL")).toList
        assertEquals(rows("SELECT source FROM sources ORDER BY citekey"),
          List("external-file", "external-file", "calibre", "NULL", s"${retired}_mirror"))
        assertEquals(rows("SELECT params::text FROM jobs ORDER BY id"),
          List("""{"citekey": "a", "provenance": "external-file"}""", s"""{"note": "$retired", "provenance": "oa"}"""),
          "only a provenance member is rewritten")
        assertEquals(rows("SELECT coalesce(detail::text, 'NULL') || ' ' || coalesce(result::text, 'NULL') FROM jobs ORDER BY id"),
          List("""NULL {"metadata": {"provenance": "external-file"}}""", """{"provenance": "external-file"} NULL"""))
        assertEquals(rows("SELECT proposal->'metadata'->>'provenance' FROM approvals"), List("external-file"))
        assertEquals(rows("SELECT coalesce(would_write::text, 'NULL') FROM agent_steps ORDER BY id"),
          List("""[{"provenance": "external-file"}]""", "NULL"))
        assertEquals(rows("SELECT report::text FROM merges"),
          List("""{"sources": [{"provenance": "external-file"}, {"provenance": "manual"}]}"""))
      finally
        st.execute(s"RESET search_path; DROP SCHEMA IF EXISTS $schema CASCADE")

  test("a put and a patch naming the retired value store external-file"):
    val a = s"${Prefix}a2020_x"
    SourceRepo.upsert(ds, source(a, Some(retired)), cfg.limits.chunkChars)
    assertEquals(SourceRepo.load(ds, a).flatMap(_.provenance), Some(Provenance.ExternalFile))
    SourceRepo.upsert(ds, source(a, Some("calibre")), cfg.limits.chunkChars)
    assertEquals(SourceRepo.load(ds, a).flatMap(_.provenance), Some("calibre"), "a current value is kept")
    val patched = SourceRepo.patch(ds, a, SourcePatch(metadata = Some(MetadataPatch(provenance = Some(retired.toUpperCase)))),
      cfg.limits.chunkChars)
    assert(patched.isRight, patched.toString)
    assertEquals(SourceRepo.load(ds, a).flatMap(_.provenance), Some(Provenance.ExternalFile))
    val raw = connect(ds)(sql"SELECT source FROM sources WHERE citekey = $a".query[Option[String]].run().head)
    assertEquals(raw, Some(Provenance.ExternalFile), "the row itself, not only the read")
