package com.histmersion.util.settings

import com.histmersion.api.settings.{ConfigEdit, ReloadReport}
import org.ekrich.config.ConfigFactory

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

class SettingsControllersSuite extends munit.FunSuite:

  final private class Fixture(text: Option[String], restartable: Boolean = false, env: Map[String, String] = Map.empty):
    val file = AtomicReference(text)
    val reloads = AtomicInteger(0)
    val settings =
      SettingsService.InMemory(file, ConfigFactory.parseMap(scala.jdk.CollectionConverters.MapHasAsJava(env).asJava))
    val restarts = RestartService.InMemory(restartable)
    val c = SettingsControllers(
      settings,
      restarts,
      () => { reloads.incrementAndGet(); ReloadReport(published = true, generation = 2) }
    )

  private val Commented = "# mine\nhistmersion {\n  backups.keep = 3   # three\n}\n"

  private def write(
      f: Fixture,
      changes: (String, Option[String])*
  ): Either[com.histmersion.api.ApiError, ConfigEdit.Written] =
    f.c.write(ConfigEdit.Write(changes.toList.map((k, v) => ConfigEdit.Change(k, v))))

  test("the page: every setting with what the file says, what applies and from where"):
    val f = Fixture(Some(Commented), env = Map("HISTMERSION_TREE_DEPTH" -> "3"))
    val p = f.c.page(None).fold(e => fail(e.message), identity)
    assertEquals(p.field("backups.keep").flatMap(_.value), Some("3"))
    assertEquals(p.field("backups.keep").map(_.source), Some("file"))
    assertEquals(p.field("ui.tree-depth").map(_.source), Some("environment"))
    assertEquals(p.field("ui.tree-depth").flatMap(_.effective), Some("\"3\""))
    assertEquals(p.field("lint.max-items").map(_.source), Some("default"))
    assert(p.sections.map(_.id).contains("backups"))
    assert(p.sections.find(_.id == "config").exists(_.doc.nonEmpty), "a section carries reference.conf's words")
    assertEquals(f.c.page(Some("backups")).map(_.sections.map(_.id)), Right(List("backups")))
    assert(f.c.page(Some("not a key!")).isLeft)

  test("a credential's value never crosses: not what the file writes, not what applies, not the default"):
    val f = Fixture(
      Some("histmersion.plugins.media-europeana.api-key = \"value:hunter2\"\n"),
      env = Map("HISTMERSION_API_KEY" -> "s3cret-key")
    )
    val p = f.c.page(None).fold(e => fail(e.message), identity)
    val europeana = p.field("plugins.media-europeana.api-key").get
    assertEquals(europeana.credential, Some(ConfigEdit.Credential("reference", "value", Some("value:***"))))
    assertEquals((europeana.value, europeana.effective, europeana.default), (None, None, None))
    val auth = p.field("auth.api-key").get
    assertEquals(auth.credential.map(_.form), Some("plain"))
    assertEquals((auth.value, auth.effective), (None, None))
    val json = io.circe.syntax.EncoderOps(p).asJson.noSpaces
    assert(!json.contains("hunter2") && !json.contains("s3cret-key"), "no credential anywhere in the answer")

  test("a change is written into the text, the rest kept, then reloaded; the same change again changes nothing"):
    val f = Fixture(Some(Commented))
    val w = write(f, "backups.keep" -> Some("8")).fold(e => fail(e.message), identity)
    assertEquals(w.changed, List("histmersion.backups.keep"))
    assertEquals(f.file.get, Some(Commented.replace("keep = 3", "keep = 8")))
    assertEquals(f.reloads.get, 1)
    assert(w.reload.exists(_.published))
    val again = write(f, "backups.keep" -> Some("8")).fold(e => fail(e.message), identity)
    assertEquals(again.changed, Nil)
    assertEquals(f.reloads.get, 1, "nothing changed: no reload")
    write(f, "backups.keep" -> None).fold(e => fail(e.message), identity)
    assert(!f.file.get.get.contains("keep"), f.file.get.get)

  test("refusals carry their code: a refused value, a credential's value, a changed file"):
    val f = Fixture(Some(Commented))
    val bad = write(f, "backups.keep" -> Some("lots")).left.toOption.get
    assertEquals(bad.code, Some(ConfigEdit.CodeRefused))
    assert(bad.message.contains("backups.keep"), bad.message)
    val cred = write(f, "auth.api-key" -> Some("s3cret")).left.toOption.get
    assert(cred.message.contains("HISTMERSION_API_KEY") && !cred.message.contains("s3cret"), cred.message)
    val before = f.c.page(None).toOption.get.version
    f.file.set(Some(Commented + "# edited by hand\n"))
    val stale = f.c.write(ConfigEdit.Write(List(ConfigEdit.Change("backups.keep", Some("4"))), version = Some(before)))
    assertEquals(stale.left.toOption.flatMap(_.code), Some(ConfigEdit.CodeChanged))
    assertEquals(f.reloads.get, 0)
    assert(write(f).isLeft, "no change at all")

  test("a credential as a reference; value: only when confirmed, with a warning"):
    val f = Fixture(None)
    assert(write(f, "plugins.oa-core.api-key" -> Some("value:x")).isLeft)
    val w = f.c
      .write(
        ConfigEdit.Write(List(ConfigEdit.Change("plugins.oa-core.api-key", Some("value:x"))), literalConsent = true)
      )
      .fold(e => fail(e.message), identity)
    assert(w.warnings.exists(_.contains("anyone who can read")), s"${w.warnings}")
    assert(w.created)

  test("restart-only settings are said, and the restart is offered only where the server can be restarted"):
    val f = Fixture(Some(Commented))
    val w = write(f, "server.port" -> Some("8123")).fold(e => fail(e.message), identity)
    assertEquals(w.restart.map(_.key), List("histmersion.server.port"))
    assert(!w.restartable)
    assertEquals(f.c.restart().left.toOption.flatMap(_.code), Some(ConfigEdit.CodeUnavailable))
    val g = Fixture(Some(Commented), restartable = true)
    assert(g.c.restart().exists(_.accepted))
    assertEquals(g.restarts.restarts, 1)

  test("the first-run setup is due for a file that sets nothing, until setup.completed is written"):
    assert(Fixture(None).c.setupDue)
    assert(Fixture(Some("# only comments\n")).c.setupDue)
    assert(!Fixture(Some(Commented)).c.setupDue, "a file of one's own: not a first run")
    val f = Fixture(Some("# only comments\n"))
    write(f, "setup.completed" -> Some("true")).fold(e => fail(e.message), identity)
    assert(!f.c.setupDue)
