package com.histmersion.domain.library.collections

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.config.*
import com.histmersion.server.{Composition, OfflineConverter}
import com.histmersion.plugins.calibre.CalibreFixture
import ox.supervised

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.library.Source

/** Every `histmersion calibre …` command, run end to end over a fixture library: a server on port 0 over a database of
  * this suite's own, seeded with sources, and the CLI as a separate process (its stdout, stderr and exit code are what
  * a person sees), with the fake `calibredb` and the logging `sqlite3` of [[CalibreFixture]] — whose argument logs are
  * part of the transcript. Writes run against a COPY of the fixture library. The transcript is `calibre/commands.txt`,
  * compared byte for byte (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites it); only what differs between runs is normalised:
  * the scratch directories, the server's port, job ids and timestamps.
  */
class CalibreCommandsSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(15, "min")

  private val Transcript = Path.of("modules/server/src/test/resources/calibre/commands.txt")

  private lazy val base =
    val b = AppConfig.load()
    b.modify(_.db).setTo(b.db.modify(_.url).setTo(testDb.get))
  private lazy val scratch = ScratchDatabases("calibre_cmds", base.db)

  override protected def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  private def source(ck: String, title: String, authors: List[String], year: Option[Int]) =
    Source(
      ck,
      title,
      authors,
      None,
      year,
      None,
      com.histmersion.api.library.Source.Status.Current,
      Nil,
      Nil,
      Nil,
      None,
      None,
      None,
      None,
      None,
      None
    )

  /** The sources the commands act on; `stored` ones get an original in the spool. */
  private def seed(ds: javax.sql.DataSource, spool: Path, chunk: Int): Unit =
    def put(s: Source, stored: Boolean = false): Unit =
      SourceFixtures.upsert(ds, s, chunk)
      if stored then
        val sha = (s.citekey.hashCode.abs.toString * 8).take(64).padTo(64, '0')
        Files.writeString(spool.resolve(s"$sha.pdf"), s"%PDF-1.4 original of ${s.citekey} ${"o" * 2048}")
        com.augustnagro.magnum.connect(ds):
          import com.augustnagro.magnum.*
          sql"UPDATE sources SET sha256 = $sha WHERE citekey = ${s.citekey}".update.run(): Unit
    put(
      source("zzcal_linked", "Bronze Age tin and its trade", List("Stocker, Sharon R."), Some(2019))
        .modify(
          _.doi).setTo(Some("10.1/zzcal.1")).modify(_.topics).setTo(List("tin", "trade")).modify(_.provenance).setTo(Some("unpaywall")).modify(_.calibreId).setTo(Some("1")).modify(_.summary).setTo(Some("Tin routes.")
        ),
      stored = true
    )
    put(
      source("zzcal_shared_a", "The Linear B tablets", List("Michael Ventris"), Some(1956)).modify(_.calibreId).setTo(Some("2"))
    )
    put(
      source("zzcal_shared_b", "Linear B, the tablets", List("John Chadwick"), Some(1956)).modify(_.calibreId).setTo(Some("2"))
    )
    put(source("zzcal_missing", "A lost book", List("Lost Author"), Some(1990)).modify(_.calibreId).setTo(Some("99")))
    put(source("zzcal_drift", "The original title", List("Original Author"), Some(2005)).modify(_.calibreId).setTo(Some("3")))
    put(source("zzcal_junk", "Real title", List("Real Author"), Some(2011)).modify(_.calibreId).setTo(Some("4")))
    put(source("zzcal_relink", "A Kindle-only book", List("Kay Format"), Some(2020)).modify(_.calibreId).setTo(Some("7")))
    put(
      source("zzcal_stored", "Stored paper", List("Ann Store"), Some(2010))
        .modify(_.topics).setTo(List("metal")).modify(_.provenance).setTo(Some("unpaywall")).modify(_.doi).setTo(Some("10.1/zzcal.stored")),
      stored = true
    )
    put(source("zzcal_unknown", "Unknown", List("Ann Other"), Some(2012)).modify(_.provenance).setTo(Some("oa")), stored = true)
    put(source("zzcal_nofile", "Metadata only", List("No File"), Some(2013)).modify(_.provenance).setTo(Some("openalex")))
    put(source("zzcal_calprov", "From Calibre", List("Cal Prov"), Some(2014)).modify(_.provenance).setTo(Some("calibre")))
    put(
      source("zzcal_pushme", "Pushed by hand", List("Push Me"), Some(2015)).modify(_.provenance).setTo(Some("calibre")),
      stored = true
    )
    put(source("zzcal_nolink", "Not linked", List("No Link"), Some(2016)).modify(_.provenance).setTo(Some("calibre")))
    put(
      source("zzcal2001_collision", "Collision of keys", List("Jane Zzcal"), Some(2001))
        .modify(_.provenance).setTo(Some("calibre"))
    )

  /** One step of the scenario: a CLI command (with the host-side originals directory or not), or a change made through
    * the server between two commands.
    */
  private enum Step:
    case Run(args: List[String], originals: Boolean = false)
    case Lock(locked: Boolean)
    case Patch(citekey: String, json: String)

  import Step.*
  private def run(args: String*): Step = Run(args.toList)

  private val Scenario: List[Step] = List(
    run("search", "Bronze"),
    run("search", "Bronze", "--json"),
    run("search", "", "--limit", "2"),
    run("search", "nomatch"),
    run("search"),
    run("search", "--unlinked"),
    run("search", "--unlinked", "--json"),
    run("search", "Hittite", "--unlinked", "--limit", "1"),
    Lock(true),
    run("search", "Bronze"),
    run("search", "--unlinked"),
    run("audit"),
    Lock(false),
    run("audit"),
    run("audit", "--json"),
    run("gaps"),
    run("gaps", "--json"),
    run("gaps", "--limit", "1"),
    run("gaps", "--quiet"),
    Run(List("gaps"), originals = true),
    Run(List("gaps", "--json"), originals = true),
    run("sync"),
    run("sync", "--json"),
    run("sync", "--apply"),
    Patch("zzcal_unknown", """{"metadata":{"title":"Now titled"}}"""),
    run("sync", "--apply", "--limit", "1000"),
    run("gaps", "--quiet"),
    run("gaps"),
    run("import", "99"),
    run("import", "7"),
    run("import", "4"),
    run("import", "8"),
    run("import", "8", "--citekey", "zzcal2001_collision", "--no-convert"),
    run("import", "5", "--no-wait"),
    run("import", "5", "--no-convert"),
    run("import", "6", "--json"),
    run("import", "3", "--citekey", "zzcal_relink", "--no-convert"),
    run("import", "5", "--acquire-item", "424242"),
    run("import", "5", "--no-convert", "--acquire-item", "1"),
    run("link", "zzcal_nofile", "6"),
    run("link", "zzcal_nofile", "99"),
    run("link", "zznosuch", "6"),
    run("link", "zzcal_nofile", "6", "--json"),
    run("push", "zzcal_missing"),
    run("push", "zzcal_pushme"),
    run("push", "zzcal_pushme", "--json"),
    run("push", "zzcal_linked", "--replace"),
    run("push", "zzcal_linked", "--replace", "--json"),
    run("push", "zzcal_missing", "--replace"),
    run("push", "zzcal_nolink", "--replace"),
    run("push", "zzcal_nolink"),
    Run(List("push", "zzcal_nolink"), originals = true),
    run("update", "zzcal_linked"),
    run("update", "zzcal_linked"),
    run("update", "zzcal_linked", "--json"),
    run("update", "zzcal_nolink"),
    run("update", "--all"),
    run("update", "--all", "--json"),
    run("update", "--all", "--apply"),
    run("update", "--all"),
    run("set", "1", "--field", "languages:eng"),
    run("set", "1", "--field", "bogus:x"),
    run("set", "1", "--field", "title:Unknown"),
    run("set", "1", "--field", "nocolon"),
    run("set", "99", "--field", "tags:x"),
    run("set", "1", "--field", "tags:a,b", "--field", "series:S"),
    run("audit")
  )

  /** The same commands, the same answers: the transcript of the whole scenario. */
  test("every calibre command over a fixture library — output, exit codes and calibredb calls"):
    val work = Files.createDirectories(Path.of("target/calibre-commands").toAbsolutePath)
    val runDir = Files.createTempDirectory(work, "run-")
    val master = CalibreFixture.create(runDir.resolve("master"))
    val lib = master.copyTo(runDir.resolve("library"))
    val spool = Files.createDirectories(runDir.resolve("spool"))
    val originals = Files.createDirectories(runDir.resolve("originals"))
    Files.createDirectories(originals.resolve("zzcal_nofile"))
    Files.writeString(originals.resolve("zzcal_nofile/paper.pdf"), s"%PDF-1.4 host copy ${"h" * 2048}")
    Files.createDirectories(originals.resolve("zzcal_nolink"))
    Files.writeString(originals.resolve("zzcal_nolink/paper.pdf"), s"%PDF-1.4 host copy ${"h" * 2048}")
    val home = Files.createDirectories(runDir.resolve("home"))
    val cliConf = Files.writeString(runDir.resolve("cli.conf"), "")
    val ds = scratch("run")
    val cfg = base.modify(
      _.db).setTo(base.db.modify(_.url).setTo(scratch.urlOf("calibre_cmds_run_test"))).modify(_.server).setTo(base.server.modify(_.publicUrl).setTo("http://histmersion.test")).modify(_.converter).setTo(base.converter.modify(_.spoolDir).setTo(spool.toString)
    )
    seed(ds, spool, cfg.limits.chunkChars)
    val transcript = supervised {
      val b = Composition
        .server(cfg, "127.0.0.1", 0, OfflineConverter(), plugins = CalibreCommandsSuite.plugins(lib))
        .start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val out = StringBuilder()
        Scenario.foreach {
          case Lock(l) =>
            lib.lock(l)
            out ++= s"## the Calibre GUI ${if l then "holds" else "released"} the library\n\n"
          case Patch(ck, json) =>
            val r = java.net.http.HttpClient
              .newBuilder()
              .version(java.net.http.HttpClient.Version.HTTP_1_1)
              .build()
              .send(
                java.net.http.HttpRequest
                  .newBuilder(java.net.URI.create(s"$url/api/sources/$ck"))
                  .header("content-type", "application/json")
                  .method("PATCH", java.net.http.HttpRequest.BodyPublishers.ofString(json))
                  .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()
              )
            out ++= s"## PATCH /api/sources/$ck $json -> ${r.statusCode}\n\n"
          case Run(args, withOriginals) =>
            val env = Map(
              "PATH" -> "/usr/bin:/bin",
              "HOME" -> home.toString,
              "HISTMERSION_CONFIG" -> cliConf.toString,
              "HISTMERSION_SERVER_URL" -> url,
              "HISTMERSION_CALIBRE_LIBRARY_DIR" -> lib.dir.toString
            ) ++ CalibreCommandsSuite.tools(lib, hostSide = withOriginals) ++
              Option.when(withOriginals)("HISTMERSION_CALIBRE_ORIGINALS_DIR" -> originals.toString)
            val (code, stdout, stderr) = CalibreCommandsSuite.cli(("calibre" :: args), env, home)
            out ++= s"## histmersion calibre ${args.map(CalibreCommandsSuite.shellWord).mkString(" ")}" +
              (if withOriginals then "   [cli.calibre.originals-dir set]" else "") + "\n"
            out ++= s"exit $code\n"
            out ++= "--- stdout\n" + stdout + (if stdout.isEmpty || stdout.endsWith("\n") then "" else "\n")
            out ++= "--- stderr\n" + stderr + (if stderr.isEmpty || stderr.endsWith("\n") then "" else "\n")
            out ++= "--- calls\n" + lib.takeLog().map(_ + "\n").mkString + "\n"
        }
        out ++= "## the linked sources afterwards\n"
        com.augustnagro.magnum.connect(ds):
          import com.augustnagro.magnum.*
          sql"""SELECT citekey, coalesce(calibre_id, '-'), coalesce(source, '-'), title FROM sources ORDER BY citekey"""
            .query[(String, String, String, String)]
            .run()
            .foreach((ck, id, prov, title) => out ++= s"$ck | $id | $prov | $title\n")
        out ++= "## the library afterwards\n" + Files.readString(lib.dir.resolve("books.json"), UTF_8) + "\n"
        CalibreCommandsSuite.normalise(out.result(), List(lib.dir, master.dir, spool, originals, home, runDir), url)
      finally b.stop()
    }
    if sys.env.get("HISTMERSION_UPDATE_TRAFFIC").contains("1") then
      Files.createDirectories(Transcript.getParent)
      Files.writeString(Transcript, transcript, UTF_8): Unit
    else
      val expected = Files.readString(Transcript, UTF_8)
      if transcript != expected then
        Files.writeString(work.resolve("commands.actual.txt"), transcript, UTF_8)
        assertEquals(transcript, expected, s"the calibre commands changed — see ${work.resolve("commands.actual.txt")}")

  /** A server without the calibre collection (the plugin off): the CLI reads the library on this machine, as before the
    * server could, and answers the same.
    */
  test("without the server's calibre collection the CLI runs the commands itself, with the same answers"):
    val work = Files.createDirectories(Path.of("target/calibre-commands").toAbsolutePath)
    val runDir = Files.createTempDirectory(work, "fallback-")
    val lib = CalibreFixture.create(runDir.resolve("master")).copyTo(runDir.resolve("library"))
    val spool = Files.createDirectories(runDir.resolve("spool"))
    val home = Files.createDirectories(runDir.resolve("home"))
    val cliConf = Files.writeString(runDir.resolve("cli.conf"), "")
    val ds = scratch("fallback")
    val cfg = base.modify(
      _.db).setTo(base.db.modify(_.url).setTo(scratch.urlOf("calibre_cmds_fallback_test"))).modify(_.converter).setTo(base.converter.modify(_.spoolDir).setTo(spool.toString)
    )
    seed(ds, spool, cfg.limits.chunkChars)
    val commands = List(
      List("search", "Bronze", "--json"),
      List("search", "--unlinked"),
      List("audit"),
      List("gaps"),
      List("gaps", "--quiet"),
      List("update", "--all")
    )
    def runAll(
        plugins: com.histmersion.pluginhost.Generation,
        tools: Map[String, String]
    ): List[(Int, String, String)] =
      supervised {
        val b = Composition.server(cfg, "127.0.0.1", 0, OfflineConverter(), plugins = plugins).start()
        val url = s"http://127.0.0.1:${b.port}"
        try
          commands.map { args =>
            val env = Map(
              "PATH" -> "/usr/bin:/bin",
              "HOME" -> home.toString,
              "HISTMERSION_CONFIG" -> cliConf.toString,
              "HISTMERSION_SERVER_URL" -> url,
              "HISTMERSION_CALIBRE_LIBRARY_DIR" -> lib.dir.toString
            ) ++ tools
            val (code, out, err) = CalibreCommandsSuite.cli("calibre" :: args, env, home)
            (code, out.replace(url, "<server>"), err.replace(url, "<server>"))
          }
        finally b.stop()
      }
    val onServer = runAll(CalibreCommandsSuite.plugins(lib), CalibreCommandsSuite.tools(lib, hostSide = false))
    assertEquals(lib.takeLog().size, 4, "the server read the library once per command but the gaps")
    val onHost =
      runAll(com.histmersion.server.ServerPlugins.offline, CalibreCommandsSuite.tools(lib, hostSide = true))
    assertEquals(lib.takeLog().size, 4, "the CLI read the library once per command but the gaps")
    assertEquals(onHost, onServer)
    assertEquals(onServer.map(_._1), List(0, 0, 0, 0, 1, 0))

