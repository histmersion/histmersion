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
    var infos: List[String] = Nil                  // what it logged at info (test-local)
    def http = new PluginHttp { def get(url: String, headers: Map[String, String]) = Left("no network in tests") }
    val log  = new PluginLog { def info(m: String) = infos = infos :+ m; def warn(m: String) = () }
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

  // ── KeePassXC + YubiKey ─────────────────────────────────────────────────────────────────────

  test("yubikey: parsing <slot>[:<serial>], lists without repeats, the setting as a string or a list"):
    assertEquals(YubiKey.parse("2"), Right(YubiKey.Slot(2, None)))
    assertEquals(YubiKey.parse(" 1:7370001 "), Right(YubiKey.Slot(1, Some(7370001L))))
    assertEquals(YubiKey.Slot(2, Some(5L)).shown, "2:5")
    List("3", "0", "x", "2:", "2:abc", "2:-1", "2:1:3", "", ":5").foreach(b =>
      assert(YubiKey.parse(b).left.exists(_.contains("<slot>[:<serial>]")), b))
    assertEquals(YubiKey.parseAll(List("2:1", "", "2:1", "1")), Right(List(YubiKey.Slot(2, Some(1)), YubiKey.Slot(1, None))))
    assert(YubiKey.parseAll(List("2:1", "9")).left.exists(_.contains("'9'")))
    assertEquals(decode(KeePassXcPlugin(), "").yubikey, None)
    assertEquals(decode(KeePassXcPlugin(), "yubikey = \"2:12345678\"").yubikey, Some(YubiKey.Setting(List("2:12345678"))))
    assertEquals(decode(KeePassXcPlugin(), "yubikey = \"2:1, 1\"").yubikey, Some(YubiKey.Setting(List("2:1", "1"))))
    assertEquals(decode(KeePassXcPlugin(), "yubikey = [\"2:1\", \"2:2\", \"1\"]").yubikey,
      Some(YubiKey.Setting(List("2:1", "2:2", "1"))))
    val c = decode(KeePassXcPlugin(), "")
    assertEquals((c.yubikeyTimeoutSeconds, c.ykman), (30, "ykman"))

  test("yubikey: serials from ykman, the plan with and without detection, the last key first"):
    assertEquals(YubiKey.serials("12345678\n 87654321 \nWARNING: x\n\n"), Set(12345678L, 87654321L))
    val a = YubiKey.Slot(2, Some(1)); val b = YubiKey.Slot(2, Some(2)); val any = YubiKey.Slot(1, None)
    assertEquals(YubiKey.plan(List(a, b, any), None), (List(a, b, any), Nil))
    assertEquals(YubiKey.plan(List(a, b, any), Some(Set(2L))), (List(b, any), List(a)))
    assertEquals(YubiKey.plan(List(a, b, any), Some(Set.empty)), (Nil, List(a, b, any)))
    assertEquals(YubiKey.plan(List(a, b, any), Some(Set(1L, 2L)), Some(b)), (List(b, a, any), Nil))
    assertEquals(YubiKey.plan(List(a, b), Some(Set(1L)), Some(b)), (List(a), List(b)))

  test("yubikey: KeePassXC's messages — absent or failed is the next key; credentials, entry, touch stop"):
    import YubiKey.Failure.*
    def f(m: String) = YubiKey.failure(m.toLowerCase(java.util.Locale.ROOT), "E")
    assert(f("Could not find hardware key with serial number 1. Please plug it in to continue.").isInstanceOf[Next])
    assert(f("Failed to complete a challenge-response, the specific error was: x").isInstanceOf[Next])
    assert(f("Hardware key slot is not configured.").isInstanceOf[Next])
    assert(f("Please present or touch your YubiKey to continue.\nError while reading the database: Invalid credentials were provided").isInstanceOf[Stop])
    assertEquals(f("Hardware key timed out waiting for user interaction."), Stop("the YubiKey was not touched in time"))
    assertEquals(f("Could not find entry with path E."), Stop("no entry 'E'"))
    assert(f("Segmentation fault").isInstanceOf[Stop])
    assertEquals(YubiKey.problem("Please present or touch your YubiKey to continue.\n\nError: no key\n"), "Error: no key")

  /** A fake keepassxc-cli with YubiKeys: `$d/plugged` lists the serials "connected" (one per line), and
    * the database opens with slot 2 of any of them plus (unless `$d/key-only`) the password `master-pw`.
    * Each call appends its argv to `$d/kp-calls`, one call per line. */
  private def fakeKeePassXcYk(d: Path): Path =
    script(d, "keepassxc-cli", """dir=$(dirname "$0")
      |echo "$*" >> "$dir/kp-calls"
      |for a; do entry=$a; done
      |nopw=""; yk=""; prev=""
      |for a; do
      |  [ "$a" = "--no-password" ] && nopw=1
      |  [ "$prev" = "-y" ] && yk=$a
      |  prev=$a
      |done
      |if [ -n "$nopw" ]; then
      |  [ -f "$dir/key-only" ] || { echo "Error while reading the database: Invalid credentials were provided, please try again." >&2; exit 1; }
      |else
      |  IFS= read -r pw
      |  [ "$pw" = "master-pw" ] || { echo "Please present or touch your YubiKey to continue." >&2; echo "Error while reading the database: Invalid credentials were provided, please try again." >&2; exit 1; }
      |fi
      |slot=${yk%%:*}; serial=""; case "$yk" in *:*) serial=${yk#*:};; esac
      |[ -f "$dir/slow" ] && sleep 30
      |if [ -n "$yk" ]; then
      |  if [ -n "$serial" ]; then
      |    grep -qx "$serial" "$dir/plugged" 2>/dev/null || { echo "Error while reading the database: Could not find hardware key with serial number $serial. Please plug it in to continue." >&2; exit 1; }
      |  else
      |    [ -s "$dir/plugged" ] || { echo "Error while reading the database: No YubiKey detected" >&2; exit 1; }
      |  fi
      |  [ "$slot" = "2" ] || { echo "Error while reading the database: Failed to complete a challenge-response, the specific error was: Hardware key slot is not configured." >&2; exit 1; }
      |fi
      |if [ "$1" = "open" ]; then
      |  printf 'v.kdbx> '
      |  while IFS= read -r line; do
      |    printf '%s\n' "$line"
      |    case "$line" in
      |      exit|quit) exit 0;;
      |      "show -q -a "*)
      |        rest=${line#show -q -a }; attr=${rest%% *}; e=$(printf '%s' "${rest#* }" | sed 's/\\\(.\)/\1/g')
      |        case "$e" in
      |          missing) echo "Could not find entry with path missing." >&2;;
      |          *) if [ "$attr" = password ]; then printf '%s\n' "secret of $e"; else printf '%s\n' "secret of $e/$attr"; fi;;
      |        esac;;
      |      *) echo "Unknown command ${line%% *}" >&2;;
      |    esac
      |    printf 'v.kdbx> '
      |  done
      |  exit 0
      |fi
      |printf '%s\n' "secret of $entry"
      |""".stripMargin)

  /** A fake ykman: `list --serials` prints `$d/plugged`; anything else fails. */
  private def fakeYkman(d: Path): Path =
    script(d, "ykman", """dir=$(dirname "$0")
      |echo "$*" >> "$dir/ykman-calls"
      |[ "$1 $2" = "list --serials" ] || exit 2
      |cat "$dir/plugged" 2>/dev/null
      |exit 0
      |""".stripMargin)

  private def plug(d: Path, serials: String*): Unit = Files.writeString(d.resolve("plugged"), serials.map(_ + "\n").mkString)
  private def calls(d: Path): List[String] =
    if Files.exists(d.resolve("kp-calls")) then Files.readAllLines(d.resolve("kp-calls")).asScala.toList else Nil

  private def ykVault(d: Path, extra: String, pw: String = "master-pw"): (SecretVault, Ctx) =
    val db = d.resolve("v.kdbx"); if !Files.exists(db) then Files.writeString(db, "")
    val ctx = Ctx(Map("PATH" -> d.toString), Map(SecretRef.Env("KP") -> pw))
    (registered(KeePassXcPlugin(), s"""database = "$db", password = "env:KP", $extra""", ctx), ctx)

  test("yubikey: a single entry adds -y <slot:serial> beside the password on stdin, and says to touch it"):
    val d = bin(); fakeKeePassXcYk(d); plug(d, "12345678")
    val (v, ctx) = ykVault(d, "yubikey = \"2:12345678\"")
    assertEquals(v.resolve("Portal"), Right("secret of Portal"))
    assertEquals(calls(d), List(s"show -q -a password -y 2:12345678 ${d.resolve("v.kdbx")} Portal"))
    assert(ctx.infos.exists(m => m.contains("touch your YubiKey") && m.contains("2:12345678")), ctx.infos)
    assert(ctx.infos.exists(_.contains("tried in order, the one that opened the database last first")), ctx.infos) // no ykman here: tried in order
    assert(!ctx.infos.exists(_.contains("master-pw")))

  test("yubikey: with ykman, an absent first key is skipped without running keepassxc-cli; the present one opens"):
    val d = bin(); fakeKeePassXcYk(d); fakeYkman(d); plug(d, "87654321")
    val (v, _) = ykVault(d, "yubikey = [\"2:12345678\", \"2:87654321\", \"1\"]")
    assertEquals(v.resolve("Portal"), Right("secret of Portal"))
    assertEquals(calls(d).map(_.split(' ')(5)), List("2:87654321"))
    assertEquals(Files.readAllLines(d.resolve("ykman-calls")).asScala.toList, List("list --serials"))
    // the key that worked goes first next time
    plug(d, "12345678", "87654321")
    assertEquals(v.resolve("Other"), Right("secret of Other"))
    assertEquals(calls(d).map(_.split(' ')(5)), List("2:87654321", "2:87654321"))

  test("yubikey: with ykman and no key plugged in, nothing is run and the error lists what was configured"):
    val d = bin(); fakeKeePassXcYk(d); fakeYkman(d); plug(d)
    val (v, _) = ykVault(d, "yubikey = [\"2:12345678\", \"1\"]")
    assertEquals(v.resolve("Portal"),
      Left("keepassxc: none of the configured YubiKeys is connected (ykman sees no YubiKey) — 2:12345678, 1; plug one in"))
    assertEquals(calls(d), Nil)

  test("yubikey: without detection, entries are tried in order — absent and failed slots go on, the error names each"):
    val d = bin(); fakeKeePassXcYk(d); plug(d, "87654321")
    val (v, _) = ykVault(d, "ykman = \"\", yubikey = [\"2:12345678\", \"1\", \"2:87654321\"]")
    assertEquals(v.resolve("Portal"), Right("secret of Portal"))
    assertEquals(calls(d).map(_.split(' ')(5)), List("2:12345678", "1", "2:87654321"))
    val d2 = bin(); fakeKeePassXcYk(d2); plug(d2)
    val (none, _) = ykVault(d2, "ykman = \"\", yubikey = [\"2:12345678\", \"2\"]")
    val err = none.resolve("Portal")
    assert(err.left.exists(e => e.contains("2:12345678: not connected (Error while reading the database: Could not find hardware key") &&
      e.contains("; 2: not connected (Error while reading the database: No YubiKey detected)") &&
      e.contains("plug in (or touch) one of them")), err)

  test("yubikey: a wrong password stops at the first key — no other key is tried, and the password is never shown"):
    val d = bin(); fakeKeePassXcYk(d); plug(d, "1", "2")
    val (v, _) = ykVault(d, "ykman = \"\", yubikey = [\"2:1\", \"2:2\"]", pw = "wrong-pw")
    val r = v.resolve("Portal")
    assert(r.left.exists(e => e.contains("2:1: invalid credentials") && e.contains("not trying further keys") &&
      !e.contains("Please present")), r)
    assert(!r.left.exists(_.contains("wrong-pw")))
    assertEquals(calls(d).size, 1)

  test("yubikey: an attempt nobody answers is killed at yubikey-timeout-seconds and stops the search"):
    val d = bin(); fakeKeePassXcYk(d); plug(d, "1"); Files.writeString(d.resolve("slow"), "")
    val (v, _) = ykVault(d, "ykman = \"\", yubikey = [\"2:1\", \"2:2\"], yubikey-timeout-seconds = 1")
    val t0 = System.nanoTime
    val r = v.resolve("Portal")
    assert(r.left.exists(e => e.contains("2:1: not touched in time (keepassxc-cli did not answer within 1 s)") &&
      e.contains("not trying further keys")), r)
    assertEquals(calls(d).size, 1)
    assert((System.nanoTime - t0).nanos < 10.seconds)

  test("yubikey: a key-only database (no password) passes --no-password with -y, and a key file with both"):
    val d = bin(); fakeKeePassXcYk(d); plug(d, "5"); Files.writeString(d.resolve("key-only"), "")
    val db = Files.writeString(d.resolve("v.kdbx"), ""); val key = Files.writeString(d.resolve("v.key"), "k")
    val v = registered(KeePassXcPlugin(), s"""database = "$db", yubikey = "2:5"""", Ctx(Map("PATH" -> d.toString)))
    assertEquals(v.resolve("E"), Right("secret of E"))
    assertEquals(calls(d).last, s"show -q -a password -y 2:5 --no-password $db E")
    val k = registered(KeePassXcPlugin(), s"""database = "$db", key-file = "$key", yubikey = "2:5"""", Ctx(Map("PATH" -> d.toString)))
    assertEquals(k.resolve("E"), Right("secret of E"))
    assertEquals(calls(d).last, s"show -q -a password -k $key -y 2:5 --no-password $db E")

  test("yubikey: registration refuses a malformed entry and a wrong ykman path; a missing entry is named"):
    val d = bin(); fakeKeePassXcYk(d); plug(d, "1")
    val db = Files.writeString(d.resolve("v.kdbx"), "")
    val p = KeePassXcPlugin()
    def reg(h: String) = p.register(decode(p, s"""database = "$db", $h"""), Ctx(Map("PATH" -> d.toString)))
    assert(reg("yubikey = [\"2:1\", \"3\"]").left.exists(_.contains("yubikey '3' is not <slot>[:<serial>]")))
    assert(reg(s"""yubikey = "2:1", ykman = "${d.resolve("no-ykman")}"""").left.exists(_.startsWith("ykman: ")))
    assertEquals(reg(s"""ykman = "${d.resolve("no-ykman")}"""").isRight, true) // no yubikey: ykman is not looked at
    script(d, "keepassxc-cli", "echo 'Could not find entry with path missing.' >&2\nexit 1\n")
    val (v, _) = ykVault(d, "ykman = \"\", yubikey = [\"2:1\", \"2:2\"]")
    assertEquals(v.resolve("missing"), Left("keepassxc: no entry 'missing'"))

  // ── KeePassXC: one open for many references ────────────────────────────────────────────────

  test("keepassxc session: the prompt, escaping for the interactive line, the answer without its echo"):
    assertEquals(KeePassXcSession.prompt("v.kdbx> "), Some("v.kdbx> "))
    assertEquals(KeePassXcSession.prompt("Please touch\nResearch> "), Some("Research> "))
    assertEquals(KeePassXcSession.prompt("v.kdbx"), None)
    assertEquals(KeePassXcSession.escape("A/Two words \"q\" b\\s\tt"), "A/Two\\ words\\ \\\"q\\\"\\ b\\\\s\\\tt")
    assertEquals(KeePassXcSession.command("password", "A/Two words"), "show -q -a password A/Two\\ words")
    assert(!KeePassXcSession.sendable("a\nb") && KeePassXcSession.sendable("a b"))
    val out = "v.kdbx> show -q -a password A\nsecret\nv.kdbx> "
    assertEquals(KeePassXcSession.segment(out, 8, "v.kdbx> "), Some("show -q -a password A\nsecret\n"))
    assertEquals(KeePassXcSession.segment(out.dropRight(8), 8, "v.kdbx> "), None, "not answered yet")
    assertEquals(KeePassXcSession.segment("v.kdbx> ", 8, "v.kdbx> "), None, "the old prompt is not an answer")
    assertEquals(KeePassXcSession.answer("show -q -a password A\nsecret\n", "show -q -a password A"), "secret")
    assertEquals(KeePassXcSession.answer("secret\n", "show -q -a password A"), "secret", "a session without readline echoes nothing")
    assertEquals(KeePassXcSession.answer("show -q -a password A\n", "show -q -a password A"), "", "a missing entry prints nothing")

  test("keepassxc batch: N references with ONE open — one touch line, the password on stdin, a missing entry its own error"):
    val d = bin(); fakeKeePassXcYk(d); plug(d, "12345678")
    val (v, ctx) = ykVault(d, "ykman = \"\", yubikey = \"2:12345678\"")
    val quoted = "B/\"quoted\" back\\slash"
    val r = v.resolveAll(List("A/One", "A/Two words#UserName", "missing", quoted, "A/One"))
    assertEquals(r, Map(
      "A/One"                -> Right("secret of A/One"),
      "A/Two words#UserName" -> Right("secret of A/Two words/UserName"),
      "missing"              -> Left("keepassxc: no entry 'missing'"),
      quoted                 -> Right(s"secret of $quoted")))
    assertEquals(calls(d), List(s"open -q -y 2:12345678 ${d.resolve("v.kdbx")}"), "one open, no show")
    assertEquals(ctx.infos.count(_.contains("touch your YubiKey")), 1)
    assert(ctx.infos.exists(_.contains("for 4 references")), ctx.infos)
    assert(!(ctx.infos ++ calls(d) ++ r.values.flatMap(_.left.toOption)).exists(_.contains("master-pw")))

  test("keepassxc batch: the first YubiKey absent, the second opens the session; it goes first next time"):
    val d = bin(); fakeKeePassXcYk(d); plug(d, "87654321")
    val (v, ctx) = ykVault(d, "ykman = \"\", yubikey = [\"2:12345678\", \"2:87654321\"]")
    assertEquals(v.resolveAll(List("A", "B")), Map("A" -> Right("secret of A"), "B" -> Right("secret of B")))
    assertEquals(calls(d).map(_.split(' ').take(4).mkString(" ")), List("open -q -y 2:12345678", "open -q -y 2:87654321"))
    assertEquals(ctx.infos.count(_.contains("touch your YubiKey")), 2, "one line per open attempt")
    plug(d, "12345678", "87654321")
    assertEquals(v.resolveAll(List("C", "D")), Map("C" -> Right("secret of C"), "D" -> Right("secret of D")))
    assertEquals(calls(d).drop(2).map(_.split(' ').take(4).mkString(" ")), List("open -q -y 2:87654321"))

  test("keepassxc batch: a wrong password fails every reference after one open; no further key is tried"):
    val d = bin(); fakeKeePassXcYk(d); plug(d, "1", "2")
    val (v, _) = ykVault(d, "ykman = \"\", yubikey = [\"2:1\", \"2:2\"]", pw = "wrong-pw")
    val r = v.resolveAll(List("A", "B"))
    assertEquals(r.keySet, Set("A", "B"))
    assert(r.values.forall(_.left.exists(e =>
      e.contains("could not open v.kdbx for 2 references with a YubiKey — 2:1: invalid credentials") &&
        e.contains("not trying further keys") && !e.contains("wrong-pw"))), r)
    assertEquals(calls(d).size, 1)

  test("keepassxc batch without a YubiKey: one password session; one reference alone is a plain show; bad ones refused"):
    val d = bin(); fakeKeePassXcYk(d)
    val db = Files.writeString(d.resolve("v.kdbx"), "")
    val v = registered(KeePassXcPlugin(), s"""database = "$db", password = "env:KP"""",
      Ctx(Map("PATH" -> d.toString), Map(SecretRef.Env("KP") -> "master-pw")))
    assertEquals(v.resolveAll(List("A", "B#UserName")), Map("A" -> Right("secret of A"), "B#UserName" -> Right("secret of B/UserName")))
    assertEquals(calls(d), List(s"open -q $db"))
    assertEquals(v.resolveAll(List("C")), Map("C" -> Right("secret of C")))
    assertEquals(calls(d).last, s"show -q -a password $db C")
    val bad = v.resolveAll(List("-x", "D", "E"))
    assertEquals(bad("-x"), Left("the entry may not start with '-'"))
    assertEquals((bad("D"), bad("E")), (Right("secret of D"), Right("secret of E")))
    assertEquals(calls(d).last, s"open -q $db", "the refused one ran nothing")
    val wrong = registered(KeePassXcPlugin(), s"""database = "$db", password = "env:KP"""",
      Ctx(Map("PATH" -> d.toString), Map(SecretRef.Env("KP") -> "nope")))
    assert(wrong.resolveAll(List("A", "B")).values.forall(_.left.exists(_.contains("the database is locked"))))

  test("keepassxc batch against the installed keepassxc-cli and a throwaway database (HISTMERSION_TEST_KEEPASSXC=1)"):
    val cli = Subprocess.locate("keepassxc-cli", sys.env.get("PATH"))
    val why = if !sys.env.get("HISTMERSION_TEST_KEEPASSXC").contains("1") then Some("set HISTMERSION_TEST_KEEPASSXC=1")
      else cli.left.toOption
    why.foreach(w => println(s"SKIPPED VaultsSuite real keepassxc-cli: $w"))
    assume(why.isEmpty, why.getOrElse(""))
    val exe = cli.toOption.get
    val d = Files.createTempDirectory("histmersion-kpx-test")
    val db = d.resolve("throwaway.kdbx").toString
    def kp(args: List[String], stdin: String) =
      val r = Subprocess.run(exe :: args, Map.empty, Some(stdin), 60.seconds).fold(fail(_), identity)
      assertEquals(r.exit, 0, r.err)
    kp(List("db-create", "-q", "-p", db), "test-pw\ntest-pw\n")
    kp(List("mkdir", "-q", db, "G"), "test-pw\n")
    kp(List("add", "-q", "-p", db, "G/One"), "test-pw\nfirst value\n")
    kp(List("add", "-q", "-p", db, "G/Two words \"q\" b\\s"), "test-pw\nsecond\n")
    kp(List("add", "-q", "-p", "-u", "someone", db, "Three"), "test-pw\nthird\n")
    val v = registered(KeePassXcPlugin(), s"""cli = "$exe", database = "$db", password = "env:KP", ykman = \"\"""",
      Ctx(Map("PATH" -> sys.env.getOrElse("PATH", "")), Map(SecretRef.Env("KP") -> "test-pw")))
    assertEquals(v.resolveAll(List("G/One", "G/Two words \"q\" b\\s", "missing", "Three#UserName", "Three")), Map(
      "G/One" -> Right("first value"), "G/Two words \"q\" b\\s" -> Right("second"),
      "missing" -> Left("keepassxc: no entry 'missing'"), "Three#UserName" -> Right("someone"), "Three" -> Right("third")))

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
