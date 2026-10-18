package com.histmersion.plugins.vaults

import com.histmersion.plugin.*
import org.ekrich.config.ConfigFactory

import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermissions
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/** The vaults against FAKE command-line tools: shell scripts in a temporary directory that the test
  * context offers as the whole PATH. No real vault, no real `keepassxc-cli`/`op`/`bw`, is ever run. */
class VaultsSuite extends munit.FunSuite:

  private def bin(): Path = Files.createTempDirectory("histmersion-fake-bin")

  private def script(dir: Path, name: String, body: String): Path =
    val f = dir.resolve(name)
    Files.writeString(f, "#!/bin/sh\n" + body)
    Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rwx------"))
    f

  /** A context with an environment of its own (PATH = the fake tools), secrets from `secrets`. */
  private final class Ctx(envVars: Map[String, String], secrets: Map[SecretRef, String] = Map.empty) extends PluginContext:
    var vaults: List[(String, SecretVault)] = Nil // what the plugin registered (test-local)
    def http = new PluginHttp { def get(url: String, headers: Map[String, String]) = Left("no network in tests") }
    val log  = new PluginLog { def info(m: String) = (); def warn(m: String) = () }
    def env(name: String) = envVars.get(name)
    def secret(s: Secret) = secrets.get(s.ref).toRight(s"no value for ${s.ref.shown}")
    val secretVaults = new Registry[SecretVault]:
      def add(id: String, a: SecretVault) =
        if vaults.exists(_._1 == id) then Left("dup") else { vaults = vaults :+ (id -> a); Right(()) }

  private def decode(p: Plugin, hocon: String): p.Config =
    val defs = ConfigFactory.parseResources(p.getClass.getClassLoader, p.defaultsResource).resolve()
    val c = ConfigFactory.parseString(hocon).withFallback(defs).withoutPath("enabled")
    p.configReader.from(c.root).fold(e => fail(e.toString), identity)

  private def registered(p: Plugin, hocon: String, ctx: Ctx): SecretVault =
    p.register(decode(p, hocon), ctx).fold(e => fail(e), identity)
    assertEquals(ctx.vaults.map(_._1), List(p.info.id))
    ctx.vaults.head._2

  // ── Subprocess ──────────────────────────────────────────────────────────────────────────────

  test("locate: a bare name on the given PATH, a full path as given, and what is missing"):
    val d = bin()
    val t = script(d, "tool", "exit 0\n")
    assertEquals(Subprocess.locate("tool", Some(s"/nonexistent:$d")), Right(t.toString))
    assertEquals(Subprocess.locate(t.toString, None), Right(t.toString))
    assert(Subprocess.locate("tool", Some("/nonexistent")).left.exists(_.contains("not found on the PATH")))
    assert(Subprocess.locate("tool", None).isLeft)
    assert(Subprocess.locate(d.resolve("nope").toString, None).left.exists(_.contains("not an executable")))
    assert(Subprocess.locate(" ", Some(d.toString)).isLeft)

  test("run: argv without a shell, stdin, extra env, exit code and both streams"):
    val d = bin()
    val t = script(d, "echoer", "IFS= read -r line\nprintf '%s|%s|%s\\n' \"$1\" \"$line\" \"$EXTRA\"\necho oops >&2\nexit 3\n")
    val r = Subprocess.run(List(t.toString, "a b; rm -rf /tmp/x"), Map("EXTRA" -> "e"), Some("in\n"), 5.seconds)
    assertEquals(r, Right(Subprocess.Result(3, "a b; rm -rf /tmp/x|in|e\n", "oops\n")))

  test("run: a tool that waits on a prompt sees end-of-file; one that hangs is killed at the timeout"):
    val d = bin()
    val prompt = script(d, "prompt", "IFS= read -r pw || { echo 'no input' >&2; exit 1; }\necho \"$pw\"\n")
    assertEquals(Subprocess.run(List(prompt.toString), Map.empty, None, 5.seconds).map(_.exit), Right(1))
    val slow = script(d, "slow", "sleep 30\n")
    val t0 = System.nanoTime
    val r = Subprocess.run(List(slow.toString), Map.empty, None, 1.second)
    assert(r.left.exists(_.contains("did not answer within 1 s")), r)
    assert((System.nanoTime - t0).nanos < 10.seconds)

  test("pure helpers: the problem line, the value without its line break, arguments that look like options"):
    assertEquals(Subprocess.problem("\n  Error: locked \nmore"), "Error: locked")
    assertEquals(Subprocess.problem(""), "no message")
    assertEquals(Subprocess.value(" v \n"), " v ")
    assertEquals(Subprocess.value("v\r\n"), "v")
    assertEquals(Subprocess.plain("x", "--help"), Left("x may not start with '-'"))
    assertEquals(Subprocess.plain("x", ""), Left("x is empty"))
    assertEquals(Vaults.entryAndField("A/B#UserName", "password"), ("A/B", "UserName"))
    assertEquals(Vaults.entryAndField("A#B#c", "password"), ("A#B", "c"))
    assertEquals(Vaults.entryAndField("A/B", "password"), ("A/B", "password"))
    assertEquals(Vaults.entryAndField("A/B#", "password"), ("A/B", "password"))
    assertEquals(Vaults.expand("~/x.kdbx", Some("/h")), "/h/x.kdbx")
    assertEquals(Vaults.expand("/a/~/x", Some("/h")), "/a/~/x")

  // ── KeePassXC ───────────────────────────────────────────────────────────────────────────────

  /** A fake keepassxc-cli: the master password must arrive on stdin (or a key file with --no-password). */
  private def fakeKeePassXc(d: Path): Path =
    script(d, "keepassxc-cli", """printf '%s\n' "$@" > "$(dirname "$0")/kp-args"
      |for a; do entry=$a; done
      |nopw=""; key=""; attr=""; prev=""
      |for a; do
      |  [ "$a" = "--no-password" ] && nopw=1
      |  [ "$prev" = "-k" ] && key=$a
      |  [ "$prev" = "-a" ] && attr=$a
      |  prev=$a
      |done
      |if [ -n "$nopw" ]; then
      |  [ -n "$key" ] || { echo "Error while reading the database: Invalid credentials were provided" >&2; exit 1; }
      |else
      |  IFS= read -r pw
      |  [ "$pw" = "master-pw" ] || { echo "Error while reading the database: Invalid credentials were provided" >&2; exit 1; }
      |fi
      |case "$entry" in
      |  missing) echo "Could not find entry with path missing." >&2; exit 1;;
      |  slow) sleep 30;;
      |esac
      |printf '%s\n' "value of $entry/$attr"
      |""".stripMargin)

  test("keepassxc: the password comes from a Secret over stdin, never the command line; the attribute defaults"):
    val d = bin(); fakeKeePassXc(d)
    val db = Files.writeString(d.resolve("v.kdbx"), "")
    val ctx = Ctx(Map("PATH" -> d.toString), Map(SecretRef.Env("KP") -> "master-pw"))
    val v = registered(KeePassXcPlugin(), s"""database = "$db", password = "env:KP"""", ctx)
    assertEquals(v.name, "keepassxc")
    assertEquals(v.resolve("Histmersion/Portal"), Right("value of Histmersion/Portal/password"))
    assertEquals(v.resolve("Histmersion/Portal#UserName"), Right("value of Histmersion/Portal/UserName"))
    val args = Files.readAllLines(d.resolve("kp-args")).asScala.toList
    assertEquals(args, List("show", "-q", "-a", "UserName", db.toString, "Histmersion/Portal"))
    assert(!args.exists(_.contains("master-pw")))

  test("keepassxc: a key file alone opens with --no-password; no password and no key file is locked"):
    val d = bin(); fakeKeePassXc(d)
    val db  = Files.writeString(d.resolve("v.kdbx"), "")
    val key = Files.writeString(d.resolve("v.key"), "k")
    val withKey = registered(KeePassXcPlugin(), s"""database = "$db", key-file = "$key"""", Ctx(Map("PATH" -> d.toString)))
    assertEquals(withKey.resolve("E"), Right("value of E/password"))
    assertEquals(Files.readAllLines(d.resolve("kp-args")).asScala.toList,
      List("show", "-q", "-a", "password", "-k", key.toString, "--no-password", db.toString, "E"))
    val bare = registered(KeePassXcPlugin(), s"""database = "$db"""", Ctx(Map("PATH" -> d.toString)))
    assert(bare.resolve("E").left.exists(_.contains("the database is locked")), bare.resolve("E"))

  test("keepassxc: a wrong password is locked, a missing entry is named, a hung tool times out, options are refused"):
    val d = bin(); fakeKeePassXc(d)
    val db = Files.writeString(d.resolve("v.kdbx"), "")
    val wrong = registered(KeePassXcPlugin(), s"""database = "$db", password = "env:KP"""",
      Ctx(Map("PATH" -> d.toString), Map(SecretRef.Env("KP") -> "nope")))
    assert(wrong.resolve("E").left.exists(_.contains("locked")))
    val v = registered(KeePassXcPlugin(), s"""database = "$db", password = "env:KP", timeout-seconds = 1""",
      Ctx(Map("PATH" -> d.toString), Map(SecretRef.Env("KP") -> "master-pw")))
    assertEquals(v.resolve("missing"), Left("keepassxc: no entry 'missing'"))
    assert(v.resolve("slow").left.exists(_.contains("did not answer within 1 s")))
    assert(v.resolve("--help").left.exists(_.contains("may not start with '-'")))
    assert(v.resolve("E#-x").left.exists(_.contains("may not start with '-'")))

  test("keepassxc: registration names what is missing — the database, the tool, a secret"):
    val d = bin()
    val db = Files.writeString(d.resolve("v.kdbx"), "")
    val p = KeePassXcPlugin()
    def reg(h: String, ctx: Ctx) = p.register(decode(p, h), ctx)
    assert(reg("", Ctx(Map("PATH" -> d.toString))).left.exists(_.contains("set database")))
    assert(reg(s"""database = "${d.resolve("nope.kdbx")}"""", Ctx(Map("PATH" -> d.toString))).left.exists(_.contains("does not exist")))
    assert(reg(s"""database = "$db"""", Ctx(Map("PATH" -> d.toString))).left.exists(_.contains("keepassxc-cli was not found")))
    fakeKeePassXc(d)
    assert(reg(s"""database = "$db", password = "env:UNSET"""", Ctx(Map("PATH" -> d.toString))).left.exists(_.contains("env:UNSET")))
    assert(reg(s"""database = "$db", key-file = "${d.resolve("nokey")}"""", Ctx(Map("PATH" -> d.toString))).left.exists(_.contains("key-file")))

  // ── 1Password ───────────────────────────────────────────────────────────────────────────────

  private def fakeOp(d: Path): Path =
    script(d, "op", """printf '%s\n' "$@" > "$(dirname "$0")/op-args"
      |[ "$OP_SERVICE_ACCOUNT_TOKEN" = "sa-token" ] || { echo "[ERROR] You are not currently signed in." >&2; exit 1; }
      |case "$2" in
      |  op://Private/Portal/password) printf 'op-value\n';;
      |  *) echo "[ERROR] \"$2\" isn't an item." >&2; exit 1;;
      |esac
      |""".stripMargin)

  test("1password: op read with the service-account token from a Secret, in the tool's environment only"):
    val d = bin(); fakeOp(d)
    val ctx = Ctx(Map("PATH" -> d.toString), Map(SecretRef.Env("OPT") -> "sa-token"))
    val v = registered(OnePasswordPlugin(), """service-account-token = "env:OPT", account = "me"""", ctx)
    assertEquals(v.name, "1password")
    assertEquals(v.resolve("op://Private/Portal/password"), Right("op-value"))
    assertEquals(v.resolve("Private/Portal/password"), Right("op-value"))
    assertEquals(Files.readAllLines(d.resolve("op-args")).asScala.toList,
      List("read", "op://Private/Portal/password", "--account", "me"))
    assertEquals(v.resolve("Private/Nope/password"), Left("1password: no item at op://Private/Nope/password"))
    val unsigned = registered(OnePasswordPlugin(), "", Ctx(Map("PATH" -> d.toString)))
    assert(unsigned.resolve("Private/Portal/password").left.exists(_.contains("not signed in")))

  // ── Bitwarden ───────────────────────────────────────────────────────────────────────────────

  private def fakeBw(d: Path): Path =
    script(d, "bw", """printf '%s\n' "$@" > "$(dirname "$0")/bw-args"
      |[ "$BW_SESSION" = "sess" ] || { echo "Vault is locked." >&2; exit 1; }
      |case "$3" in
      |  item-1) printf '%s\n' "bw $2";;
      |  dup) echo "More than one result was found." >&2; exit 1;;
      |  *) echo "Not found." >&2; exit 1;;
      |esac
      |""".stripMargin)

  test("bitwarden: bw get <field> <item> with BW_SESSION from a Secret; locked, ambiguous and missing are said"):
    val d = bin(); fakeBw(d)
    val v = registered(BitwardenPlugin(), """session = "env:BWS"""",
      Ctx(Map("PATH" -> d.toString), Map(SecretRef.Env("BWS") -> "sess")))
    assertEquals(v.resolve("item-1"), Right("bw password"))
    assertEquals(v.resolve("item-1#username"), Right("bw username"))
    assertEquals(Files.readAllLines(d.resolve("bw-args")).asScala.toList, List("get", "username", "item-1", "--nointeraction"))
    assertEquals(v.resolve("dup"), Left("bitwarden: 'dup' matches more than one item — use its id"))
    assertEquals(v.resolve("other"), Left("bitwarden: no item 'other'"))
    val locked = registered(BitwardenPlugin(), "", Ctx(Map("PATH" -> d.toString)))
    assert(locked.resolve("item-1").left.exists(_.contains("the vault is locked")))

  test("no error text ever carries the value a tool printed"):
    val d = bin()
    script(d, "bw", "echo 'leaked-value'\necho 'bad thing' >&2\nexit 2\n")
    val v = registered(BitwardenPlugin(), "", Ctx(Map("PATH" -> d.toString)))
    val r = v.resolve("x")
    assertEquals(r, Left("bitwarden: could not read 'x' (exit 2: bad thing)"))

  test("the three are discoverable providers, built for this SPI, disabled by their defaults"):
    val found = java.util.ServiceLoader.load(classOf[Plugin]).iterator.asScala.toList
    assertEquals(found.map(_.info.id).sorted, List("1password", "bitwarden", "keepassxc"))
    found.foreach { p =>
      assertEquals(p.info.spiVersion, Spi.Version)
      val defs = ConfigFactory.parseResources(p.getClass.getClassLoader, p.defaultsResource).resolve()
      assertEquals(defs.getBoolean("enabled"), false, p.info.id)
    }
