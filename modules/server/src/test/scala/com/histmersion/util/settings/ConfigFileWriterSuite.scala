package com.histmersion
package util.settings

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.cli.LocalSettingsFile
import com.histmersion.config.edit.*
import com.histmersion.util.storage.{Location, Storage}
import org.ekrich.config.{Config, ConfigFactory}

import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermissions
import scala.jdk.CollectionConverters.*
import com.histmersion.util.storage.Store
import com.histmersion.config.edit.SettingPolicy
import com.histmersion.config.edit.SettingValue
import com.histmersion.lint.{LintRule, tolerated}

/** The settings editor over each place a settings file is kept: the command line's file on its own disk, the server's
  * in the store of its directory. Both must change a file exactly alike.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.SilentInterp, "pending G35 (a vocabulary renders through its wire string)")
abstract class ConfigFileWriterSuite(fileAt: Path => SettingsFile) extends munit.FunSuite:

  private val K = SettingKey.ParseRaw
  private val T = SettingValue.Text.Value

  private def key(s: SettingKey.ParseRaw) = SettingKey.parse(s).toOption.get
  private def set(k: SettingKey.ParseRaw, v: SettingValue) = SettingEdit.Set(key(k), v)
  private def unset(k: SettingKey.ParseRaw) = SettingEdit.Unset(key(k))
  private def num(n: Int) = SettingValue.Number(BigDecimal(n))
  private def text(s: SettingValue.Text.Value) = SettingValue.Text(s)
  private val noEnv: Config = ConfigFactory.empty

  private def dir(): Path = Files.createTempDirectory("histmersion-conf-edit")

  private def fileWith(text: String, d: Path = dir()): Path =
    val f = d.resolve("histmersion.conf")
    Files.writeString(f, text)
    f

  private def writer(f: Path, env: Config = noEnv) = ConfigFileWriter(fileAt(f), env, ConfigSchema.reference)

  private def ok(f: Path, edits: SettingEdit*): WriteOutcome =
    writer(f)
      .write(edits.toList, None, literalConsent = SettingPolicy.LiteralConsent(false))
      .fold(e => fail(e.message.sentences.mkString(" ")), identity)

  private def refused(f: Path, edits: SettingEdit*): SettingError =
    writer(f)
      .write(edits.toList, None, literalConsent = SettingPolicy.LiteralConsent(false))
      .fold(identity, o => fail(s"written: $o"))

  private def read(f: Path) = Files.readString(f)

  private val Commented =
    """# my settings — keep this line
      |histmersion {
      |  # the converter: two workers
      |  converter.parallelism = 2   # trailing note
      |  backups {
      |    keep = 3                  // C++-style note
      |  }
      |  zz-unknown = "kept as it is"
      |}
      |# the end
      |""".stripMargin

  test("one value changed in place: every comment, the order, the layout and an unknown key are kept"):
    val f = fileWith(Commented)
    val o = ok(f, set(K("backups.keep"), num(7)))
    assertEquals(o.changed.map(_.relative), List("backups.keep"))
    assertEquals(read(f), Commented.replace("keep = 3", "keep = 7"))
    assertEquals(Files.readString(f.resolveSibling("histmersion.conf.bak")), Commented, "the old text is the .bak")
    assertEquals(o.backup, Some(f.resolveSibling("histmersion.conf.bak").toString))

  test("setting a value the file already holds writes nothing; the same change twice gives the same file"):
    val f = fileWith(Commented)
    val same = ok(f, set(K("backups.keep"), num(3)))
    assertEquals(same.changed, Nil)
    assertEquals(same.unchanged.map(_.relative), List("backups.keep"))
    assert(!Files.exists(f.resolveSibling("histmersion.conf.bak")), "no write, no backup")
    ok(f, set(K("ui.page-size"), num(40)))
    val once = read(f)
    val again = ok(f, set(K("ui.page-size"), num(40)))
    assertEquals(again.changed, Nil)
    assertEquals(read(f), once)

  test("a new setting goes under histmersion { … }; taking it out leaves the rest of the file as it was"):
    val f = fileWith(Commented)
    ok(f, set(K("acquire.contact-email"), text(T("someone@example.org"))))
    val after = ConfigFactory.parseFile(f.toFile)
    assertEquals(after.getString("histmersion.acquire.contact-email"), "someone@example.org")
    assertEquals(after.getInt("histmersion.backups.keep"), 3)
    assert(
      read(f).startsWith("# my settings — keep this line\nhistmersion {\n  # the converter: two workers\n"),
      read(f)
    )
    ok(f, unset(K("acquire.contact-email")))
    val back = ConfigFactory.parseFile(f.toFile)
    assert(!back.hasPath("histmersion.acquire.contact-email"))
    assertEquals(back.root.render(org.ekrich.config.ConfigRenderOptions.concise).length > 0, true)
    // the round trip: what the file says is what it said before, setting for setting
    def leaves(c: Config) = c.entrySet.asScala.map(e => e.getKey -> e.getValue.unwrapped).toMap
    assertEquals(leaves(back), leaves(ConfigFactory.parseString(Commented)))
    assert(
      read(f).contains("# trailing note") && read(f).contains("// C++-style note") && read(f).contains("# the end")
    )

  test("a file that is not there is made, with a header and the setting; its first write keeps no .bak"):
    val d = dir().resolve("deeper")
    val f = d.resolve("histmersion.conf")
    val o = writer(f)
      .write(List(set(K("backups.keep"), num(9))), None, literalConsent = SettingPolicy.LiteralConsent(false))
      .fold(e => fail(e.message.sentences.mkString(" ")), identity)
    assert(o.created.value)
    assertEquals(o.backup, None)
    assert(read(f).startsWith(ConfigFileWriter.NewFileHeader.value))
    assertEquals(ConfigFactory.parseFile(f.toFile).getInt("histmersion.backups.keep"), 9)

  test("an all-comment template (the installer's) gets its first setting; the comments stay"):
    val template = "# Histmersion settings\n#\n# histmersion {\n#   backups.keep = 10\n# }\n"
    val f = fileWith(template)
    ok(f, set(K("setup.completed"), SettingValue.Flag(SettingValue.Flag.Value(true))))
    assert(read(f).startsWith(template))
    assert(ConfigFactory.parseFile(f.toFile).getBoolean("histmersion.setup.completed"))

  test("a setting written with a substitution or += is refused, and the file is not touched"):
    val written = "histmersion {\n  ui.tiles = ${?MY_TILES}\n  acquire.candidates.indexes += crossref\n}\n"
    val f = fileWith(written)
    assert(refused(f, set(K("ui.tiles"), text(T("x")))).isInstanceOf[SettingError.Substitution])
    assert(refused(f, unset(K("ui.tiles"))).isInstanceOf[SettingError.Substitution])
    assert(
      refused(f, set(K("acquire.candidates.indexes"), SettingValue.Items(Nil))).isInstanceOf[SettingError.Substitution]
    )
    assertEquals(read(f), written)
    // the rest of the file can still be changed, and the substitution stays exactly as written
    ok(f, set(K("backups.keep"), num(4)))
    assert(read(f).contains("ui.tiles = ${?MY_TILES}") && read(f).contains("indexes += crossref"), read(f))

  test("a block written as a substitution refuses the settings inside it"):
    val f = fileWith("base { keep = 2 }\nhistmersion {\n  backups = ${base}\n}\n")
    assert(refused(f, set(K("backups.keep"), num(3))).isInstanceOf[SettingError.Substitution])

  test("a key written twice, once as a substitution, is refused rather than losing the substitution"):
    val written = "histmersion.backups.keep = ${?KEEP}\nhistmersion.backups.keep = 4\n"
    val f = fileWith(written)
    assert(refused(f, set(K("backups.keep"), num(6))).isInstanceOf[SettingError.Substitution])
    assertEquals(read(f), written)

  test("an included file: its settings are refused, the include line is kept, others are written after it"):
    val d = dir()
    Files.writeString(d.resolve("shared.conf"), "histmersion.backups.keep = 11\nhistmersion.ui.page-size = 30\n")
    val written = "include \"shared.conf\"\nhistmersion {\n  converter.parallelism = 2\n}\n"
    val f = fileWith(written, d)
    refused(f, set(K("backups.keep"), num(3))) match
      case SettingError.FromInclude(k, file) =>
        assertEquals(k.relative, "backups.keep"); assert(file.value.endsWith("shared.conf"), file)
      case other => fail(s"$other")
    assert(refused(f, unset(K("ui.page-size"))).isInstanceOf[SettingError.FromInclude])
    ok(f, set(K("converter.parallelism"), num(3)), set(K("lint.max-items"), num(9)))
    assert(read(f).startsWith("include \"shared.conf\"\n"), read(f))
    val c = ConfigFactory.parseFile(f.toFile)
    assertEquals(c.getInt("histmersion.backups.keep"), 11)
    assertEquals(c.getInt("histmersion.converter.parallelism"), 3)
    assertEquals(c.getInt("histmersion.lint.max-items"), 9)

  test("a setting the file sets before an include that sets it too would not take: refused, file unchanged"):
    val d = dir()
    Files.writeString(d.resolve("later.conf"), "histmersion.backups.keep = 11\n")
    val written = "histmersion.backups.keep = 2\ninclude \"later.conf\"\n"
    val f = fileWith(written, d)
    assert(refused(f, set(K("backups.keep"), num(3))).isInstanceOf[SettingError.FromInclude])
    assertEquals(read(f), written)

  test("a value of the wrong kind, an unknown key and a block are refused in plain words"):
    val f = fileWith(Commented)
    assert(
      refused(f, set(K("backups.keep"), text(T("many")))).message.sentences
        .mkString(" ")
        .contains("histmersion.backups.keep must be a number, not text")
    )
    val unknown = refused(f, set(K("backups.kep"), num(1))).message.sentences.mkString(" ")
    assert(unknown.contains("unknown setting key 'histmersion.backups.kep'"), unknown)
    assert(refused(f, set(K("backups"), num(1))).message.sentences.mkString(" ").contains("is a block of settings"))
    assertEquals(read(f), Commented)

  test("a value the server could not start with is refused before the file is touched"):
    val f = fileWith(Commented)
    val e = refused(
      f,
      set(K("ui.base-map-source"), text(T("auto"))),
      set(K("basemap.bathymetry.source"), text(T("nonsense")))
    )
    assert(e.isInstanceOf[SettingError.Invalid], s"$e")
    assert(
      e.message.sentences.mkString(" ").contains("unknown sea floor source 'nonsense' (one of: etopo1, gebco)"),
      e.message
    )
    assertEquals(read(f), Commented)
    assert(
      !Files.list(f.getParent).iterator.asScala.exists(_.getFileName.toString.endsWith(".tmp")),
      "no temporary file left"
    )

  test("a file that does not read is not rewritten"):
    val f = fileWith("histmersion { backups { keep = 3 \n")
    assert(refused(f, set(K("ui.page-size"), num(4))).isInstanceOf[SettingError.Unreadable])

  test("a change made against an older version of the file is refused"):
    val f = fileWith(Commented)
    val w = writer(f)
    val v = w.read().toOption.get.version
    Files.writeString(f, Commented + "\n# edited by hand\n")
    val now = w.read().toOption.get.version
    assertEquals(
      w.write(List(set(K("backups.keep"), num(8))), Some(v), literalConsent = SettingPolicy.LiteralConsent(false)),
      Left(SettingError.Changed(v, now))
    )
    assert(
      w.write(List(set(K("backups.keep"), num(8))), Some(now), literalConsent = SettingPolicy.LiteralConsent(false))
        .isRight
    )

  test("credentials: the value itself never goes in the file; a plugin's is a reference, value: only when confirmed"):
    val f = fileWith(Commented)
    assert(
      refused(f, set(K("auth.api-key"), text(T("s3cret")))).message.sentences
        .mkString(" ")
        .contains("HISTMERSION_API_KEY")
    )
    assert(
      refused(f, set(K("plugins.oa-core.api-key"), text(T("s3cret")))).message.sentences
        .mkString(" ")
        .contains("reference")
    )
    assert(
      refused(f, set(K("plugins.oa-core.api-key"), text(T("value:s3cret"))))
        .isInstanceOf[SettingError.LiteralNeedsConsent]
    )
    assert(!read(f).contains("s3cret"))
    ok(f, set(K("plugins.oa-core.api-key"), text(T("keepassxc:Histmersion/CORE#password"))))
    assertEquals(
      ConfigFactory.parseFile(f.toFile).getString("histmersion.plugins.oa-core.api-key"),
      "keepassxc:Histmersion/CORE#password"
    )
    val lit = writer(f)
      .write(
        List(set(K("plugins.oa-core.api-key"), text(T("value:s3cret")))),
        None,
        literalConsent = SettingPolicy.LiteralConsent(true)
      )
      .fold(e => fail(e.message.sentences.mkString(" ")), identity)
    assert(lit.warnings.exists(_.value.contains("anyone who can read")), s"${lit.warnings}")

  test("the file's permissions are kept"):
    val f = fileWith(Commented)
    Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rw-------"))
    ok(f, set(K("backups.keep"), num(2)))
    assertEquals(PosixFilePermissions.toString(Files.getPosixFilePermissions(f)), "rw-------")

  test("a restart-only setting and an environment variable that overrides the file are said"):
    val f = fileWith(Commented)
    val env = ConfigFactory.parseMap(java.util.Map.of("HISTMERSION_BACKUPS_KEEP", "1"))
    val o = writer(f, env)
      .write(
        List(set(K("server.port"), num(8123)), set(K("backups.keep"), num(6))),
        None,
        literalConsent = SettingPolicy.LiteralConsent(false)
      )
      .fold(e => fail(e.message.sentences.mkString(" ")), identity)
    assertEquals(o.restart.map(_._1.relative), List("server.port"))
    assert(o.warnings.exists(_.value.contains("HISTMERSION_BACKUPS_KEEP")), s"${o.warnings}")

  test("a plugin's own key and a quoted plugin id"):
    val f = fileWith(Commented)
    ok(
      f,
      set(K("plugins.1password.enabled"), SettingValue.Flag(SettingValue.Flag.Value(true))),
      set(K("plugins.media-europeana.enabled"), SettingValue.Flag(SettingValue.Flag.Value(false)))
    )
    val c = ConfigFactory.parseFile(f.toFile)
    assert(c.getBoolean("histmersion.plugins.\"1password\".enabled"))
    assert(!c.getBoolean("histmersion.plugins.media-europeana.enabled"))

  test("a symbolic link to the file: the file it points to is changed, the link stays"):
    val d = dir()
    val real = fileWith(Commented, Files.createDirectories(d.resolve("real")))
    val link = Files.createSymbolicLink(d.resolve("histmersion.conf"), real)
    writer(link)
      .write(List(set(K("backups.keep"), num(12))), None, literalConsent = SettingPolicy.LiteralConsent(false))
      .fold(e => fail(e.message.sentences.mkString(" ")), identity)
    assert(Files.isSymbolicLink(link))
    assert(read(real).contains("keep = 12"))

  test("what is added is written in the file's own style, and a block left empty is taken out with its last setting"):
    val f = fileWith("# mine\n")
    ok(f, set(K("backups.keep"), num(4)), set(K("acquire.contact-email"), text(T("someone@example.org"))))
    val added = read(f)
    assert(added.startsWith("# mine\n"), added)
    assert(
      added.contains("histmersion {") && added.contains("keep = 4") && added.contains(
        "contact-email = \"someone@example.org\""
      ),
      added
    )
    assert(!added.contains(" : "), s"no sconfig-style colons: $added")
    ok(f, unset(K("backups.keep")), unset(K("acquire.contact-email")))
    assertEquals(read(f).trim, "# mine")
    // a block that holds a comment stays, emptied or not
    val g = fileWith("histmersion {\n  backups {\n    # kept for later\n    keep = 3\n  }\n}\n")
    ok(g, unset(K("backups.keep")))
    assert(read(g).contains("# kept for later") && read(g).contains("backups"), read(g))

  test("a file written from nothing ends with a line break"):
    val f = fileWith("")
    ok(f, set(K("backups.keep"), num(3)))
    assert(read(f).endsWith("}\n"), read(f))

class LocalConfigFileWriterSuite extends ConfigFileWriterSuite(LocalSettingsFile(_))

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class StoredConfigFileWriterSuite
    extends ConfigFileWriterSuite(f =>
      StoredSettingsFile(
        Storage(l => Option.when(l == Location.UserConfig)(f.toAbsolutePath.getParent), () => f.getParent)
          .store(Location.UserConfig),
        Store.Key.applyUnsafe(f.getFileName.toString),
        SettingsFile.Shown(f.toString)
      )
    )
