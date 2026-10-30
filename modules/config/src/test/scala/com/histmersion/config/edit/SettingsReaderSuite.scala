package com.histmersion.config.edit

import com.histmersion.config.AppConfig
import org.ekrich.config.ConfigFactory

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

class SettingsReaderSuite extends munit.FunSuite:

  private def key(s: String) = SettingKey.parse(s).toOption.get

  private def readings(text: String, env: Map[String, String] = Map.empty, prefix: Option[String] = None) =
    val d = Files.createTempDirectory("histmersion-settings-read")
    val f = d.resolve("histmersion.conf")
    Files.writeString(f, text)
    val e = ConfigFactory.parseMap(env.asJava)
    val state = ConfigFileWriter(f, e, ConfigSchema.reference).read().fold(x => fail(x.message), identity)
    val merged = AppConfig.loadWithUserFile(e, Some(f)).fold(m => fail(m), _.merged)
    SettingsReader.read(ConfigSchema.reference, state, merged, e, prefix.map(key)).map(r => r.field.key.relative -> r).toMap

  test("each setting's value in the file, what applies, and where that comes from"):
    val r = readings("histmersion { backups.keep = 3, ui.page-size = 40 }", Map("HISTMERSION_BACKUPS_KEEP" -> "9"))
    assertEquals(r("ui.page-size").inFile, Some(SettingValue.Number(BigDecimal(40))))
    assertEquals(r("ui.page-size").source, SettingSource.File)
    assertEquals(r("backups.keep").inFile, Some(SettingValue.Number(BigDecimal(3))))
    assertEquals(r("backups.keep").source, SettingSource.Environment)
    assertEquals(r("backups.keep").effective.map(_.hocon), Some("\"9\""))
    assertEquals(r("lint.max-items").source, SettingSource.Default)
    assertEquals(r("lint.max-items").inFile, None)

  test("a credential is told only by how it is held"):
    val r = readings("histmersion.plugins.oa-core.api-key = \"keepassxc:Histmersion/CORE#password\"\n" +
      "histmersion.plugins.media-europeana.api-key = \"value:abc\"\n", Map("HISTMERSION_API_KEY" -> "s3cret"))
    assertEquals(r("plugins.oa-core.api-key").credential, Some(SecretForm.Vault))
    assertEquals(r("plugins.oa-core.api-key").shown, Some("keepassxc:Histmersion/CORE#password"))
    assertEquals(r("plugins.media-europeana.api-key").shown, Some("value:***"))
    assertEquals(r("auth.api-key").credential, Some(SecretForm.Plain))
    assertEquals(r("auth.api-key").shown, None)
    assertEquals(r("acquire.core.api-key").credential, Some(SecretForm.Unset))

  test("a setting written with a substitution or by an included file is locked, with why"):
    val r = readings("histmersion.ui.tiles = ${?MY_TILES}\n")
    assert(r("ui.tiles").locked.exists(_.isInstanceOf[SettingError.Substitution]))
    assertEquals(r("ui.tiles").source, SettingSource.File)

  test("only the settings under a prefix"):
    val r = readings("", prefix = Some("backups"))
    assert(r.keySet.forall(_.startsWith("backups.")) && r.contains("backups.keep"), s"${r.keySet}")

  test("loading a config file's text says what loading the file says"):
    assert(AppConfig.loadWithUserText(ConfigFactory.empty, "histmersion.backups.keep = 3").isRight)
    val bad = AppConfig.loadWithUserText(ConfigFactory.empty, "histmersion.backups.keep = lots")
    assert(bad.left.exists(_.contains("backups.keep")), s"$bad")
    assert(AppConfig.loadWithUserText(ConfigFactory.empty, "histmersion { ").isLeft)
