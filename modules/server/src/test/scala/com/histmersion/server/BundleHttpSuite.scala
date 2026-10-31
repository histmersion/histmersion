package com.histmersion.server

import ox.supervised
import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.{Db, Migrate}
import io.circe.parser
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Files
import com.histmersion.values.BundleKind
import com.histmersion.util.data.exchange.{BundleFormat, BundleTar}

/** The portable bundle over real HTTP: `GET/PUT /api/instance`, `GET /api/bundle`, `POST /api/bundle/verify` and
  * `POST /api/bundle/import` — the wiring, the write gate, the download name, and an instance importing its own bundle
  * changing nothing. The behaviour in depth is `db.BundleSuite`'s. Skipped without HISTMERSION_TEST_DB_URL.
  */
class BundleHttpSuite extends com.histmersion.db.DbSuite with com.histmersion.db.Bounded:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Key = "k3y"
  private val http = HttpClient.newHttpClient()
  private val work = Files.createTempDirectory("histmersion-bundles")

  private lazy val cfg =
    val base = AppConfig.load()
    base.copy(
      db = base.db.copy(url = testDb.get),
      auth = base.auth.copy(apiKey = Key),
      bundles = base.bundles.copy(dir = work.toString)
    )

  private def withServer(body: String => Unit): Unit =
    Migrate.run(testPool(cfg.db))
    supervised {
      val b = Composition.server(cfg, "127.0.0.1", 0).start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  private def get(url: String): HttpResponse[Array[Byte]] =
    http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray())

  private def post(url: String, body: Array[Byte], key: Option[String] = Some(Key)): HttpResponse[String] =
    val b = HttpRequest
      .newBuilder(URI.create(url))
      .header("content-type", "application/octet-stream")
      .POST(HttpRequest.BodyPublishers.ofByteArray(body))
    http.send(key.fold(b)(k => b.header("Authorization", s"Bearer $k")).build(), HttpResponse.BodyHandlers.ofString())

  private def postJson(url: String, json: String, key: Option[String] = Some(Key)): HttpResponse[String] =
    val b = HttpRequest
      .newBuilder(URI.create(url))
      .header("content-type", "application/json")
      .header("accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString(json))
    http.send(key.fold(b)(k => b.header("Authorization", s"Bearer $k")).build(), HttpResponse.BodyHandlers.ofString())

  private def put(url: String, json: String, key: Option[String]): HttpResponse[String] =
    val b = HttpRequest
      .newBuilder(URI.create(url))
      .header("content-type", "application/json")
      .PUT(HttpRequest.BodyPublishers.ofString(json))
    http.send(key.fold(b)(k => b.header("Authorization", s"Bearer $k")).build(), HttpResponse.BodyHandlers.ofString())

  private def decode[A: io.circe.Decoder](s: String): A = parser.decode[A](s).fold(e => fail(s"$e in $s"), identity)

  test("instance, export, verify and an own-bundle import over HTTP"):
    withServer { url =>
      val inst = decode[InstanceInfo](String(get(s"$url/api/instance").body, "UTF-8"))

      // naming the instance is a write: gated
      assertEquals(put(s"$url/api/instance", """{"label":"zz-work"}""", None).statusCode, 401)
      val named = put(s"$url/api/instance", """{"label":"zz-work"}""", Some(Key))
      assertEquals(named.statusCode, 200, named.body)
      assertEquals(decode[InstanceInfo](named.body).label, Some("zz-work"))

      val exp = get(s"$url/api/bundle")
      assertEquals(exp.statusCode, 200)
      val cd = exp.headers.firstValue("content-disposition").orElse("")
      assert(cd.contains("histmersion-zz-work-") && cd.endsWith(".hmx\""), cd)
      val r = BundleTar.Reader(java.io.ByteArrayInputStream(exp.body))
      val m = r
        .next()
        .toOption
        .flatten
        .filter(_.name == Bundle.ManifestPath)
        .flatMap(_ => BundleFormat.parseManifest(r.readAll()).toOption)
        .getOrElse(fail("no manifest"))
      assertEquals(m.instance, BundleInstance(inst.id, Some("zz-work")))
      // the same bytes again: the export is deterministic
      assert(java.util.Arrays.equals(get(s"$url/api/bundle").body, exp.body))

      assertEquals(get(s"$url/api/bundle?since=yesterday").statusCode, 400)

      val v = post(s"$url/api/bundle/verify", exp.body)
      assertEquals(v.statusCode, 200, v.body)
      assert(decode[BundleVerifyResult](v.body).ok, v.body)
      assertEquals(post(s"$url/api/bundle/verify", exp.body, None).statusCode, 401)

      val i = post(s"$url/api/bundle/import", exp.body)
      assertEquals(i.statusCode, 200, i.body)
      val res = decode[BundleImportResult](i.body)
      assert(res.applied, i.body)
      assertEquals(res.changed, 0, i.body)

      assertEquals(post(s"$url/api/bundle/import", Array.emptyByteArray).statusCode, 400)
      val _ = put(s"$url/api/instance", """{"label":""}""", Some(Key))
    }

  test("merge plan over HTTP and MCP: upload by sha256, plan it (read-only), import it with resolutions"):
    withServer { url =>
      val exp = get(s"$url/api/bundle").body
      val up = post(s"$url/api/bundle/upload", exp)
      assertEquals(up.statusCode, 200, up.body)
      val u = decode[BundleUpload](up.body)
      assertEquals(u.sha256, BundleFormat.sha256Hex(exp))
      assert(u.manifest.isDefined && u.problems.isEmpty, up.body)
      assertEquals(post(s"$url/api/bundle/upload", exp, None).statusCode, 401)

      val pl = postJson(
        s"$url/api/merge/plan",
        s"""{"bundle":"${u.sha256}","resolutions":{"resolve":[{"issue":"S-00000000","decision":"keep-left"}]}}"""
      )
      assertEquals(pl.statusCode, 200, pl.body)
      val plan = decode[MergePlan](pl.body)
      // its own bundle: every source the same record by uid, nothing to decide; a resolution for no issue is an error
      assertEquals(plan.open, Nil)
      assertEquals(plan.matches.byUid, plan.matches.sources)
      assert(plan.resolutionErrors.exists(_.startsWith("S-00000000")), pl.body)
      // a name that is no upload: 404; not a sha256: 400
      assertEquals(postJson(s"$url/api/merge/plan", s"""{"bundle":"${"0" * 64}"}""").statusCode, 404)
      assertEquals(postJson(s"$url/api/merge/plan", """{"bundle":"../../etc/passwd"}""").statusCode, 400)

      val im = postJson(s"$url/api/merge/import", s"""{"bundle":"${u.sha256}","dryRun":true}""")
      assertEquals(im.statusCode, 200, im.body)
      assert(decode[BundleImportResult](im.body).dryRun, im.body)

      // merge apply: a plan that is not the one reviewed is refused; its own bundle changes nothing,
      // so the apply is a no-op (no backup is taken); and it is not an MCP tool
      val stale = postJson(s"$url/api/merge/apply", s"""{"bundle":"${u.sha256}","planHash":"0badc0de"}""")
      assertEquals(stale.statusCode, 200, stale.body)
      // the apply is a job (its backup can outlast a request): a quick one answers done, with its result
      val sj = decode[MergeApplyJob](stale.body)
      assertEquals(sj.state, MergeJobState.Done, stale.body)
      val st = sj.result.getOrElse(fail(stale.body))
      assert(!st.applied && st.problems.exists(_.contains("not 0badc0de")), stale.body)
      // …and can be asked about again by its id
      val again = get(s"$url/api/merge/apply/${sj.job}")
      assertEquals(again.statusCode, 200)
      assertEquals(decode[MergeApplyJob](String(again.body, "UTF-8")).result.map(_.planHash), Some(st.planHash))
      assertEquals(get(s"$url/api/merge/apply/no-such-job").statusCode, 404)
      val clean = decode[MergePlan](postJson(s"$url/api/merge/plan", s"""{"bundle":"${u.sha256}"}""").body)
      assertEquals(clean.changes, 0)
      val ap = postJson(s"$url/api/merge/apply", s"""{"bundle":"${u.sha256}","planHash":"${clean.planHash}"}""")
      val applied = decode[MergeApplyJob](ap.body).result.getOrElse(fail(ap.body))
      assert(applied.applied && applied.mergeId.isEmpty && applied.backup.isEmpty, ap.body)
      assertEquals(
        postJson(s"$url/api/merge/apply", s"""{"bundle":"${u.sha256}","planHash":"x"}""", None).statusCode,
        401
      )
      val tools = postJson(s"$url/mcp", """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""")
      assert(!tools.body.contains("merge_apply"), "merge apply must not be an MCP tool")

      // the MCP tool reads the same plan
      val mcp = postJson(
        s"$url/mcp",
        s"""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"merge_plan","arguments":{"bundle":"${u.sha256}"}}}"""
      )
      assertEquals(mcp.statusCode, 200, mcp.body)
      assert(mcp.body.contains(plan.planHash), mcp.body)
    }

  test("the review draft, resolution files, merge list/show/ledger and label renames over HTTP and MCP"):
    withServer { url =>
      val ds = testPool(cfg.db)
      val sha = "a" * 64
      // the review screen's draft: empty until written, the whole set replaced, gated like any write
      assertEquals(
        decode[MergeSession](String(get(s"$url/api/merge/sessions/$sha").body, "UTF-8")).resolutions,
        MergeResolutions()
      )
      val res =
        """{"resolve":[{"issue":"S-1234abcd","decision":"keep-left"},{"issue":"A-1234abcd","decision":"file:merged.md","text":"merged\n"}]}"""
      assertEquals(put(s"$url/api/merge/sessions/$sha", res, None).statusCode, 401)
      val saved = put(s"$url/api/merge/sessions/$sha", res, Some(Key))
      assertEquals(saved.statusCode, 200, saved.body)
      val back = decode[MergeSession](String(get(s"$url/api/merge/sessions/$sha").body, "UTF-8"))
      assertEquals(back.resolutions.resolve.map(_.decision), List("keep-left", "file:merged.md"))
      assertEquals(back.resolutions.resolve.flatMap(_.text), List("merged\n"))
      assertEquals(get(s"$url/api/merge/sessions/not-a-sha").statusCode, 400)

      // a resolution file read on the server (the web screen's upload); every bad line at once
      val yaml = "bundle: 0192f6c4-0000-7000-8000-000000000000\nresolve:\n  S-1234abcd: keep-right\n"
      val parsed = post(s"$url/api/merge/resolutions", yaml.getBytes("UTF-8"))
      assertEquals(parsed.statusCode, 200, parsed.body)
      assertEquals(decode[MergeResolutions](parsed.body).resolve, List(MergeResolution("S-1234abcd", "keep-right")))
      val bad = post(s"$url/api/merge/resolutions", "resolve: [1, 2]\nwhat: x\n".getBytes("UTF-8"))
      assertEquals(bad.statusCode, 400, bad.body)
      assert(bad.body.contains("unknown key 'what'") && bad.body.contains("resolve:"), bad.body)

      // merge list | show | ledger over what an import left
      import com.augustnagro.magnum.*
      val (mid, src) = transact(ds) {
        val m = sql"""INSERT INTO merges (bundle_id, bundle_sha256, from_instance, from_label, plan_hash, backup)
                      VALUES (gen_random_uuid(), ${"b" * 64}, gen_random_uuid(), 'zz-home', 'cafe0123', 'histmersion-x.dump')
                      RETURNING id""".query[java.util.UUID].run().head
        sql"""INSERT INTO sources (citekey, title, authors) VALUES ('zzmerge2020_ledger', 'Ledger', ARRAY['A'])
              ON CONFLICT (citekey) DO NOTHING""".update.run()
        val u = sql"SELECT uid FROM sources WHERE citekey = 'zzmerge2020_ledger'".query[java.util.UUID].run().head
        val f = java.util.UUID.randomUUID()
        sql"""INSERT INTO merge_ledger (foreign_uid, kind, merge_id, foreign_rev, local_uid, local_rev_after, decision)
              VALUES ($f, 'source', $m, 'r1', $u, 'r2', 'FastForward')""".update.run()
        sql"INSERT INTO uid_aliases (alias_uid, uid, kind, merge_id) VALUES ($f, $u, 'source', $m)".update.run()
        (m, u)
      }
      try
        val list = decode[List[MergeInfo]](String(get(s"$url/api/merges").body, "UTF-8"))
        val mine = list.find(_.id == mid).getOrElse(fail(list.toString))
        assertEquals(
          (mine.from.label, mine.planHash, mine.ledgerRows, mine.redirects),
          (Some("zz-home"), Some("cafe0123"), 1, 1)
        )
        val d = get(s"$url/api/merges/${mid.toString.take(8)}")
        assertEquals(d.statusCode, 200, String(d.body, "UTF-8"))
        assertEquals(decode[MergeDetail](String(d.body, "UTF-8")).merge.backup, Some("histmersion-x.dump"))
        assertEquals(get(s"$url/api/merges/ffffffff-0000").statusCode, 404)
        val led = decode[MergeLedger](String(get(s"$url/api/merges/ledger?uid=zzmerge2020_ledger").body, "UTF-8"))
        assertEquals(led.entries.map(e => (e.label, e.decision)), List((Some("zzmerge2020_ledger"), "FastForward")))
        assertEquals(led.redirects.map(_.uid), List(src))
        val mcp = postJson(
          s"$url/mcp",
          """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"merges","arguments":{"action":"ledger","uid":"zzmerge2020_ledger"}}}"""
        )
        assert(mcp.body.contains("zzmerge2020_ledger") && mcp.body.contains("Redirects"), mcp.body)
      finally
        transact(ds) {
          sql"DELETE FROM uid_aliases WHERE merge_id = $mid".update.run()
          sql"DELETE FROM merges WHERE id = $mid".update.run()
          sql"DELETE FROM sources WHERE citekey = 'zzmerge2020_ledger'".update.run()
        }

      // label renames: the old label keeps resolving, and a write to it goes to the goal under its new
      // slug (#1799), the answer naming that slug
      assertEquals(put(s"$url/api/goals/zz-goal-a", """{"title":"A goal"}""", Some(Key)).statusCode, 200)
      val rg = postJson(s"$url/api/goals/zz-goal-a/rename", """{"to":"zz-goal-b"}""")
      assertEquals(rg.statusCode, 200, rg.body)
      assertEquals(decode[LabelRenamed](rg.body), LabelRenamed(BundleKind.Goal, "zz-goal-a", "zz-goal-b"))
      assertEquals(decode[Goal](String(get(s"$url/api/goals/zz-goal-a").body, "UTF-8")).slug, "zz-goal-b")
      val w = put(s"$url/api/goals/zz-goal-a", """{"title":"again"}""", Some(Key))
      assertEquals(w.statusCode, 200, w.body)
      assertEquals(decode[Goal](w.body).slug, "zz-goal-b")
      assertEquals(decode[Goal](String(get(s"$url/api/goals/zz-goal-b").body, "UTF-8")).title, "again")
      assertEquals(postJson(s"$url/api/goals/zz-goal-b/rename", """{"to":"Not A Slug"}""").statusCode, 400)
      assertEquals(postJson(s"$url/api/goals/zz-goal-b/rename", """{"to":"zz-goal-c"}""", None).statusCode, 401)
      val viaMcp = postJson(
        s"$url/mcp",
        """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"rename_label","arguments":{"kind":"goal","from":"zz-goal-b","to":"zz-goal-a"}}}"""
      )
      assert(viaMcp.body.contains("Goal renamed"), viaMcp.body)
      // renaming back to the old slug drops that alias: the slug is the goal's own again
      assertEquals(decode[Goal](String(get(s"$url/api/goals/zz-goal-a").body, "UTF-8")).slug, "zz-goal-a")
      assertEquals(decode[Goal](String(get(s"$url/api/goals/zz-goal-b").body, "UTF-8")).slug, "zz-goal-a")
      val _ = http.send(
        HttpRequest
          .newBuilder(URI.create(s"$url/api/goals/zz-goal-a"))
          .DELETE()
          .header("Authorization", s"Bearer $Key")
          .build(),
        HttpResponse.BodyHandlers.ofString()
      )
    }

  // #888: the apply jobs were kept in memory — a restart answered 404 to the CLI's poll, and an apply
  // the server stopped under was never reported. In a database of its own, so no other suite's
  // apply is refused while this one holds a "running" row.
  test("merge apply jobs outlive a restart: a finished one answers its report, one cut short is interrupted"):
    val name = "histmersion_bundlehttp_restart_test"
    def exec(stmt: String): Unit =
      val c = testPool(cfg.db).getConnection
      try {
        val st = c.createStatement();
        try { val _ = st.execute(stmt) }
        finally st.close()
      } finally c.close()
    exec(s"DROP DATABASE IF EXISTS $name WITH (FORCE)")
    exec(s"CREATE DATABASE $name")
    val own = cfg.copy(db = cfg.db.copy(url = testDb.get.replaceFirst("/[^/?]+(\\?|$)", s"/$name$$1")))
    def serve(body: String => Unit): Unit =
      supervised {
        val b = Composition.server(own, "127.0.0.1", 0).start()
        try body(s"http://127.0.0.1:${b.port}")
        finally b.stop()
      }
    try
      var job = "" // the apply of the first run, asked about in the second
      serve { url =>
        val sha = decode[BundleUpload](post(s"$url/api/bundle/upload", get(s"$url/api/bundle").body).body).sha256
        val ap =
          decode[MergeApplyJob](postJson(s"$url/api/merge/apply", s"""{"bundle":"$sha","planHash":"0badc0de"}""").body)
        assertEquals(ap.state, MergeJobState.Done)
        job = ap.job
      }
      // the server stopped while an apply ran (its row still says running)
      val cut = com.histmersion.util.jobs.JobRepo.startMergeApply(testPool(own.db), "c" * 64, "feedbeef")
      serve { url =>
        val done = get(s"$url/api/merge/apply/$job")
        assertEquals(done.statusCode, 200, String(done.body, "UTF-8"))
        val d = decode[MergeApplyJob](String(done.body, "UTF-8"))
        assertEquals((d.state, d.planHash), (MergeJobState.Done, "0badc0de"))
        assert(d.result.exists(r => !r.applied && r.problems.exists(_.contains("not 0badc0de"))), d.toString)
        val i = decode[MergeApplyJob](String(get(s"$url/api/merge/apply/$cut").body, "UTF-8"))
        assertEquals((i.state, i.bundle, i.planHash), (MergeJobState.Interrupted, "c" * 64, "feedbeef"))
        assert(i.error.exists(_.contains("never half applied")), i.toString)
        // …and it no longer blocks the next apply
        val sha = decode[BundleUpload](post(s"$url/api/bundle/upload", get(s"$url/api/bundle").body).body).sha256
        assertEquals(postJson(s"$url/api/merge/apply", s"""{"bundle":"$sha","planHash":"0badc0de"}""").statusCode, 200)
        // the jobs list shows it, by what it is
        val jobs = decode[List[JobInfo]](String(get(s"$url/api/jobs").body, "UTF-8"))
        assert(
          jobs.exists(j => j.id == cut && j.kind == MergeApplyJob.JobKind && j.error.contains(JobState.Interrupted)),
          jobs.toString
        )
      }
    finally exec(s"DROP DATABASE IF EXISTS $name WITH (FORCE)")
