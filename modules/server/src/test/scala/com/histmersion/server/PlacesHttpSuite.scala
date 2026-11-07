package com.histmersion.server

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.catalogue.{Lpf, Period, Place}
import com.histmersion.api.instance.{About, Version}
import com.histmersion.api.library.CorrespondingSource

/** The gazetteer over real HTTP (plan §1.6a): import a tiny Linked-Places document, then read it back through every
  * route. Gated on HISTMERSION_TEST_DB_URL like the other server suites.
  *
  * Deliberately does NOT call `POST /api/places/rebuild`: that re-materialises `finding_places` for the WHOLE corpus,
  * and the db-module suites (which may run in another JVM against the same test database) assert on it.
  * `PlaceRepoSuite` covers the rebuild.
  */
class PlacesHttpSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val client = java.net.http.HttpClient.newHttpClient()

  private def send(req: java.net.http.HttpRequest) =
    val r = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def get(url: String) =
    send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build())

  private def post(url: String, body: String) =
    send(
      java.net.http.HttpRequest
        .newBuilder(java.net.URI.create(url))
        .header("Content-Type", "application/json")
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
        .build()
    )

  /** Two nested regions and a site, with a relation that is only valid in the Late Bronze Age. The land is dated: an
    * undated region with a shape and no finding is in no view at a time (the time rule, `PlaceTimeSuite`), and the tree
    * must walk through it.
    */
  private val doc = Lpf.render(
    List(
      Place.Input(
        Place(com.histmersion.api.catalogue.Place.Id("zzh_world"), com.histmersion.api.catalogue.Place.Label("Zzh World"), Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region)),
        List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzh World")))
      ),
      Place.Input(
        Place(
          com.histmersion.api.catalogue.Place.Id("zzh_land"),
          com.histmersion.api.catalogue.Place.Label("Zzh Land"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region),
          Some(
            Place.Geometry("""{"type":"Polygon","coordinates":[[[100.0,10.0],[110.0,10.0],[110.0,20.0],[100.0,20.0],[100.0,10.0]]]}""")
          ),
          Some(Period(com.histmersion.api.catalogue.Year(-3000), com.histmersion.api.catalogue.Year(0)))
        ),
        List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzh Land")), Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzhland"), com.histmersion.api.catalogue.Place.Name.Lang("xx"))),
        List(Place.Relation(com.histmersion.api.catalogue.Place.Relation.ChildId("zzh_land"), com.histmersion.api.catalogue.Place.Relation.ParentId("zzh_world")))
      ),
      Place.Input(
        Place(
          com.histmersion.api.catalogue.Place.Id("zzh_polity"),
          com.histmersion.api.catalogue.Place.Label("Zzh Polity"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Polity),
          Some(Place.Geometry("""{"type":"Point","coordinates":[105.0,15.0]}""")),
          Some(Period(com.histmersion.api.catalogue.Year(-1400), com.histmersion.api.catalogue.Year(-1200)))
        ),
        List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzh Polity"))),
        List(Place.Relation(com.histmersion.api.catalogue.Place.Relation.ChildId("zzh_polity"), com.histmersion.api.catalogue.Place.Relation.ParentId("zzh_land"), Place.Relation.Kind(Place.Relation.PartOf), Some(Period(com.histmersion.api.catalogue.Year(-1400), com.histmersion.api.catalogue.Year(-1200)))))
      )
    )
  )

  test("import → search → detail → children → tree, all as of a year"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"

      val (is, ib) = post(s"$url/api/places/import", doc)
      assertEquals(is, 200, ib)
      assert(ib.contains("\"places\":3"), ib)
      assert(ib.contains("\"relations\":2"), ib)

      // search finds a place by an ALTERNATE name and reports the id a `place=` filter wants
      val (ss, sb) = get(s"$url/api/places?q=Zzhland")
      assertEquals(ss, 200, sb)
      assert(sb.contains("\"id\":\"zzh_land\""), sb)
      assert(sb.contains("\"matchedName\":\"Zzhland\""), sb)

      // detail: geometry comes back as GeoJSON TEXT, parents/children as of the asked-for year
      val (ds, db) = get(s"$url/api/places/zzh_land?asOf=-1300")
      assertEquals(ds, 200, db)
      assert(db.contains("\\\"Polygon\\\""), db)
      assert(db.contains("\"zzh_world\""), db)
      assert(db.contains("\"zzh_polity\""), db)

      // …but the polity edge is out of validity in the Iron Age
      val (_, iron) = get(s"$url/api/places/zzh_land?asOf=-700")
      assert(!iron.contains("zzh_polity"), iron)

      val (cs, cb) = get(s"$url/api/places/zzh_land/children?asOf=-1300")
      assertEquals(cs, 200, cb)
      assert(cb.contains("zzh_polity"), cb)

      val (ts, tb) = get(s"$url/api/places/zzh_world/tree?asOf=-1300")
      assertEquals(ts, 200, tb)
      assert(tb.contains("\"depth\":2"), tb) // world > land > polity

      assertEquals(get(s"$url/api/places/zzh_nope")._1, 404)
      assertEquals(post(s"$url/api/places/import", "{ not json")._1, 400)
      assertEquals(post(s"$url/api/places/import?kind=nonsense", doc)._1, 400)

      b.stop()
    }

  test("a place id in the filter reaches sources through finding_places; an unknown one is a name"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      // no findings were ever materialised into zzh_land, so filtering by it must return nothing —
      // and must NOT fall back to matching the id as a region-name substring
      val (s1, b1) = get(s"$url/api/sources?place=zzh_land&limit=1")
      assertEquals(s1, 200, b1)
      assert(b1.contains("\"total\":0"), b1)
      // an unknown value is still the wave-1 name match, so the endpoint never 500s on free text
      assertEquals(get(s"$url/api/sources?place=Definitely%20Not%20A%20Place&limit=1")._1, 200)
      b.stop()
    }

  private def put(url: String, body: String) =
    send(
      java.net.http.HttpRequest
        .newBuilder(java.net.URI.create(url))
        .header("Content-Type", "application/json")
        .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body))
        .build()
    )

  /** Its own places and source (`zzc_*`), far from `zzh_*`: the test above asserts `zzh_land` selects nothing, and a
    * shared test database keeps what earlier runs wrote.
    */
  private val countsDoc = Lpf.render(
    List(
      Place.Input(
        Place(com.histmersion.api.catalogue.Place.Id("zzc_world"), com.histmersion.api.catalogue.Place.Label("Zzc World"), Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region)),
        List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzc World")))
      ),
      Place.Input(
        Place(
          com.histmersion.api.catalogue.Place.Id("zzc_land"),
          com.histmersion.api.catalogue.Place.Label("Zzc Land"),
          Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region),
          Some(
            Place.Geometry("""{"type":"Polygon","coordinates":[[[120.0,10.0],[130.0,10.0],[130.0,20.0],[120.0,20.0],[120.0,10.0]]]}""")
          )
        ),
        List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzc Land"))),
        List(Place.Relation(com.histmersion.api.catalogue.Place.Relation.ChildId("zzc_land"), com.histmersion.api.catalogue.Place.Relation.ParentId("zzc_world")))
      )
    )
  )

  test("#716: counts=true says what place=<id> selects — the place and its descendants"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      assertEquals(post(s"$url/api/places/import", countsDoc)._1, 200)
      // a source write materialises its findings into the places that cover them (odd years: the
      // aggregate suites assert on which periods exist anywhere in the shared test database)
      val (ps, pb) = put(
        s"$url/api/sources/zzc_counted",
        """{"title":"Counted","authors":["A"],"year":2020,"status":"current",
          |"findings":[{"claim":"here","lon":125.0,"lat":15.0,"periodStart":-1337,"periodEnd":-1221},
          |{"claim":"also here","lon":126.0,"lat":16.0,"periodStart":-1337,"periodEnd":-1221}]}""".stripMargin
      )
      assertEquals(ps, 200, pb)

      val (ls, lb) = get(s"$url/api/places/zzc_land?counts=true")
      assertEquals(ls, 200, lb)
      assert(lb.contains("\"counts\":{\"sources\":1,\"findings\":2,\"articles\":0,\"events\":0}"), lb)
      // the parent has no shape and no findings of its own: it selects its child's
      val (ws, wb) = get(s"$url/api/places/zzc_world?counts=true")
      assertEquals(ws, 200, wb)
      assert(wb.contains("\"counts\":{\"sources\":1,\"findings\":2"), wb)
      // without the flag, no counting
      val (_, plain) = get(s"$url/api/places/zzc_land")
      assert(!plain.contains("\"sources\":"), plain)
      b.stop()
    }

  test("#718: /api/about reports the build, health, job queue and store counts"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0), OfflineConverter()).start()
      val url = s"http://127.0.0.1:${b.port}"
      assertEquals(post(s"$url/api/places/import", countsDoc)._1, 200)
      val (as, ab) = get(s"$url/api/about")
      assertEquals(as, 200, ab)
      val about = io.circe.parser.decode[About](ab).fold(e => fail(s"$e: $ab"), identity)
      assertEquals(about.build.version, Version.version)
      // the AGPL's network clause: everyone using the server is offered the source of what is running
      assertEquals(about.build.licence, Some("AGPL-3.0-or-later"))
      assertEquals(
        about.build.sourceUrl,
        Some(CorrespondingSource.url(CorrespondingSource.Repository(cfg.server.sourceUrl.value), BuildMeta.gitCommit.map(CorrespondingSource.Commit(_))))
      )
      assertEquals(about.health.db, "ok")
      assert(about.health.schemaVersion.isDefined, ab)
      val counts = about.counts.getOrElse(fail(s"no counts: $ab"))
      assert(counts.places.value >= 2, ab) // at least the two imported above
      assert(counts.sources.value >= 0 && counts.periods.value >= 0, ab)
      assert(about.jobs.queued.value >= 0, ab)
      b.stop()
    }

  test("about: the Storage part — the database's size and largest tables, the configured directories, measured once"):
    val base = AppConfig.load()
    val d = java.nio.file.Files.createTempDirectory("about-storage")
    java.nio.file.Files.createDirectories(d.resolve("backups"))
    java.nio.file.Files.write(d.resolve("backups/histmersion-20261001T100000Z.dump"), Array.fill[Byte](2048)(1))
    val cfg = base.modify(
      _.db).setTo(base.db.modify(_.url).setTo(testDb.get)).modify(_.runtime).setTo(base.runtime.modify(_.dataDir).setTo(d.resolve("data").toString)).modify(_.backups).setTo(base.backups.modify(_.dir).setTo(d.resolve("backups").toString)).modify(_.converter).setTo(base.converter.modify(_.spoolDir).setTo(d.resolve("uploads").toString)).modify(_.bundles).setTo(base.bundles.modify(_.dir).setTo(d.resolve("bundles").toString)).modify(_.basemap).setTo(base.basemap.modify(_.dir).setTo(d.resolve("basemap").toString)).modify(_.places).setTo(base.places.modify(_.sources).setTo(base.places.sources.modify(_.dir).setTo(d.resolve("gazetteer").toString))).modify(_.storage).setTo(base.storage.modify(_.waitMillis).setTo(30000)
    )
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0), OfflineConverter()).start()
      val url = s"http://127.0.0.1:${b.port}"
      val (as, ab) = get(s"$url/api/about")
      assertEquals(as, 200, ab)
      val st = io.circe.parser.decode[About](ab).fold(e => fail(s"$e: $ab"), identity).storage
      assert(!st.measuring.value, ab)
      val db = st.entries.find(_.key.value == "database").getOrElse(fail(s"no database entry: $ab"))
      assert(db.bytes.exists(_.value > 0) && db.detail.exists(_.value.endsWith("_test")), db)
      assert(st.tables.nonEmpty && st.tables.size <= cfg.storage.topTables.value, st.tables)
      val bk = st.entries.find(_.key.value == "backups").getOrElse(fail(ab))
      assertEquals(bk.bytes, Some(2048L))
      assertEquals(st.backups.map(_.count), Some(1))
      assert(st.volumes.nonEmpty, ab)
      // the second About within the TTL answers the same measurement
      val again = io.circe.parser.decode[About](get(s"$url/api/about")._2).fold(e => fail(e.toString), identity).storage
      assertEquals(again.measuredAt, st.measuredAt)
      b.stop()
    }

  /** #987: a name added by hand and a place of one's own, over REST and MCP (`zzn_*`). */
  test("names added by hand and hand-added places: REST and MCP agree"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      val clean = testPool(cfg.db)
      def wipe() = com.augustnagro.magnum.connect(clean):
        import com.augustnagro.magnum.*
        val _ = sql"DELETE FROM places WHERE id LIKE 'zzn_%' OR id LIKE 'local_zzn_%'".update.run()
      wipe()
      val lake = Lpf.render(
        List(
          Place.Input(
            Place(
              com.histmersion.api.catalogue.Place.Id("zzn_lake"),
              com.histmersion.api.catalogue.Place.Label("Zzn Sea"),
              Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Feature),
              Some(Place.Geometry("""{"type":"Point","coordinates":[140.5,-20.5]}"""))
            ),
            List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzn Sea")))
          )
        )
      )
      assertEquals(post(s"$url/api/places/import?dataset=seed", lake)._1, 200)

      val (as, ab) = post(s"$url/api/places/zzn_lake/names", """{"name":"Lake Zznkinneret","lang":"EN"}""")
      assertEquals(as, 200, ab)
      assert(ab.contains("\"lang\":\"en\"") && ab.contains("\"changed\":true"), ab)
      assertEquals(post(s"$url/api/places/zzn_nope/names", """{"name":"X"}""")._1, 404)
      assertEquals(post(s"$url/api/places/zzn_lake/names", """{"name":"  "}""")._1, 400)
      assertEquals(
        post(s"$url/api/places/zzn_lake/names", """{"name":"X","language":"en"}""")._1,
        400,
        "unknown keys are refused"
      )

      // a re-seed of the place keeps the name added by hand
      assertEquals(post(s"$url/api/places/import?dataset=seed", lake)._1, 200)
      val (ls, lb) = get(s"$url/api/places/names?place=zzn_lake")
      assertEquals(ls, 200, lb)
      assert(lb.contains("\"name\":\"Lake Zznkinneret\""), lb)
      val (qs, qb) = get(s"$url/api/places?q=Zznkinneret")
      assertEquals(qs, 200, qb)
      assert(qb.contains("\"id\":\"zzn_lake\""), qb)

      // a seed name cannot be removed; the hand-added one can
      assertEquals(post(s"$url/api/places/zzn_lake/names/remove", """{"name":"Zzn Sea"}""")._1, 400)
      assertEquals(
        post(s"$url/api/places/zzn_lake/names/remove", """{"name":"Lake Zznkinneret","lang":"en"}""")._1,
        200
      )

      // a place of one's own: local, its source required, its id never reused
      val place = """{"label":"Zzn Gornji","kind":"site","lon":140.25,"lat":-20.25,"source":"fixture map"}"""
      val (ps, pb) = post(s"$url/api/places", place)
      assertEquals(ps, 200, pb)
      assert(pb.contains("\"id\":\"local_zzn_gornji\""), pb)
      assertEquals(post(s"$url/api/places", place)._1, 400, "the id is taken")
      assertEquals(post(s"$url/api/places", place.replace("fixture map", ""))._1, 400, "no source, no point")

      // the MCP tools reach the same handlers
      def mcp(name: String, args: String): String =
        send(
          java.net.http.HttpRequest
            .newBuilder(java.net.URI.create(s"$url/mcp"))
            .header("content-type", "application/json")
            .header("accept", "application/json, text/event-stream")
            .POST(
              java.net.http.HttpRequest.BodyPublishers.ofString(
                s"""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$name","arguments":$args}}"""
              )
            )
            .build()
        )._2
      val added = mcp("place_names", """{"placeId":"zzn_lake","name":"Zznkinnereth","lang":"he"}""")
      assert(added.contains("added the name **Zznkinnereth** (he)"), added)
      val listed = mcp("place_names", """{"placeId":"zzn_lake"}""")
      assert(listed.contains("Zznkinnereth"), listed)
      val second =
        mcp("add_place", """{"label":"Zzn Arkona","kind":"site","lon":140.3,"lat":-20.3,"source":"fixture"}""")
      assert(second.contains("`local_zzn_arkona`"), second)
      val refusedMcp =
        mcp("add_place", """{"label":"Zzn Arkona","kind":"site","lon":140.3,"lat":-20.3,"source":"fixture"}""")
      assert(refusedMcp.contains("already the id of a place"), refusedMcp)

      wipe()
      b.stop()
    }

  test("V080: a kind and period set by hand — REST and MCP agree, a re-seed keeps it, a reset restores the source's"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      val clean = testPool(cfg.db)
      def wipe() = com.augustnagro.magnum.connect(clean):
        import com.augustnagro.magnum.*
        val _ = sql"DELETE FROM places WHERE id LIKE 'zzo_%'".update.run()
        val _ = sql"DELETE FROM place_aliases WHERE alias LIKE 'zzo_%'".update.run()
        val _ = sql"DELETE FROM place_removals WHERE id LIKE 'zzo_%'".update.run()
      wipe()
      val regio = Lpf.render(
        List(
          Place.Input(
            Place(
              com.histmersion.api.catalogue.Place.Id("zzo_regio"),
              com.histmersion.api.catalogue.Place.Label("Zzo Regio XII"),
              Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site),
              Some(Place.Geometry("""{"type":"Point","coordinates":[141.5,-21.3]}"""))
            ),
            List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzo Regio XII")))
          )
        )
      )
      assertEquals(post(s"$url/api/places/import?dataset=seed", regio)._1, 200)

      val (ss, sb) =
        post(s"$url/api/places/zzo_regio/override", """{"kind":"Region","reason":"one of the 14 regiones"}""")
      assertEquals(ss, 200, sb)
      assert(sb.contains("\"kindBefore\":\"site\"") && sb.contains("\"kindAfter\":\"region\""), sb)
      assertEquals(post(s"$url/api/places/zzo_nope/override", """{"kind":"region"}""")._1, 404)
      assertEquals(post(s"$url/api/places/zzo_regio/override", """{"kind":"town"}""")._1, 400)
      assertEquals(post(s"$url/api/places/zzo_regio/override", """{}""")._1, 400, "nothing to set")
      assertEquals(
        post(s"$url/api/places/zzo_regio/override", """{"kind":"region","why":"x"}""")._1,
        400,
        "unknown keys are refused"
      )

      // a re-seed sends kind site again; the override stays and the source's kind is remembered
      assertEquals(post(s"$url/api/places/import?dataset=seed", regio)._1, 200)
      val (ds1, db1) = get(s"$url/api/places/zzo_regio")
      assertEquals(ds1, 200, db1)
      assert(db1.contains("\"kind\":\"region\""), db1)
      val (ls, lb) = get(s"$url/api/places/overrides?place=zzo_regio")
      assertEquals(ls, 200, lb)
      assert(lb.contains("\"sourceKind\":\"site\"") && lb.contains("\"reason\":\"one of the 14 regiones\""), lb)

      def mcp(name: String, args: String): String =
        send(
          java.net.http.HttpRequest
            .newBuilder(java.net.URI.create(s"$url/mcp"))
            .header("content-type", "application/json")
            .header("accept", "application/json, text/event-stream")
            .POST(
              java.net.http.HttpRequest.BodyPublishers.ofString(
                s"""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$name","arguments":$args}}"""
              )
            )
            .build()
        )._2
      val listed = mcp("place_override", """{"placeId":"zzo_regio"}""")
      assert(listed.contains("**Zzo Regio XII** `zzo_regio`: region _(the source says: site, undated)_"), listed)
      val dated = mcp("place_override", """{"placeId":"zzo_regio","periodStart":-7}""")
      assert(dated.contains("period undated → from 7 BCE"), dated)
      val noId = mcp("place_override", """{"kind":"region"}""")
      assert(noId.contains("`placeId` is required"), noId)
      val reset = mcp("place_override", """{"placeId":"zzo_regio","reset":true}""")
      assert(
        reset.contains(
          "reset **Zzo Regio XII** `zzo_regio` to what its source says: kind region → site, period from 7 BCE → undated"
        ),
        reset
      )
      assertEquals(post(s"$url/api/places/zzo_regio/override", """{"reset":true}""")._1, 404, "nothing left to reset")
      val (es, eb) = get(s"$url/api/places/overrides?place=zzo_regio")
      assertEquals((es, eb), (200, "[]"))

      // a merge (REST) and a removal (MCP); neither comes back with the seed
      val two = Lpf.render(
        List(
          Place.Input(
            Place(
              com.histmersion.api.catalogue.Place.Id("zzo_dup"),
              com.histmersion.api.catalogue.Place.Label("Withdrawn: Zzo Regio XII"),
              Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site),
              Some(Place.Geometry("""{"type":"Point","coordinates":[141.5,-21.3]}"""))
            ),
            List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Withdrawn: Zzo Regio XII")))
          ),
          Place.Input(
            Place(
              com.histmersion.api.catalogue.Place.Id("zzo_road"),
              com.histmersion.api.catalogue.Place.Label("Withdrawn: Untitled"),
              Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site),
              Some(Place.Geometry("""{"type":"Point","coordinates":[141.7,-21.7]}"""))
            ),
            List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Withdrawn: Untitled")))
          )
        )
      )
      assertEquals(post(s"$url/api/places/import?dataset=seed", two)._1, 200)
      val (ms, mb) = post(s"$url/api/places/zzo_dup/merge", """{"into":"zzo_regio","reason":"same place"}""")
      assertEquals(ms, 200, mb)
      assert(mb.contains("\"into\":\"zzo_regio\""), mb)
      assertEquals(
        post(s"$url/api/places/zzo_dup/merge", """{"into":"zzo_regio"}""")._1,
        400,
        "the old id is zzo_regio now: into itself"
      )
      val removed = mcp("place_override", """{"placeId":"zzo_road","remove":true,"reason":"withdrawn"}""")
      assert(removed.contains("removed **Withdrawn: Untitled** `zzo_road`; no re-seed brings it back"), removed)
      val both = mcp("place_override", """{"placeId":"zzo_regio","remove":true,"mergeInto":"zzo_x"}""")
      assert(both.contains("exclude each other"), both)
      assertEquals(post(s"$url/api/places/import?dataset=seed", two)._1, 200)
      assertEquals(get(s"$url/api/places/zzo_road")._1, 404)
      val (rs, rb) = get(s"$url/api/places/zzo_dup")
      assert(rs == 200 && rb.contains("\"id\":\"zzo_regio\""), s"$rs $rb")

      // V081: a point set by hand — refused without the gazetteer value it comes from; kept by a re-seed
      assertEquals(
        post(s"$url/api/places/zzo_regio/override", """{"pointLat":-21.0,"pointLon":141.0}""")._1,
        400,
        "a point needs a reason"
      )
      val (ps, pb) = post(
        s"$url/api/places/zzo_regio/override",
        """{"pointLat":-21.0,"pointLon":141.0,"reason":"fixture gazetteer point"}"""
      )
      assertEquals(ps, 200, pb)
      assert(pb.contains("\"latBefore\":-21.3") && pb.contains("\"latAfter\":-21.0"), pb)
      assertEquals(post(s"$url/api/places/import?dataset=seed", regio)._1, 200)
      val (gs, gb) = get(s"$url/api/places/zzo_regio")
      assertEquals(gs, 200, gb)
      assert(gb.contains("141") && !gb.contains("141.5"), gb)
      val pointed = mcp("place_override", """{"placeId":"zzo_regio"}""")
      assert(
        pointed.contains("point 21.0000 S 141.0000 E _(the source says: site, undated, point 21.3000 S 141.5000 E)_"),
        pointed
      )
      val unpointed = mcp("place_override", """{"placeId":"zzo_regio","reset":true}""")
      assert(unpointed.contains("point 21.0000 S 141.0000 E → 21.3000 S 141.5000 E"), unpointed)

      wipe()
      b.stop()
    }

  test("#1064: /api/places/sources is routed before /api/places/{id}; load refuses an unknown source"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      val (ss, sb) = get(s"$url/api/places/sources")
      assertEquals(ss, 200, sb)
      assert(sb.trim.startsWith("["), sb)
      val (ls, lb) = post(s"$url/api/places/load?source=atlantis", "")
      assertEquals(ls, 400, lb)
      assert(lb.contains("geonames, wikidata, lpf"), lb)
      b.stop()
    }
