package com.histmersion.server.importers

import com.histmersion.core.*
import com.histmersion.db.{Db, GazetteerRepo, Migrate, TestDb}

import com.augustnagro.magnum.*
import java.nio.file.{Files, Path, StandardCopyOption}

/** The external gazetteer sources end to end (#1064): a pinned extract (checksum verified BEFORE
  * anything is read, a mismatch refused, a missing file downloaded through the seam), and the LPF
  * drop folder (a hand-written Linked Places Format file following the spec, an LP-TSV file with a
  * `.meta.json` sidecar). The DB half runs against HISTMERSION_TEST_DB_URL (skipped otherwise); it
  * does not re-materialise `finding_places`, which other suites assert on. */
class GazetteerLoadSuite extends munit.FunSuite:
  private val testDb = TestDb.url

  private lazy val base = AppConfig.load()
  private lazy val ds =
    val d = Db.dataSource(base.db.copy(url = testDb.get))
    Migrate.run(d); d

  private val FixtureSha = "20af2b1dd68f5bce2a5c9ea67bb06862038a7e007eb2d7872a4dfdb763954e5b"

  /** Copy a test resource out (resources may live in a jar). */
  private def copyResource(name: String, to: Path): Unit =
    val in = getClass.getResourceAsStream(s"/gazetteer/$name")
    try { val _ = Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING) } finally in.close()

  /** A data dir with a manifest pinning the fixture as `geonames`, an extracts dir and an LPF dir. */
  private def layout(sha: String = FixtureSha, withFile: Boolean = true, url: Option[String] = None): GazetteerLoad.Settings =
    val d = Files.createTempDirectory("histmersion-gaz")
    val x = GazetteerExtract("geonames", "geonames-fixture.ndjson.gz", sha, 438, 2, "fixture-1", "GeoNames fixture",
      "CC BY 4.0", Some("GeoNames (geonames.org)"), url)
    Files.createDirectories(d.resolve("extracts"))
    Files.writeString(d.resolve("extracts.json"), io.circe.syntax.EncoderOps(GazetteerManifest(List(x))).asJson.spaces2)
    if withFile then
      copyResource("geonames-fixture.ndjson.gz", d.resolve("extracts/geonames-fixture.ndjson.gz"))
    val lpf = d.resolve("lpf")
    Files.createDirectories(lpf)
    List("zzgaz_hittite.jsonld", "zzgaz_kultepe.tsv", "zzgaz_kultepe.meta.json").foreach(f =>
      copyResource(s"lpf/$f", lpf.resolve(f)))
    GazetteerLoad.Settings(GazetteerLoad.Paths(d.resolve("extracts"), lpf, d.resolve("extracts.json")),
      batch = 1, maxLpfBytes = 1 << 20, downloadTimeoutSeconds = 5, toleranceKm = 10, rebuild = false)

  private val noNet: GazetteerLoad.Download = (u, _, _) => Left(s"no network in a test ($u)")

  // ── no database ──────────────────────────────────────────────────────────────────────────────

  test("a checksum mismatch is refused before anything is read"):
    val s = layout(sha = "0" * 64)
    val r = GazetteerLoad.extract(s.paths, GazetteerLoad.manifest(s.paths).toOption.get.extracts.head, noNet, 5)
    assert(r.left.exists(_.contains("refused")), r.toString)

  test("a missing extract without a url says where to put it; with one it is downloaded and verified"):
    val bare = layout(withFile = false)
    val x0   = GazetteerLoad.manifest(bare.paths).toOption.get.extracts.head
    val why  = GazetteerLoad.extract(bare.paths, x0, noNet, 5).left.getOrElse(fail("a missing file"))
    val at   = bare.paths.extracts.resolve(x0.file).toString
    assert(why.contains(s"is not at $at") && why.contains("no download url") && why.contains(FixtureSha) &&
      why.contains("places.sources.dir"), s"names the exact path, the checksum and the setting: $why")
    val net = layout(withFile = false, url = Some("https://example.org/geonames-fixture.ndjson.gz"))
    val x1  = GazetteerLoad.manifest(net.paths).toOption.get.extracts.head
    val fetchOk: GazetteerLoad.Download = (_, to, _) =>
      copyResource("geonames-fixture.ndjson.gz", to); Right(())
    assertEquals(GazetteerLoad.extract(net.paths, x1, fetchOk, 5).map(_.getFileName.toString), Right("geonames-fixture.ndjson.gz"))
    // a download that is not the pinned file is refused and removed
    val wrong = layout(withFile = false, url = Some("https://example.org/x"))
    val x2 = GazetteerLoad.manifest(wrong.paths).toOption.get.extracts.head
    val fetchBad: GazetteerLoad.Download = (_, to, _) => { Files.writeString(to, "not it"); Right(()) }
    assert(GazetteerLoad.extract(wrong.paths, x2, fetchBad, 5).left.exists(_.contains("refused")))
    assert(!Files.exists(wrong.paths.extracts.resolve(x2.file)))

  test("a pin that is not built yet (no sha256) is named as such, never downloaded"):
    val s = layout(sha = "", url = Some("https://example.org/x"))
    val x = GazetteerLoad.manifest(s.paths).toOption.get.extracts.head
    val r = GazetteerLoad.extract(s.paths, x, (_, _, _) => fail("no download for an unbuilt pin"), 5)
    assert(r.left.exists(_.contains("not pinned yet")), r.toString)

  test("the extracts default to the XDG cache directory; the LPF folder stays in the data directory"):
    val cfg = base.copy(places = base.places.copy(sources = base.places.sources.copy(dir = "", lpfDir = "")),
      runtime = base.runtime.copy(dataDir = "/data"))
    val p = GazetteerLoad.paths(cfg, Map("HOME" -> "/home/u", "XDG_CACHE_HOME" -> "/xc").get)
    assertEquals(p.extracts, Path.of("/xc/histmersion/gazetteer"))
    assertEquals(p.lpf, Path.of("/data/gazetteer/lpf"))
    assertEquals(p.legacy, Some(Path.of("/data/gazetteer")), "the old default, adopted from")
    val own = GazetteerLoad.paths(cfg.copy(places = cfg.places.copy(sources = cfg.places.sources.copy(dir = "/mine"))),
      Map("HOME" -> "/home/u").get)
    assertEquals(own.extracts, Path.of("/mine"), "places.sources.dir still overrides")

  test("an extract at the old default place is verified and moved over once"):
    val s      = layout(withFile = false)
    val legacy = Files.createTempDirectory("histmersion-gaz-legacy")
    copyResource("geonames-fixture.ndjson.gz", legacy.resolve("geonames-fixture.ndjson.gz"))
    val p = s.paths.copy(legacy = Some(legacy))
    val x = GazetteerLoad.manifest(p).toOption.get.extracts.head
    assertEquals(GazetteerLoad.extract(p, x, noNet, 5), Right(p.extracts.resolve(x.file)))
    assert(!Files.exists(legacy.resolve(x.file)), "moved, not copied")
    assertEquals(GazetteerLoad.extract(p, x, noNet, 5), Right(p.extracts.resolve(x.file)), "found in place next time")
    // a legacy file that is not the pinned one is refused and left where it is
    val bad = Files.createTempDirectory("histmersion-gaz-legacy")
    Files.writeString(bad.resolve(x.file), "not it")
    val q = layout(withFile = false).paths.copy(legacy = Some(bad))
    assert(GazetteerLoad.extract(q, x, noNet, 5).left.exists(_.contains("refused")))
    assert(Files.exists(bad.resolve(x.file)))

  test("the LPF drop folder lists data files only, never a sidecar"):
    val s = layout()
    assertEquals(GazetteerLoad.lpfFiles(s.paths.lpf).map(_.getFileName.toString), List("zzgaz_hittite.jsonld", "zzgaz_kultepe.tsv"))

  // ── the database ─────────────────────────────────────────────────────────────────────────────

  private def clean(): Unit = if testDb.nonEmpty then connect(ds):
    val _ = sql"DELETE FROM places WHERE import_source IN ('geonames', 'tgn', 'lpf:zzgaz_hittite', 'lpf:zzgaz_kultepe')".update.run()
    val _ = sql"DELETE FROM place_names WHERE import_source IN ('geonames', 'tgn', 'lpf:zzgaz_hittite', 'lpf:zzgaz_kultepe')".update.run()
    val _ = sql"DELETE FROM place_links WHERE import_source IN ('geonames', 'tgn', 'lpf:zzgaz_hittite', 'lpf:zzgaz_kultepe')".update.run()
    val _ = sql"DELETE FROM gazetteer_sources WHERE key IN ('geonames', 'tgn', 'lpf:zzgaz_hittite', 'lpf:zzgaz_kultepe')".update.run()

  override def beforeAll(): Unit = clean()
  override def afterAll(): Unit  = clean()

  private def dbTest(name: String)(body: => Unit): Unit =
    test(name)(if testDb.isEmpty then () else { clean(); body })

  dbTest("LPF then GeoNames: dataset provenance, open-ended dated names, and a GeoNames record joining by a shared Wikidata id"):
    val s = layout()
    val lpf = GazetteerLoad.load(ds, s, GazetteerKind.Lpf, apply = true, _ => ()).fold(e => fail(e), identity)
    assertEquals(lpf.map(c => c.source -> c.created), List("lpf:zzgaz_hittite" -> 2L, "lpf:zzgaz_kultepe" -> 1L))
    val rows = GazetteerRepo.sources(ds).filter(_.key.startsWith("lpf:zzgaz")).map(r => r.key -> r).toMap
    val hit = rows("lpf:zzgaz_hittite")
    assertEquals(hit.title, "Zzgaz Hittite capitals (test fixture)")
    assertEquals(hit.licence, Some("CC BY 4.0"))
    assert(hit.citation.exists(_.startsWith("Fixture, A. (2026)")))
    assertEquals((hit.periodStart, hit.periodEnd), (Some(-1650L), Some(-1180L)), "the records' widest years")
    assertEquals(rows("lpf:zzgaz_kultepe").title, "Zzgaz Kültepe (test fixture)")
    assertEquals(rows("lpf:zzgaz_kultepe").citation, Some("Fixture, B. (2026). Kültepe LP-TSV."))
    val hattusa = "lpf_zzgaz_hittite_hattusa"
    val (lo, hi, cite) = connect(ds)(sql"""SELECT lower(period), upper(period) - 1, citation FROM place_names
                                           WHERE place_id = $hattusa AND name = 'Zzgaz Ḫattuša'"""
      .query[(Option[Long], Option[Long], Option[String])].run().head)
    assertEquals((lo, hi, cite), (Some(-1650L), Some(-1180L), Some("Bryce, The Kingdom of the Hittites (2005)")))
    val tr = connect(ds)(sql"SELECT lower(period), upper(period) - 1 FROM place_names WHERE place_id = $hattusa AND name = 'Zzgaz Boğazköy'"
      .query[(Option[Long], Option[Long])].run().head)
    assertEquals(tr, (None, Some(1937L)), "a name used 'until 1937' keeps its open start")

    // GeoNames 999000202 names wd:Q999000101, which the LPF record links: its names go to the LPF place
    val gn = GazetteerLoad.load(ds, s, GazetteerKind.Geonames, apply = true, _ => ()).fold(e => fail(e), identity).head
    assertEquals((gn.records, gn.created, gn.joined), (2L, 1L, 1L))
    val names = connect(ds)(sql"SELECT name FROM place_names WHERE place_id = $hattusa AND import_source = 'geonames' ORDER BY name"
      .query[String].run().toList)
    assertEquals(names, List("Zzgaz Mikoyan", "Zzgaz Yeghegnadzor"))
    assertEquals(GazetteerRepo.sources(ds).find(_.key == "geonames").map(r => (r.version, r.sha256)),
      Some(("fixture-1", FixtureSha)))

    // idempotent: the same versions again change nothing
    val again = GazetteerLoad.load(ds, s, GazetteerKind.Lpf, apply = true, _ => ()).toOption.get ++
      GazetteerLoad.load(ds, s, GazetteerKind.Geonames, apply = true, _ => ()).toOption.get
    assert(again.forall(_.unchanged), again.map(_.describe).mkString("; "))

  private val TgnSha = "2c386aa87210da40fc3bcba558ebfd99ff6fa3924c4139f139212095882c4384"

  /** [[layout]] with a `tgn` extract pinned too. */
  private def withTgn(s: GazetteerLoad.Settings): GazetteerLoad.Settings =
    copyResource("tgn-fixture.ndjson.gz", s.paths.extracts.resolve("tgn-fixture.ndjson.gz"))
    val m = GazetteerLoad.manifest(s.paths).toOption.get
    val x = GazetteerExtract("tgn", "tgn-fixture.ndjson.gz", TgnSha, 449, 3, "fixture-t", "Getty TGN fixture",
      "ODC-By 1.0", Some("Getty Thesaurus of Geographic Names (TGN), ODC-By 1.0"))
    Files.writeString(s.paths.manifest, io.circe.syntax.EncoderOps(GazetteerManifest(m.extracts :+ x)).asJson.spaces2)
    s

  dbTest("Getty TGN (#1403): joins by a shared tgn id only, keeps open name spans, drops a point-only region"):
    val s = withTgn(layout())
    val _ = GazetteerLoad.load(ds, s, GazetteerKind.Lpf, apply = true, _ => ()).fold(e => fail(e), identity)
    val hattusa = "lpf_zzgaz_hittite_hattusa"
    // what the Wikidata extract does for 25 753 items: a tgn: link on a place (here, by hand)
    connect(ds)(sql"""INSERT INTO place_links (place_id, scheme, external_id, relation)
                      VALUES ($hattusa, 'tgn', '999000301', 'close_match')""".update.run())
    val c = GazetteerLoad.load(ds, s, GazetteerKind.Tgn, apply = true, _ => ()).fold(e => fail(e), identity).head
    assertEquals((c.source, c.records, c.joined, c.created, c.pointAreasSkipped), ("tgn", 3L, 1L, 1L, 1L), c.describe)
    val span = connect(ds)(sql"""SELECT lower(period), upper(period) - 1 FROM place_names
                                 WHERE place_id = $hattusa AND import_source = 'tgn' AND name = 'Zzgaz Hattusa (TGN)'"""
      .query[(Option[Long], Option[Long])].run().head)
    assertEquals(span, (Some(-1650L), None), "a name used from 1650 BCE on stays open")
    val own = connect(ds)(sql"""SELECT import_source, lower_inf(period) AND upper_inf(period) FROM places
                                WHERE id = 'tgn_999000302'""".query[(String, Boolean)].run().toList)
    assertEquals(own, List(("tgn", true)), "a record no place names becomes tgn_<id>, undated")
    assertEquals(connect(ds)(sql"SELECT count(*)::int FROM places WHERE id = 'tgn_999000303'".query[Int].run().head), 0,
      "a TGN region known only by a point is no place")
    val src = GazetteerRepo.sources(ds).find(_.key == "tgn").getOrElse(fail("recorded"))
    assertEquals((src.kind, src.version, src.licence), ("tgn", "fixture-t", Some("ODC-By 1.0")))
    val desc = connect(ds)(sql"SELECT text FROM place_descriptions WHERE place_id = $hattusa AND import_source = 'tgn'"
      .query[String].run().toList)
    assertEquals(desc, List("Capital of the Hittites."))
    val again = GazetteerLoad.load(ds, s, GazetteerKind.Tgn, apply = true, _ => ()).fold(e => fail(e), identity)
    assert(again.forall(_.unchanged), again.map(_.describe).mkString("; "))

  dbTest("a dry run counts and keeps nothing"):
    val s = layout()
    val c = GazetteerLoad.load(ds, s, GazetteerKind.Geonames, apply = false, _ => ()).fold(e => fail(e), identity).head
    assertEquals(c.created, 2L)
    assertEquals(connect(ds)(sql"SELECT count(*)::int FROM places WHERE import_source = 'geonames'".query[Int].run().head), 0)
    assert(!GazetteerRepo.sources(ds).exists(_.key == "geonames"))
