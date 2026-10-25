package com.histmersion.cli

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.sun.net.httpserver.HttpServer

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*

/** `histmersion plugin …` against a TEMPORARY install and data directory: the kit's scripts are fakes
  * (`histmersion-relink --describe` prints the JSON the test chose, `--resolve` records its arguments
  * and answers what the test chose; `histmersion-signing` checks a fake
  * signature), downloads go through the real curl to a local HTTP server, Maven to a fake repository
  * it serves. Nothing touches ~/Applications/Histmersion or the user's data directory. */
class PluginCmdSuite extends munit.FunSuite:

  final case class Env(home: Path, data: Path, ops: PluginOps, out: collection.mutable.Buffer[String]):
    def plugins: Path = data.resolve("plugins")
    def describe(id: String, version: String, spi: Int = 1, access: String = "open"): Unit =
      Files.writeString(home.resolve("describe.json"),
        s"""[{"file":"staged.jar","sha256":"x","plugins":[{"id":"$id","version":"$version","spiVersion":$spi,
           |"provides":["source-fetcher:$id"],"access":["$access"]}]}]""".stripMargin)
    def installed: List[String] = ops.jars.map(_.getFileName.toString)
    /** What the resolver answers: its report, its refusals, its exit code. */
    def resolver(report: String = "", refusals: String = "", rc: Int = 0): Unit =
      Files.writeString(home.resolve("resolve.out"), report)
      Files.writeString(home.resolve("resolve.err"), refusals)
      Files.writeString(home.resolve("resolve.rc"), rc.toString)
    def resolveCalls: List[String] =
      if Files.isRegularFile(home.resolve("resolve.args")) then Files.readAllLines(home.resolve("resolve.args")).asScala.toList else Nil

  private def script(p: Path, body: String): Unit =
    Files.createDirectories(p.getParent)
    Files.writeString(p, "#!/bin/sh\n" + body)
    Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rwxr-xr-x"))

  private val env = FunFixture[Env](
    _ =>
      val root = Files.createTempDirectory("plugin-cmd")
      val home = Files.createDirectories(root.resolve("install"))
      val data = Files.createDirectories(root.resolve("data dir")) // a space, as in "Application Support"
      script(home.resolve("bin/histmersion-relink"),
        """if [ "$1" = --describe ]; then echo "$@" > "$HISTMERSION_HOME/describe.args"; cat "$HISTMERSION_HOME/describe.json"; exit 0; fi
          |if [ "$1" = --resolve ]; then
          |  echo "$@" >> "$HISTMERSION_HOME/resolve.args"
          |  cat "$HISTMERSION_HOME/resolve.out" 2>/dev/null; cat "$HISTMERSION_HOME/resolve.err" >&2 2>/dev/null
          |  exit "$(cat "$HISTMERSION_HOME/resolve.rc" 2>/dev/null || echo 0)"
          |fi
          |echo "$@" > "$HISTMERSION_HOME/relink.args"
          |exit "$(cat "$HISTMERSION_HOME/relink.rc" 2>/dev/null || echo 0)"
          |""".stripMargin)
      script(home.resolve("bin/histmersion-signing"),
        """case "$1" in
          |  verify-file) if grep -q good "$3"; then sed -n 's/^signer=//p' "$3"; else echo "bad signature" >&2; exit 2; fi ;;
          |  sign-plugins) ls "$HISTMERSION_DATA_DIR/plugins" > "$HISTMERSION_DATA_DIR/signed-manifest"; echo "plugins manifest signed" ;;
          |  status) echo "- GPG provenance: (fake)" ;;
          |esac
          |""".stripMargin)
      Files.createDirectories(home.resolve("kit"))
      Files.writeString(home.resolve("kit/kit.json"),
        """{"format":1,"version":"0.1.0","commit":"abc","graalvm":"25.0.4.1.1","spiVersion":1,"mainClass":"M","files":[]}""")
      val out = collection.mutable.Buffer.empty[String]
      val cfg = CliPluginsConfig("", "", "curl")
      Env(home, data, PluginOps(LocalInstall(home, data), cfg, out = s => out += s, confirm = _ => true), out)
    ,
    e => Files.walk(e.home.getParent).sorted(java.util.Comparator.reverseOrder()).forEach(p => { val _ = Files.deleteIfExists(p) })
  )

  private def jarFile(dir: Path, name: String, content: String = "jar bytes"): Path =
    Files.writeString(Files.createDirectories(dir).resolve(name), content)

  private def withMaven(e: Env)(cfgRepos: String => CliPluginsConfig)(f: (Env, Path, String) => Unit): Unit =
    val repo = Files.createDirectories(e.home.getParent.resolve("repo"))
    val srv  = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    srv.createContext("/", ex => {
      val p = repo.resolve(ex.getRequestURI.getPath.stripPrefix("/"))
      if Files.isRegularFile(p) then
        val b = Files.readAllBytes(p)
        ex.sendResponseHeaders(200, b.length.toLong); ex.getResponseBody.write(b)
      else ex.sendResponseHeaders(404, -1)
      ex.close()
    })
    srv.start()
    try
      val url = s"http://127.0.0.1:${srv.getAddress.getPort}"
      f(e.copy(ops = PluginOps(LocalInstall(e.home, e.data), cfgRepos(url), out = s => e.out += s, confirm = _ => true)), repo, url)
    finally srv.stop(0)

  private def sha(algorithm: String, b: Array[Byte]) = MessageDigest.getInstance(algorithm).digest(b).map(x => f"$x%02x").mkString

  // ── locating the install ──────────────────────────────────────────────────────────────────────

  env.test("locate: the configured install, the one the executable sits in, else refused without a kit"): e =>
    val env = Map("HOME" -> e.home.getParent.toString, "HISTMERSION_DATA_DIR" -> e.data.toString).get
    val cfg = CliPluginsConfig(e.home.toString, "", "curl")
    assertEquals(LocalInstall.locate(cfg, "", env, "Mac OS X", None).map(_.home), Right(e.home))
    // ~/bin/histmersion → <install>/bin/histmersion
    val cliBin = Files.writeString(e.home.resolve("bin/histmersion"), "")
    val link   = Files.createSymbolicLink(Files.createDirectories(e.home.getParent.resolve("userbin")).resolve("histmersion"), cliBin)
    assertEquals(LocalInstall.locate(cfg.copy(installDir = ""), "", env, "Linux", Some(link)).map(_.home), Right(e.home.toRealPath()))
    val none = LocalInstall.locate(cfg.copy(installDir = e.data.toString), "", env, "Linux", None)
    assert(none.left.exists(_.contains("no Histmersion install with a relink kit")), none)
    assertEquals(LocalInstall.locate(cfg, e.data.toString, env, "Linux", None).map(_.data), Right(e.data))
    assertEquals(LocalInstall.locate(cfg, "", env, "Mac OS X", None).map(_.data),
      Right(e.home.getParent.resolve("Library/Application Support/Histmersion")))

  test("the plugin commands refuse a server on another machine"):
    val r = PluginCmd.local(Client("https://histmersion.example.org"))
    assert(r.left.exists(e => e.message.contains("THIS machine") && e.exitCode == ExitCode.Usage), r)

  // ── add ───────────────────────────────────────────────────────────────────────────────────────

  env.test("add <path>: described, shown, installed as <id>-<version>.jar, indexed"): e =>
    e.describe("hello", "0.1.0")
    val src = jarFile(e.home.getParent.resolve("downloads"), "histmersion-plugin-hello_3-0.1.0.jar")
    val r = e.ops.add(src.toString, AddOptions(yes = true))
    assertEquals(r.map(_.file), Right("hello-0.1.0.jar"))
    assertEquals(e.installed, List("hello-0.1.0.jar"))
    val shown = e.out.mkString("\n")
    assert(shown.contains("plugin hello 0.1.0 · SPI 1 · access: open · provides source-fetcher:hello"), shown)
    assert(shown.contains(s"SHA-256 ${sha("SHA-256", Files.readAllBytes(src))}"), shown)
    assertEquals(e.ops.index.jars.map(j => (j.file, j.source, j.plugins.map(_.id))),
      List(("hello-0.1.0.jar", src.toString, List("hello"))))
    assertEquals(Files.list(e.plugins.resolve(".staging")).count(), 0L, "the staging file is gone")

  env.test("add: a foreign SPI and a checksum mismatch are refused; the same id from a dropped-in jar is replaced"): e =>
    val src = jarFile(e.home.getParent.resolve("downloads"), "p.jar")
    e.describe("hello", "0.1.0", spi = 7)
    val spi = e.ops.add(src.toString, AddOptions(yes = true))
    assert(spi.left.exists(_.message.contains("built for plugin SPI 7; this Histmersion speaks SPI 1")), spi)
    e.describe("hello", "0.1.0")
    val sum = e.ops.add(src.toString, AddOptions(yes = true, sha256 = Some("0" * 64)))
    assert(sum.left.exists(_.message.contains("SHA-256 mismatch")), sum)
    assertEquals(e.installed, Nil)
    // a jar dropped in by hand already provides `portal`
    jarFile(e.plugins, "portal-2.0.jar")
    e.describe("portal", "3.0")
    val taken = e.ops.add(src.toString, AddOptions(yes = true))
    assertEquals(taken.map(_.file), Right("portal-3.0.jar"), "another version of the same id REPLACES it")
    assertEquals(e.installed, List("portal-3.0.jar"))

  env.test("add: a newer version replaces the older; without --yes and no terminal it is not installed"): e =>
    val src = jarFile(e.home.getParent.resolve("downloads"), "p.jar")
    e.describe("hello", "0.1.0")
    assert(e.ops.add(src.toString, AddOptions(yes = true)).isRight)
    e.describe("hello", "0.2.0")
    assert(e.ops.add(src.toString, AddOptions(yes = true)).isRight)
    assertEquals(e.installed, List("hello-0.2.0.jar"))
    assertEquals(e.ops.index.jars.map(_.file), List("hello-0.2.0.jar"))
    val shy = PluginOps(LocalInstall(e.home, e.data), CliPluginsConfig("", "", "curl"), out = _ => (), confirm = _ => false)
    e.describe("other", "1.0")
    assert(shy.add(src.toString, AddOptions()).left.exists(_.message.contains("not installed")))
    assertEquals(e.installed, List("hello-0.2.0.jar"))

  env.test("add <url>: downloaded with curl from a local server; a 404 is an error"): e =>
    withMaven(e)(_ => CliPluginsConfig("", "", "curl")) { (e, repo, url) =>
      Files.writeString(Files.createDirectories(repo.resolve("dl")).resolve("hello.jar"), "served bytes")
      e.describe("hello", "0.1.0")
      val r = e.ops.add(s"$url/dl/hello.jar", AddOptions(yes = true))
      assertEquals(r.map(_.file), Right("hello-0.1.0.jar"))
      assertEquals(Files.readString(e.plugins.resolve("hello-0.1.0.jar")), "served bytes")
      assertEquals(e.ops.index.jars.map(_.source), List(s"$url/dl/hello.jar"))
      assert(e.out.exists(_.startsWith(s"file hello-0.1.0.jar · SHA-256 ${sha("SHA-256", "served bytes".getBytes(UTF_8))}")), e.out)
      val missing = e.ops.add(s"$url/dl/none.jar", AddOptions(yes = true))
      assert(missing.left.exists(_.message.contains("not found")), missing)
    }

  env.test("add group:artifact:version: resolved from the first repository that has it, SHA-1 checked"): e =>
    withMaven(e)(url => CliPluginsConfig("", s"$url/empty,$url/maven2", "curl")) { (e, repo, _) =>
      val dir = Files.createDirectories(repo.resolve("maven2/org/example/hello_3/0.1.0"))
      val bytes = "the plugin".getBytes(UTF_8)
      Files.write(dir.resolve("hello_3-0.1.0.jar"), bytes)
      Files.writeString(dir.resolve("hello_3-0.1.0.jar.sha1"), sha("SHA-1", bytes) + "\n")
      val pom = "<project>the POM</project>".getBytes(UTF_8)
      Files.write(dir.resolve("hello_3-0.1.0.pom"), pom)
      Files.writeString(dir.resolve("hello_3-0.1.0.pom.sha1"), sha("SHA-1", pom) + "\n")
      e.describe("hello", "0.1.0")
      val r = e.ops.add("org.example::hello:0.1.0", AddOptions(yes = true))
      assertEquals(r.map(_.file), Right("hello-0.1.0.jar"))
      assert(e.ops.index.jars.head.source.endsWith("/maven2/org/example/hello_3/0.1.0/hello_3-0.1.0.jar"), e.ops.index)
      assertEquals(Files.readString(e.plugins.resolve("hello-0.1.0.jar")), "the plugin")
      assertEquals(Files.readString(e.plugins.resolve("hello-0.1.0.pom")), "<project>the POM</project>", "its POM beside it")
      // a corrupted download (the published checksum does not match) is refused
      Files.writeString(dir.resolve("hello_3-0.1.0.jar.sha1"), "0" * 40)
      val bad = e.ops.add("org.example::hello:0.1.0", AddOptions(yes = true))
      assert(bad.left.exists(_.message.contains("SHA-1 mismatch")), bad)
      val missing = e.ops.add("org.example:nothing:1.0", AddOptions(yes = true))
      assert(missing.left.exists(_.message.contains("not resolved from")), missing)
    }

  env.test("add with a POM: its dependencies resolved first and described with them, shown, the POM kept beside the jar"): e =>
    e.describe("oabooks", "0.1.0")
    e.resolver(report = "dependency: org.scala-lang.modules:scala-xml_3:2.4.0 (for add.jar)\nprovided by Histmersion: 1 module(s) (org.ekrich:sconfig_3)\n")
    val dl  = e.home.getParent.resolve("downloads")
    val src = jarFile(dl, "oabooks_3-0.1.0.jar")
    Files.writeString(dl.resolve("oabooks_3-0.1.0.pom"), "<project>deps</project>")
    assertEquals(e.ops.add(src.toString, AddOptions(yes = true)).map(_.file), Right("oabooks-0.1.0.jar"))
    assertEquals(Files.readString(e.plugins.resolve("oabooks-0.1.0.pom")), "<project>deps</project>")
    val List(staged, all) = e.resolveCalls: @unchecked
    assert(staged.matches("--resolve --lib .*/\\.staging/add-[0-9]+/lib .*/\\.staging/add-[0-9]+/oabooks_3-0\\.1\\.0\\.jar"), staged)
    assertEquals(all, "--resolve", "then every installed plugin's, into plugins/lib")
    assert(Files.readString(e.home.resolve("describe.args")).matches("--describe --lib .*/\\.staging/add-[0-9]+/lib .*\\n"), "described with its dependencies")
    assert(e.out.contains("dependency: org.scala-lang.modules:scala-xml_3:2.4.0 (for add.jar)"), e.out)
    assert(e.out.contains("provided by Histmersion: 1 module(s) (org.ekrich:sconfig_3)"), e.out)
    assertEquals(Files.list(e.plugins.resolve(".staging")).count(), 0L, "the staged jar, POM and lib are gone")

  env.test("add: a plugin that needs a newer version of a library Histmersion ships is refused, both versions named"): e =>
    e.describe("oabooks", "0.1.0")
    e.resolver(refusals = "WARNING: a JVM warning\nrefused: p.jar needs io.circe:circe-core_3 0.14.20, but this Histmersion ships 0.14.17 — build against 0.14.17\n", rc = 2)
    val r = e.ops.add(jarFile(e.home.getParent.resolve("d"), "p.jar").toString, AddOptions(yes = true))
    assertEquals(r.left.map(_.message), Left("refused:\n  - p.jar needs io.circe:circe-core_3 0.14.20, but this Histmersion ships 0.14.17 — build against 0.14.17"))
    assertEquals(e.installed, Nil)
    e.resolver(refusals = "could not resolve the plugins' dependencies: not found: org.example:gone_3:1.0", rc = 3)
    val failed = e.ops.add(jarFile(e.home.getParent.resolve("d"), "p.jar").toString, AddOptions(yes = true))
    assert(failed.left.exists(_.message.contains("dependencies could not be resolved: could not resolve")), failed)

  env.test("add --repo: looked in first for the plugin, and passed on for its dependencies"): e =>
    withMaven(e)(url => CliPluginsConfig("", s"$url/central", "curl")) { (e, repo, url) =>
      val dir = Files.createDirectories(repo.resolve("mine/org/example/hello_3/0.1.0"))
      List("jar" -> "the plugin", "pom" -> "<project/>").foreach { (ext, text) =>
        Files.writeString(dir.resolve(s"hello_3-0.1.0.$ext"), text)
        Files.writeString(dir.resolve(s"hello_3-0.1.0.$ext.sha1"), sha("SHA-1", text.getBytes(UTF_8)))
      }
      e.describe("hello", "0.1.0")
      e.resolver()
      assert(e.ops.add("org.example::hello:0.1.0", AddOptions(yes = true, repos = List(s"$url/mine"))).isRight)
      assert(e.resolveCalls.forall(_.contains(s"--repo $url/mine --repo $url/central")), e.resolveCalls)
    }

  env.test("rm: the POM goes with the jar, and lib/ is resolved again; a failed resolution only warns"): e =>
    e.describe("hello", "0.1.0")
    val dl = e.home.getParent.resolve("d")
    Files.writeString(Files.createDirectories(dl).resolve("h.pom"), "<project/>")
    assert(e.ops.add(jarFile(dl, "h.jar").toString, AddOptions(yes = true)).isRight)
    assert(Files.isRegularFile(e.plugins.resolve("hello-0.1.0.pom")))
    e.resolver(refusals = "offline", rc = 3)
    assertEquals(e.ops.rm("hello"), Right(List("hello-0.1.0.jar")))
    assert(!Files.exists(e.plugins.resolve("hello-0.1.0.pom")))
    assertEquals(e.resolveCalls.last, "--resolve")
    assert(e.out.exists(_.startsWith("warning: the plugins' dependencies were not resolved now (offline)")), e.out)

  env.test("signatures: required when GPG provenance is set up; the signer pinned per plugin id"): e =>
    Files.writeString(Files.createDirectories(e.data.resolve("signing")).resolve("gpg-key"), "MYKEY\n")
    e.describe("hello", "0.1.0")
    val dl  = e.home.getParent.resolve("downloads")
    val src = jarFile(dl, "hello.jar")
    val unsigned = e.ops.add(src.toString, AddOptions(yes = true))
    assert(unsigned.left.exists(_.message.contains("no signature (.asc)")), unsigned)
    Files.writeString(dl.resolve("hello.jar.asc"), "good\nsigner=AAAA\n")
    assert(e.ops.add(src.toString, AddOptions(yes = true)).isRight)
    assertEquals(e.ops.index.jars.map(_.signer), List(Some("AAAA")))
    assertEquals(Files.readString(e.data.resolve("signed-manifest")).trim, "hello-0.1.0.jar\nindex.json", "re-signed")
    // the next version signed by another key: refused (trust on first use)
    Files.writeString(dl.resolve("hello.jar.asc"), "good\nsigner=BBBB\n")
    e.describe("hello", "0.2.0")
    val other = e.ops.add(src.toString, AddOptions(yes = true))
    assert(other.left.exists(_.message.contains("signed by AAAA")), other)
    Files.writeString(dl.resolve("hello.jar.asc"), "forged")
    assert(e.ops.add(src.toString, AddOptions(yes = true)).left.exists(_.message.contains("signature not verified")))
    assert(e.ops.add(src.toString, AddOptions(yes = true, unsigned = true)).isRight, "--unsigned installs anyway")
    assertEquals(e.installed, List("hello-0.2.0.jar"))

  // ── rm, list, status, rebuild ─────────────────────────────────────────────────────────────────

  env.test("rm: by id or file name; an unknown one is a usage error"): e =>
    e.describe("hello", "0.1.0")
    assert(e.ops.add(jarFile(e.home.getParent.resolve("d"), "h.jar").toString, AddOptions(yes = true)).isRight)
    jarFile(e.plugins, "dropped-1.0.jar")
    assertEquals(e.ops.rm("hello"), Right(List("hello-0.1.0.jar")))
    assertEquals(e.ops.rm("dropped-1.0.jar"), Right(List("dropped-1.0.jar")))
    assertEquals(e.installed, Nil)
    assertEquals(e.ops.index.jars, Nil)
    assertEquals(e.ops.rm("nope").left.map(_.exitCode), Left(ExitCode.Usage))

  env.test("list and status: what the installed binary has, and whether a rebuild is needed"): e =>
    Files.writeString(e.home.resolve("bin/histmersion-server.plugin-set.json"),
      PluginKit.renderSet(PluginKit.pluginSet(Nil, 1, "native", None, None)))
    val (empty, _) = e.ops.status(None, serverAnswered = false)
    assertEquals(empty.verdict, PluginKit.Verdict.UpToDate)
    e.describe("hello", "0.1.0")
    assert(e.ops.add(jarFile(e.home.getParent.resolve("d"), "h.jar").toString, AddOptions(yes = true)).isRight)
    val (st, lines) = e.ops.status(Some(PluginsReport(1)), serverAnswered = true)
    assertEquals(st.verdict, PluginKit.Verdict.RelinkNeeded)
    assertEquals(st.added, List("hello-0.1.0.jar"))
    assert(lines.exists(_.contains("kit: 0.1.0 (abc) · GraalVM 25.0.4.1.1 · plugin SPI 1")), lines)
    assert(lines.contains("- GPG provenance: (fake)"), lines)
    val listed = e.ops.list(None).mkString("\n")
    assert(listed.contains("- hello-0.1.0.jar — hello 0.1.0 (open) · NOT in the binary yet"), listed)
    // after a relink the record has it
    val sha = e.ops.sha256(e.plugins.resolve("hello-0.1.0.jar"))
    val set = PluginKit.pluginSet(List(PluginJar("hello-0.1.0.jar", sha)), 1, "native", None, None)
    Files.writeString(e.home.resolve("bin/histmersion-server.plugin-set.json"), PluginKit.renderSet(set))
    assert(e.ops.list(None).mkString.contains("in the binary"))
    assertEquals(e.ops.status(Some(PluginsReport(1, set = Some(set))), true)._1.verdict, PluginKit.Verdict.UpToDate)
    assertEquals(e.ops.status(Some(PluginsReport(1)), true)._1.verdict, PluginKit.Verdict.RestartNeeded)
    // linked with its POM and a lib/ jar: a changed POM or lib/ jar beside the same jar needs a rebuild
    val pom = e.plugins.resolve("hello-0.1.0.pom"); Files.writeString(pom, "<project/>")
    Files.createDirectories(e.plugins.resolve("lib"))
    Files.writeString(e.plugins.resolve("lib/dep-1.0.jar"), "dep"); Files.writeString(e.plugins.resolve("lib/classpath"), "dep-1.0.jar\n")
    val withDeps = PluginKit.pluginSet(List(PluginJar("hello-0.1.0.jar", sha)), 1, "native", None, None, e.ops.dependencyEntries)
    Files.writeString(e.home.resolve("bin/histmersion-server.plugin-set.json"), PluginKit.renderSet(withDeps))
    assertEquals(e.ops.status(Some(PluginsReport(1, set = Some(withDeps))), true)._1.verdict, PluginKit.Verdict.UpToDate)
    Files.writeString(pom, "<project><dependencies/></project>")
    val (pomChanged, _) = e.ops.status(Some(PluginsReport(1, set = Some(withDeps))), true)
    assertEquals((pomChanged.verdict, pomChanged.changed), (PluginKit.Verdict.RelinkNeeded, List("hello-0.1.0.pom")))
    Files.writeString(pom, "<project/>"); Files.writeString(e.plugins.resolve("lib/dep-1.0.jar"), "other")
    assertEquals(e.ops.status(Some(PluginsReport(1, set = Some(withDeps))), true)._1.changed, List("lib/dep-1.0.jar"))

  env.test("rebuild and rollback run histmersion-relink with this install's directories"): e =>
    assertEquals(e.ops.rebuild(List("--no-restart")), Right(()))
    assertEquals(Files.readString(e.home.resolve("relink.args")).trim, "--no-restart")
    Files.writeString(e.home.resolve("relink.rc"), "3")
    assertEquals(e.ops.rebuild(List("--rollback")).left.map(_.exitCode), Left(ExitCode.Refused))