object CalibreCommandsSuite:

  /** The plugins the test server runs with: the offline generation and the calibre plugin over the fixture library,
    * with a GUI process that never runs.
    */
  def plugins(lib: CalibreFixture): com.histmersion.pluginhost.Generation =
    CalibreFixture.refuseRealLibrary(lib.dir)
    val g = com.histmersion.server.ServerPlugins.offlineWith(
      List(com.histmersion.plugins.calibre.CalibrePlugin()),
      org.ekrich.config.ConfigFactory.parseString(
        s"""histmersion.plugins.calibre {
           |  library-path = "${lib.dir}"
           |  calibredb = "${lib.calibredb}"
           |  sqlite = "${lib.sqlite3}"
           |  gui-process = "/no/such/calibre-gui-process"
           |}""".stripMargin
      )
    )
    assert(g.registries.sourceCollections.contains("calibre"), g.report.toString)
    g

  /** The CLI's own Calibre tools: the fixture's where the command runs on this machine (a host-side original), else
    * programs that do not exist — the server runs the command, so the CLI never starts one, and the transcript is the
    * proof.
    */
  def tools(lib: CalibreFixture, hostSide: Boolean): Map[String, String] =
    if hostSide then
      Map(
        "HISTMERSION_CALIBRE_CALIBREDB" -> lib.calibredb.toString,
        "HISTMERSION_CALIBRE_SQLITE3" -> lib.sqlite3.toString
      )
    else
      Map("HISTMERSION_CALIBRE_CALIBREDB" -> "/no/such/calibredb", "HISTMERSION_CALIBRE_SQLITE3" -> "/no/such/sqlite3")

  def shellWord(s: String): String = if s.isEmpty then "\"\"" else s

  /** The CLI as its own process (the installed binary's entry point on this class path), in `env` alone. */
  /** The class path this suite was loaded from (sbt's test worker loads it through a class loader of its own), every
    * entry absolute: the CLI runs in a directory of its own.
    */
  private lazy val classPath: String =
    @scala.annotation.tailrec
    def urls(l: ClassLoader, acc: List[String]): List[String] = l match
      case null                       => acc
      case u: java.net.URLClassLoader =>
        urls(u.getParent, acc ++ u.getURLs.toList.map(x => Path.of(x.toURI).toAbsolutePath.toString))
      case other => urls(other.getParent, acc)
    (urls(getClass.getClassLoader, Nil) ++ System
      .getProperty("java.class.path")
      .split(java.io.File.pathSeparator)).distinct
      .mkString(java.io.File.pathSeparator)

  def cli(args: List[String], env: Map[String, String], cwd: Path): (Int, String, String) =
    val launcher = Path.of(System.getProperty("java.home"), "bin", "java").toString
    val pb = new ProcessBuilder(
      (List(
        launcher,
        "--sun-misc-unsafe-memory-access=allow",
        "-cp",
        classPath,
        "com.histmersion.cli.histmersionCli"
      ) ++ args).asJava
    )
    pb.environment().clear()
    pb.environment().putAll(env.asJava)
    pb.directory(cwd.toFile)
    val p = pb.start()
    p.getOutputStream.close()
    val err = java.util.concurrent.CompletableFuture.supplyAsync(() => new String(p.getErrorStream.readAllBytes, UTF_8))
    val out = new String(p.getInputStream.readAllBytes, UTF_8)
    val code = p.waitFor()
    (code, out, err.get())

  private val Uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r
  private val Time = "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?".r
  private val Original = "/[^\\s\"]*histmersion-original-[0-9]+".r

  /** What differs between two runs: the scratch directories, the port, a job's id, a clock reading. */
  def normalise(text: String, dirs: List[Path], url: String): String =
    val named = List("<library>", "<master>", "<spool>", "<originals>", "<home>", "<run>")
    val replaced = dirs.zip(named).foldLeft(text) { case (t, (d, n)) => t.replace(d.toString, n) }
    val t1 = Original.replaceAllIn(replaced.replace(url, "<server>"), "<tmp>")
    val t2 = Uuid.replaceAllIn(t1, "<uuid>")
    val t3 = "queued job \\S+".r.replaceAllIn(t2, "queued job <job>")
    Time.replaceAllIn(t3, "<time>")
