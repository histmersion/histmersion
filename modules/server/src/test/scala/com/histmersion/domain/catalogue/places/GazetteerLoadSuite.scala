package com.histmersion
package domain.catalogue.places
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence
import io.scalaland.chimney.dsl.*

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.TestDb
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.{GazetteerPaths, HttpDownload}
import com.histmersion.util.database.Transactor
import com.histmersion.util.storage.{Location, Storage}

import com.augustnagro.magnum.*
import java.nio.file.{Files, Path, StandardCopyOption}
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.db.DbSuite
import com.histmersion.config.UserPaths

/** The external gazetteer sources end to end (#1064): a pinned extract (checksum verified BEFORE anything is read, a
  * mismatch refused, a missing file downloaded through the seam), and the LPF drop folder (a hand-written Linked Places
  * Format file following the spec, an LP-TSV file with a `.meta.json` sidecar). The DB half runs against
  * HISTMERSION_TEST_DB_URL (skipped otherwise); it does not re-materialise `finding_places`, which other suites assert
  * on.
  */
class GazetteerLoadSuite extends DbSuite:
  private val testDb = TestDb.url

  private lazy val base = AppConfig.load()
  private lazy val ds =
    val d = testPool(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private val FixtureSha = Gazetteer.Extract.Sha256("20af2b1dd68f5bce2a5c9ea67bb06862038a7e007eb2d7872a4dfdb763954e5b")

  private val Timeout = GazetteerLoad.Settings.DownloadTimeoutSeconds(5)

  /** Copy a test resource out (resources may live in a jar). */
  private def copyResource(name: String, to: Path): Unit =
    val in = getClass.getResourceAsStream(s"/gazetteer/$name")
    try { val _ = Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING) }
    finally in.close()

  /** A data dir with a manifest pinning the fixture as `geonames`, an extracts dir and an LPF dir. */
  private def layout(
      sha: Gazetteer.Extract.Sha256 = FixtureSha,
      withFile: Boolean = true,
      url: Option[Gazetteer.Extract.Url] = None
  ): GazetteerLoad.Settings =
    val d = Files.createTempDirectory("histmersion-gaz")
    val x = Gazetteer.Extract(
      Gazetteer.Extract.Name("geonames"),
      Gazetteer.Extract.File("geonames-fixture.ndjson.gz"),
      sha,
      Gazetteer.Extract.Bytes(438),
      Gazetteer.Extract.Features(2),
      Gazetteer.Extract.Version("fixture-1"),
      Gazetteer.Extract.Title("GeoNames fixture"),
      Gazetteer.Extract.Licence("CC BY 4.0"),
      Some(Gazetteer.Extract.Attribution("GeoNames (geonames.org)")),
      url
    )
    Files.createDirectories(d.resolve("extracts"))
    Files.writeString(
      d.resolve("extracts.json"),
      io.circe.syntax.EncoderOps(Gazetteer.Manifest(List(x))).asJson.spaces2
    )
    if withFile then copyResource("geonames-fixture.ndjson.gz", d.resolve("extracts/geonames-fixture.ndjson.gz"))
    val lpf = d.resolve("lpf")
    Files.createDirectories(lpf)
    List("zzgaz_hittite.jsonld", "zzgaz_kultepe.tsv", "zzgaz_kultepe.meta.json").foreach(f =>
      copyResource(s"lpf/$f", lpf.resolve(f))
    )
    GazetteerLoad.Settings(
      stores(d.resolve("extracts"), lpf, d.resolve("extracts.json")),
      batch = GazetteerLoad.Settings.Batch(1),
      maxLpfBytes = GazetteerLoad.Settings.MaxLpfBytes(1 << 20),
      downloadTimeoutSeconds = Timeout,
      toleranceKm = GeocodeSettings.ToleranceKm(10),
      rebuild = GazetteerLoad.Settings.Rebuild(false)
    )

  /** The stores over the directories, the manifest read from its file. */
  private def stores(extracts: Path, lpf: Path, manifest: Path, legacy: Option[Path] = None): GazetteerLoad.Stores =
    GazetteerLoad.Stores(
      Storage.at(Location.GazetteerExtracts, extracts),
      legacy.map(Storage.at(Location.GazetteerFolder, _)),
      Storage.at(Location.GazetteerDrop, lpf),
      GazetteerLoad.Stores.ManifestAt(manifest.toString),
      () => GazetteerPaths(extracts, lpf, manifest).manifestText()
    )

  private def extractsOf(s: GazetteerLoad.Settings): Path = Path.of(s.stores.extracts.where().value)
  private def manifestOf(s: GazetteerLoad.Settings): Path = Path.of(s.stores.manifestAt.value)

  private val noNet: GazetteerLoad.Download = (u, _, _) =>
    Left(Problem.Unclassified(Legacy.Text(s"no network in a test ($u)")))
  private lazy val db = Transactor(ds)

  /** A download that writes `bytes` where it is told to. */
  private def serving(bytes: => Array[Byte]): GazetteerLoad.Download = (_, into, _) =>
    val out = into.output()
    try out.write(bytes)
    finally out.close()
    Right(())

  private def resourceBytes(name: String): Array[Byte] =
    val in = getClass.getResourceAsStream(s"/gazetteer/$name")
    try in.readAllBytes()
    finally in.close()

  // ── no database ──────────────────────────────────────────────────────────────────────────────

  test("a checksum mismatch is refused before anything is read"):
    val s = layout(sha = Gazetteer.Extract.Sha256("0" * 64))
    val r = GazetteerLoad.extract(s.stores, GazetteerLoad.manifest(s.stores).toOption.get.extracts.head, noNet, Timeout)
    assert(r.left.exists(_.sentence.contains("refused")), r.toString)

  test("a missing extract without a url says where to put it; with one it is downloaded and verified"):
    val bare = layout(withFile = false)
    val x0 = GazetteerLoad.manifest(bare.stores).toOption.get.extracts.head
    val why = GazetteerLoad.extract(bare.stores, x0, noNet, Timeout).left.getOrElse(fail("a missing file"))
    val at = extractsOf(bare).resolve(x0.file.value).toString
    assert(
      why.sentence.contains(s"is not at $at") && why.sentence.contains("no download url") && why.sentence.contains(
        FixtureSha.value
      ) &&
        why.sentence.contains("places.sources.dir"),
      s"names the exact path, the checksum and the setting: ${why.sentence}"
    )
    val net =
      layout(withFile = false, url = Some(Gazetteer.Extract.Url("https://example.org/geonames-fixture.ndjson.gz")))
    val x1 = GazetteerLoad.manifest(net.stores).toOption.get.extracts.head
    val fetchOk = serving(resourceBytes("geonames-fixture.ndjson.gz"))
    assertEquals(
      GazetteerLoad.extract(net.stores, x1, fetchOk, Timeout).map(_.key.name),
      Right("geonames-fixture.ndjson.gz")
    )
    // a download that is not the pinned file is refused and removed
    val wrong = layout(withFile = false, url = Some(Gazetteer.Extract.Url("https://example.org/x")))
    val x2 = GazetteerLoad.manifest(wrong.stores).toOption.get.extracts.head
    val fetchBad = serving("not it".getBytes(java.nio.charset.StandardCharsets.UTF_8))
    assert(GazetteerLoad.extract(wrong.stores, x2, fetchBad, Timeout).left.exists(_.sentence.contains("refused")))
    assert(!Files.exists(extractsOf(wrong).resolve(x2.file.value)))

  test("a pin that is not built yet (no sha256) is named as such, never downloaded"):
    val s = layout(sha = Gazetteer.Extract.Sha256(""), url = Some(Gazetteer.Extract.Url("https://example.org/x")))
    val x = GazetteerLoad.manifest(s.stores).toOption.get.extracts.head
    val r = GazetteerLoad.extract(s.stores, x, (_, _, _) => fail("no download for an unbuilt pin"), Timeout)
    assert(r.left.exists(_.sentence.contains("not pinned yet")), r.toString)

  test("the extracts default to the XDG cache directory; the LPF folder stays in the data directory"):
    val cfg = base
      .modify(_.places)
      .setTo(
        base.places
          .modify(_.sources)
          .setTo(
            base.places.sources
              .modify(_.dir)
              .setTo(PlaceSourcesConfig.Dir(""))
              .modify(_.lpfDir)
              .setTo(PlaceSourcesConfig.LpfDir(""))
          )
      )
      .modify(_.runtime)
      .setTo(base.runtime.modify(_.dataDir).setTo(RuntimeConfig.DataDir("/data")))
    val p = GazetteerPaths.of(
      cfg,
      Environment(
        Map(
          Environment.VariablesKey("HOME") -> Environment.VariablesValue("/home/u"),
          Environment.VariablesKey("XDG_CACHE_HOME") -> Environment.VariablesValue("/xc")
        )
      ).lookup,
      UserPaths.OsName("Linux")
    )
    assertEquals(p.extracts, Path.of("/xc/histmersion/gazetteer"))
    assertEquals(p.lpf, Path.of("/data/gazetteer/lpf"))
    assertEquals(p.legacy, Some(Path.of("/data/gazetteer")), "the old default, adopted from")
    val own = GazetteerPaths.of(
      cfg
        .modify(_.places)
        .setTo(
          cfg.places.modify(_.sources).setTo(cfg.places.sources.modify(_.dir).setTo(PlaceSourcesConfig.Dir("/mine")))
        ),
      Environment(Map(Environment.VariablesKey("HOME") -> Environment.VariablesValue("/home/u"))).lookup,
      UserPaths.OsName("Linux")
    )
    assertEquals(own.extracts, Path.of("/mine"), "places.sources.dir still overrides")

  test("an extract at the old default place is verified and moved over once"):
    val s = layout(withFile = false)
    val legacy = Files.createTempDirectory("histmersion-gaz-legacy")
    copyResource("geonames-fixture.ndjson.gz", legacy.resolve("geonames-fixture.ndjson.gz"))
    val p = s.stores.modify(_.legacy).setTo(Some(Storage.at(Location.GazetteerFolder, legacy)))
    val x = GazetteerLoad.manifest(p).toOption.get.extracts.head
    val moved = extractsOf(s).resolve(x.file.value).toString
    assertEquals(GazetteerLoad.extract(p, x, noNet, Timeout).map(_.where), Right(moved))
    assert(!Files.exists(legacy.resolve(x.file.value)), "moved, not copied")
    assertEquals(GazetteerLoad.extract(p, x, noNet, Timeout).map(_.where), Right(moved), "found in place next time")
    // a legacy file that is not the pinned one is refused and left where it is
    val bad = Files.createTempDirectory("histmersion-gaz-legacy")
    Files.writeString(bad.resolve(x.file.value), "not it")
    val q = layout(withFile = false).stores.copy(legacy = Some(Storage.at(Location.GazetteerFolder, bad)))
    assert(GazetteerLoad.extract(q, x, noNet, Timeout).left.exists(_.sentence.contains("refused")))
    assert(Files.exists(bad.resolve(x.file.value)))

  test("the committed manifest decodes; every extract is pinned with a checksum"):
    val root = Iterator
      .iterate(Path.of(sys.props("user.dir")).toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .find(d => Files.isRegularFile(d.resolve("db/gazetteer/extracts.json")))
      .getOrElse(fail("no db/gazetteer above"))
    val p = stores(Files.createTempDirectory("gaz"), Path.of("/nowhere"), root.resolve("db/gazetteer/extracts.json"))
    val m = GazetteerLoad.manifest(p).fold(e => fail(e.sentence), identity)
    assertEquals(m.extracts.map(_.name), List("geonames", "tgn", "wikidata"))
    assert(m.extracts.forall(x => Gazetteer.Kind.parse(Gazetteer.Kind.Wire(x.name.value)).isDefined))
    val tgn = m.extracts.find(_.name == Gazetteer.Extract.Name("tgn")).get
    assertEquals(tgn.licence, "ODC-By 1.0")
    assert(m.extracts.forall(x => x.sha256.value.matches("[0-9a-f]{64}") && x.file.value.nonEmpty), m.extracts.toString)
    // not on disk here: the error names the file to put in place, not "not pinned yet"
    assert(
      GazetteerLoad
        .extract(p, tgn, noNet, Timeout)
        .left
        .exists(e => e.sentence.contains(tgn.file.value) && !e.sentence.contains("not pinned yet"))
    )

  test("the LPF drop folder lists data files only, never a sidecar"):
    val s = layout()
    assertEquals(
      GazetteerLoad.lpfFiles(s.stores.lpf).map(_.key.name),
      List("zzgaz_hittite.jsonld", "zzgaz_kultepe.tsv")
    )

  // ── the database ─────────────────────────────────────────────────────────────────────────────

  private def clean(): Unit = if testDb.nonEmpty then
    connect(ds):
      val _ =
        sql"DELETE FROM places WHERE import_source IN ('geonames', 'tgn', 'lpf:zzgaz_hittite', 'lpf:zzgaz_kultepe')".update
          .run()
      val _ =
        sql"DELETE FROM place_names WHERE import_source IN ('geonames', 'tgn', 'lpf:zzgaz_hittite', 'lpf:zzgaz_kultepe')".update
          .run()
      val _ =
        sql"DELETE FROM place_links WHERE import_source IN ('geonames', 'tgn', 'lpf:zzgaz_hittite', 'lpf:zzgaz_kultepe')".update
          .run()
      val _ =
        sql"DELETE FROM gazetteer_sources WHERE key IN ('geonames', 'tgn', 'lpf:zzgaz_hittite', 'lpf:zzgaz_kultepe')".update
          .run()

  override def beforeAll(): Unit = clean()
  override def afterSuite(): Unit = clean()

  private def dbTest(name: String)(body: => Unit): Unit =
    test(name)(if testDb.isEmpty then () else { clean(); body })

  dbTest(
    "LPF then GeoNames: dataset provenance, open-ended dated names, and a GeoNames record joining by a shared Wikidata id"
  ):
    val s = layout()
    val lpf = GazetteerLoad
      .load(db, s, Gazetteer.Kind.Lpf, apply = GazetteerLoad.LoadApply(true), _ => (), noNet)
      .fold(e => fail(e.sentence), identity)
    assertEquals(lpf.map(c => c.source -> c.created), List("lpf:zzgaz_hittite" -> 2L, "lpf:zzgaz_kultepe" -> 1L))
    val rows = PlaceFixtures.gazetteerSources(ds).filter(_.key.value.startsWith("lpf:zzgaz")).map(r => r.key -> r).toMap
    val hit = rows(GazetteerSourceRow.Key("lpf:zzgaz_hittite"))
    assertEquals(hit.title, "Zzgaz Hittite capitals (test fixture)")
    assertEquals(hit.licence, Some("CC BY 4.0"))
    assert(hit.citation.exists(_.value.startsWith("Fixture, A. (2026)")))
    assertEquals((hit.periodStart, hit.periodEnd), (Some(-1650), Some(-1180)), "the records' widest years")
    assertEquals(rows(GazetteerSourceRow.Key("lpf:zzgaz_kultepe")).title, "Zzgaz Kültepe (test fixture)")
    assertEquals(
      rows(GazetteerSourceRow.Key("lpf:zzgaz_kultepe")).citation,
      Some("Fixture, B. (2026). Kültepe LP-TSV.")
    )
    val hattusa = "lpf_zzgaz_hittite_hattusa"
    val (lo, hi, cite) = connect(ds)(
      sql"""SELECT lower(period), upper(period) - 1, citation FROM place_names
                                           WHERE place_id = $hattusa AND name = 'Zzgaz Ḫattuša'"""
        .query[(Option[Long], Option[Long], Option[String])]
        .run()
        .head
    )
    assertEquals((lo, hi, cite), (Some(-1650L), Some(-1180L), Some("Bryce, The Kingdom of the Hittites (2005)")))
    val tr = connect(ds)(
      sql"SELECT lower(period), upper(period) - 1 FROM place_names WHERE place_id = $hattusa AND name = 'Zzgaz Boğazköy'"
        .query[(Option[Long], Option[Long])]
        .run()
        .head
    )
    assertEquals(tr, (None, Some(1937L)), "a name used 'until 1937' keeps its open start")

    // GeoNames 999000202 names wd:Q999000101, which the LPF record links: its names go to the LPF place
    val gn =
      GazetteerLoad
        .load(db, s, Gazetteer.Kind.Geonames, apply = GazetteerLoad.LoadApply(true), _ => (), noNet)
        .fold(e => fail(e.sentence), identity)
        .head
    assertEquals((gn.records, gn.created, gn.joined), (2L, 1L, 1L))
    val names = connect(ds)(
      sql"SELECT name FROM place_names WHERE place_id = $hattusa AND import_source = 'geonames' ORDER BY name"
        .query[String]
        .run()
        .toList
    )
    assertEquals(names, List("Zzgaz Mikoyan", "Zzgaz Yeghegnadzor"))
    assertEquals(
      PlaceFixtures
        .gazetteerSources(ds)
        .find(_.key == GazetteerSourceRow.Key("geonames"))
        .map(r => (r.version, r.sha256.value: String)),
      Some(("fixture-1", FixtureSha))
    )

    // idempotent: the same versions again change nothing
    val again = GazetteerLoad
      .load(db, s, Gazetteer.Kind.Lpf, apply = GazetteerLoad.LoadApply(true), _ => (), noNet)
      .toOption
      .get ++
      GazetteerLoad
        .load(db, s, Gazetteer.Kind.Geonames, apply = GazetteerLoad.LoadApply(true), _ => (), noNet)
        .toOption
        .get
    assert(again.forall(_.unchanged), again.map(_.describe).mkString("; "))

  private val TgnSha = Gazetteer.Extract.Sha256("2c386aa87210da40fc3bcba558ebfd99ff6fa3924c4139f139212095882c4384")

  /** [[layout]] with a `tgn` extract pinned too. */
  private def withTgn(s: GazetteerLoad.Settings): GazetteerLoad.Settings =
    copyResource("tgn-fixture.ndjson.gz", extractsOf(s).resolve("tgn-fixture.ndjson.gz"))
    val m = GazetteerLoad.manifest(s.stores).toOption.get
    val x = Gazetteer.Extract(
      Gazetteer.Extract.Name("tgn"),
      Gazetteer.Extract.File("tgn-fixture.ndjson.gz"),
      TgnSha,
      Gazetteer.Extract.Bytes(449),
      Gazetteer.Extract.Features(3),
      Gazetteer.Extract.Version("fixture-t"),
      Gazetteer.Extract.Title("Getty TGN fixture"),
      Gazetteer.Extract.Licence("ODC-By 1.0"),
      Some(Gazetteer.Extract.Attribution("Getty Thesaurus of Geographic Names (TGN), ODC-By 1.0"))
    )
    Files.writeString(manifestOf(s), (Gazetteer.Manifest(m.extracts :+ x)).asJson.spaces2)
    s

  dbTest("Getty TGN (#1403): joins by a shared tgn id only, keeps open name spans, drops a point-only region"):
    val s = withTgn(layout())
    val _ = GazetteerLoad
      .load(db, s, Gazetteer.Kind.Lpf, apply = GazetteerLoad.LoadApply(true), _ => (), noNet)
      .fold(e => fail(e.sentence), identity)
    val hattusa = "lpf_zzgaz_hittite_hattusa"
    // what the Wikidata extract does for 25 753 items: a tgn: link on a place (here, by hand)
    connect(ds)(sql"""INSERT INTO place_links (place_id, scheme, external_id, relation)
                      VALUES ($hattusa, 'tgn', '999000301', 'close_match')""".update.run())
    val c =
      GazetteerLoad
        .load(db, s, Gazetteer.Kind.Tgn, apply = GazetteerLoad.LoadApply(true), _ => (), noNet)
        .fold(e => fail(e.sentence), identity)
        .head
    assertEquals((c.source, c.records, c.joined, c.created, c.pointAreasSkipped), ("tgn", 3L, 1L, 1L, 1L), c.describe)
    val span = connect(ds)(
      sql"""SELECT lower(period), upper(period) - 1 FROM place_names
                                 WHERE place_id = $hattusa AND import_source = 'tgn' AND name = 'Zzgaz Hattusa (TGN)'"""
        .query[(Option[Long], Option[Long])]
        .run()
        .head
    )
    assertEquals(span, (Some(-1650L), None), "a name used from 1650 BCE on stays open")
    val own = connect(ds)(sql"""SELECT import_source, lower_inf(period) AND upper_inf(period) FROM places
                                WHERE id = 'tgn_999000302'""".query[(String, Boolean)].run().toList)
    assertEquals(own, List(("tgn", true)), "a record no place names becomes tgn_<id>, undated")
    assertEquals(
      connect(ds)(sql"SELECT count(*)::int FROM places WHERE id = 'tgn_999000303'".query[Int].run().head),
      0,
      "a TGN region known only by a point is no place"
    )
    val src =
      PlaceFixtures.gazetteerSources(ds).find(_.key == GazetteerSourceRow.Key("tgn")).getOrElse(fail("recorded"))
    assertEquals((src.kind, src.version, src.licence), ("tgn", "fixture-t", Some("ODC-By 1.0")))
    val desc = connect(ds)(
      sql"SELECT text FROM place_descriptions WHERE place_id = $hattusa AND import_source = 'tgn'"
        .query[String]
        .run()
        .toList
    )
    assertEquals(desc, List("Capital of the Hittites."))
    val again = GazetteerLoad
      .load(db, s, Gazetteer.Kind.Tgn, apply = GazetteerLoad.LoadApply(true), _ => (), noNet)
      .fold(e => fail(e.sentence), identity)
    assert(again.forall(_.unchanged), again.map(_.describe).mkString("; "))

  dbTest("a dry run counts and keeps nothing"):
    val s = layout()
    val c =
      GazetteerLoad
        .load(db, s, Gazetteer.Kind.Geonames, apply = GazetteerLoad.LoadApply(false), _ => (), noNet)
        .fold(e => fail(e.sentence), identity)
        .head
    assertEquals(c.created, 2L)
    assertEquals(
      connect(ds)(sql"SELECT count(*)::int FROM places WHERE import_source = 'geonames'".query[Int].run().head),
      0
    )
    assert(!PlaceFixtures.gazetteerSources(ds).exists(_.key == GazetteerSourceRow.Key("geonames")))
