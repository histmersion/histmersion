package com.histmersion
package util.settings
import com.histmersion.util.json.RawJson.*

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.settings.{ConfigEdit, ConfigEditMarkdown, ReloadReport}
import com.histmersion.config.edit.ConfigFileState
import org.ekrich.config.ConfigFactory

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import com.histmersion.api.Api
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SettingsControllersSuite extends munit.FunSuite:

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  final private class Fixture(
      text: Option[ConfigFileState.Text],
      restartable: Boolean = false,
      env: Map[String, String] = Map.empty,
      memory: Option[ByteCount] = None
  ):
    val file = AtomicReference(text)
    val reloads = AtomicInteger(0)
    val settings =
      SettingsService.InMemory(
        file,
        ConfigFactory.parseMap(scala.jdk.CollectionConverters.MapHasAsJava(env).asJava),
        memory = memory
      )
    val restarts = RestartService.InMemory(restartable)
    val c = SettingsControllers(
      settings,
      restarts,
      () => {
        reloads.incrementAndGet();
        ReloadReport(published = ReloadReport.Published.Published, generation = ReloadReport.Generation(2))
      }
    )

  private val Commented = ConfigFileState.Text("# mine\nhistmersion {\n  backups.keep = 3   # three\n}\n")
  private val K = ConfigEdit.Change.Key
  private val V = ConfigEdit.Change.Value

  private def write(
      f: Fixture,
      changes: (ConfigEdit.Change.Key, Option[ConfigEdit.Change.Value])*
  ): Either[Api.Error, ConfigEdit.Written] =
    f.c.write(ConfigEdit.Write(changes.toList.map((k, v) => ConfigEdit.Change(k, v))))

  test("the page: every setting with what the file says, what applies and from where"):
    val f = Fixture(Some(Commented), env = Map("HISTMERSION_TREE_DEPTH" -> "3"))
    val p = f.c.page(None).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(p.field(ConfigEdit.Field.Key("backups.keep")).flatMap(_.value), Some("3"))
    assertEquals(p.field(ConfigEdit.Field.Key("backups.keep")).map(_.source), Some("file"))
    assertEquals(p.field(ConfigEdit.Field.Key("ui.tree-depth")).map(_.source), Some("environment"))
    assertEquals(p.field(ConfigEdit.Field.Key("ui.tree-depth")).flatMap(_.effective), Some("\"3\""))
    assertEquals(p.field(ConfigEdit.Field.Key("lint.max-items")).map(_.source), Some("default"))
    assert(p.sections.map(_.id).contains(ConfigEdit.Section.Id("backups")))
    assert(
      p.sections.find(_.id == ConfigEdit.Section.Id("config")).exists(_.doc.value.nonEmpty),
      "a section carries reference.conf's words"
    )
    assertEquals(
      f.c.page(Some(SettingsService.ConfigPath("backups"))).map(_.sections.map(_.id)),
      Right(List("backups"))
    )
    assert(f.c.page(Some(SettingsService.ConfigPath("not a key!"))).isLeft)

  test("a bundled-Postgres size set to auto shows what it comes to on the server's machine; nothing else does"):
    val f = Fixture(
      Some(ConfigFileState.Text("histmersion.runtime.postgres.effective-cache-size = \"6GB\"\n")),
      memory = Some(ByteCount(51539607552L)) // 48 GB
    )
    val p = f.c.page(None).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    def resolved(k: ConfigEdit.Field.Key) = p.field(k).flatMap(_.resolved)
    assertEquals(
      resolved(ConfigEdit.Field.Key("runtime.postgres.shared-buffers")),
      Some(ConfigEdit.Field.Resolved("2GB"))
    )
    assertEquals(
      p.field(ConfigEdit.Field.Key("runtime.postgres.shared-buffers")).flatMap(_.effective),
      Some(ConfigEdit.Field.Effective("\"auto\""))
    )
    assertEquals(
      resolved(ConfigEdit.Field.Key("runtime.postgres.effective-cache-size")),
      None,
      "a size the file sets is shown as it is"
    )
    assertEquals(resolved(ConfigEdit.Field.Key("runtime.postgres.work-mem")), None)
    assertEquals(resolved(ConfigEdit.Field.Key("runtime.postgres.max-connections")), None)
    assertEquals(resolved(ConfigEdit.Field.Key("backups.keep")), None)
    // the machine's memory unknown (as in a test server): nothing to show
    val unknown = Fixture(None).c.page(None).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(unknown.fields.flatMap(_.resolved), Nil)
    // the CLI and the MCP tool say it beside the value
    assertEquals(
      p.field(ConfigEdit.Field.Key("runtime.postgres.shared-buffers")).map(ConfigEditMarkdown.shown),
      Some("`\"auto\"` (comes to `2GB` on the server's machine)")
    )

  test("a credential's value never crosses: not what the file writes, not what applies, not the default"):
    val f = Fixture(
      Some(ConfigFileState.Text("histmersion.plugins.media-europeana.api-key = \"value:hunter2\"\n")),
      env = Map("HISTMERSION_API_KEY" -> "s3cret-key")
    )
    val p = f.c.page(None).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val europeana = p.field(ConfigEdit.Field.Key("plugins.media-europeana.api-key")).get
    assertEquals(
      europeana.credential,
      Some(
        ConfigEdit.Credential(
          ConfigEdit.Credential.Rule("reference"),
          ConfigEdit.Credential.Form("value"),
          Some(ConfigEdit.Credential.Shown("value:***"))
        )
      )
    )
    assertEquals((europeana.value, europeana.effective, europeana.default), (None, None, None))
    val auth = p.field(ConfigEdit.Field.Key("auth.api-key")).get
    assertEquals(auth.credential.map(_.form), Some("plain"))
    assertEquals((auth.value, auth.effective), (None, None))
    val json = (p).asJson.noSpaces
    assert(!json.contains("hunter2") && !json.contains("s3cret-key"), "no credential anywhere in the answer")

  test("a change is written into the text, the rest kept, then reloaded; the same change again changes nothing"):
    val f = Fixture(Some(Commented))
    val w = write(f, K("backups.keep") -> Some(V("8"))).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(w.changed, List("histmersion.backups.keep"))
    assertEquals(f.file.get, Some(Commented.value.replace("keep = 3", "keep = 8")))
    assertEquals(f.reloads.get, 1)
    assert(w.reload.exists(_.published.published))
    val again =
      write(f, K("backups.keep") -> Some(V("8"))).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(again.changed, Nil)
    assertEquals(f.reloads.get, 1, "nothing changed: no reload")
    write(f, K("backups.keep") -> None).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(!f.file.get.get.value.contains("keep"), f.file.get.get)

  test("refusals carry their code: a refused value, a credential's value, a changed file"):
    val f = Fixture(Some(Commented))
    val bad = write(f, K("backups.keep") -> Some(V("lots"))).left.toOption.get
    assertEquals(bad.code, Some(ConfigEdit.CodeRefused))
    assert(bad.problems.sentences.mkString("; ").contains("backups.keep"), bad.problems.head)
    val cred = write(f, K("auth.api-key") -> Some(V("s3cret"))).left.toOption.get
    assert(
      cred.problems.sentences
        .mkString("; ")
        .contains("HISTMERSION_API_KEY") && !cred.problems.sentences.mkString("; ").contains("s3cret"),
      cred.problems.head
    )
    val before = f.c.page(None).toOption.get.version
    f.file.set(Some(ConfigFileState.Text(Commented.value + "# edited by hand\n")))
    val stale = f.c.write(
      ConfigEdit.Write(
        List(ConfigEdit.Change(ConfigEdit.Change.Key("backups.keep"), Some(ConfigEdit.Change.Value("4")))),
        version = Some(ConfigEdit.Write.Version(before.value))
      )
    )
    assertEquals(stale.left.toOption.flatMap(_.code), Some(ConfigEdit.CodeChanged))
    assertEquals(f.reloads.get, 0)
    assert(write(f).isLeft, "no change at all")

  test("a credential as a reference; value: only when confirmed, with a warning"):
    val f = Fixture(None)
    assert(write(f, K("plugins.oa-core.api-key") -> Some(V("value:x"))).isLeft)
    val w = f.c
      .write(
        ConfigEdit.Write(
          List(
            ConfigEdit
              .Change(ConfigEdit.Change.Key("plugins.oa-core.api-key"), Some(ConfigEdit.Change.Value("value:x")))
          ),
          literalConsent = ConfigEdit.Write.LiteralConsent.Consented
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(w.warnings.exists(_.value.contains("anyone who can read")), s"${w.warnings}")
    assert(w.created.created)

  test("restart-only settings are said, and the restart is offered only where the server can be restarted"):
    val f = Fixture(Some(Commented))
    val w = write(f, K("server.port") -> Some(V("8123"))).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(w.restart.map(_.key), List("histmersion.server.port"))
    assert(!w.restartable.restartable)
    assertEquals(f.c.restart().left.toOption.flatMap(_.code), Some(ConfigEdit.CodeUnavailable))
    val g = Fixture(Some(Commented), restartable = true)
    assert(g.c.restart().exists(_.accepted.accepted))
    assertEquals(g.restarts.restarts, 1)

  test("the first-run setup is due for a file that sets nothing, until setup.completed is written"):
    assert(Fixture(None).c.setupDue)
    assert(Fixture(Some(ConfigFileState.Text("# only comments\n"))).c.setupDue)
    assert(!Fixture(Some(Commented)).c.setupDue, "a file of one's own: not a first run")
    val f = Fixture(Some(ConfigFileState.Text("# only comments\n")))
    write(f, K("setup.completed") -> Some(V("true"))).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(!f.c.setupDue)

  test("an agent may not change the settings that decide what agents may fetch; a person may"):
    val f = Fixture(Some(Commented))
    def byAgent(k: ConfigEdit.Change.Key, v: Option[ConfigEdit.Change.Value]) =
      f.c.writeByAgent(ConfigEdit.Write(List(ConfigEdit.Change(k, v))))
    for k <- List(
        K("acquire.allow-unauthorised"),
        K("histmersion.acquire.allow-unauthorised"),
        K("acquire.agents-may-use-unauthorised")
      )
    do
      assertEquals(
        byAgent(k, Some(V("true"))).left.map(e => util.nonempty.NonEmptyVector.toVector(e.problems)),
        Left(Vector(util.problem.Problem.Refused.Policy(util.problem.Problem.Refused.Rule.PersonHeldSetting))),
        s"$k"
      )
      assert(byAgent(k, None).isLeft, s"unset $k")
    assertEquals(f.file.get, Some(Commented), "nothing was written")
    assertEquals(f.reloads.get, 0)
    // an agent's other changes go through, and a person's change of the same switch does
    byAgent(K("backups.keep"), Some(V("4"))).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    write(f, K("acquire.allow-unauthorised") -> Some(V("true")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(f.file.get.exists(t => s"$t".contains("allow-unauthorised")), f.file.get.toString)
