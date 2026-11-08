package com.histmersion
package server.agents

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.automation.agents.{AgentDefinitions, AgentRuntime, AgentTrees, Prompts}

import com.histmersion.config.{AppConfig, LoadedConfig}
import org.ekrich.config.ConfigFactory

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.domain.automation.Pipeline

/** What the automation engine is fed and what it generates, recorded on the code before the engine took contributions:
  * the agent definitions generator's trees for every runtime, written into a scratch directory (every file's path, size
  * and SHA-256, the definitions' fingerprint, the generator's answer and its manifest), and the workflows the engine
  * runs (each one's identifier, version, target, roles and description). `HISTMERSION_UPDATE_ORACLE=1` rewrites
  * `oracle/automation/`.
  */
class AutomationOracleSuite extends munit.FunSuite:

  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private val dir = Path.of("modules/server/src/test/resources/oracle/automation")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private def check(name: String, actual: String): Unit =
    val file = dir.resolve(name)
    if update then
      Files.createDirectories(dir)
      val _ = Files.writeString(file, actual, UTF_8)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_ORACLE=1")
      assertNoDiff(actual, Files.readString(file, UTF_8))

  private val loaded: LoadedConfig =
    AppConfig.loadWithUserText(ConfigFactory.empty, "").fold(e => throw AssertionError(e), identity)

  private def sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"${b & 0xff}%02x").mkString

  test("agent-trees.txt: every generated file of every runtime, the definitions' fingerprint, the answer"):
    val defs = AgentDefinitions(new Prompts(Prompts.OverrideDir("")), EngineContributions.roster)
    val out = Files.createTempDirectory("histmersion-agent-trees")
    val trees = AgentTrees(loaded.config, defs)
    val known = Generated.known(defs)
    val res = trees.generate(known, AgentRuntime.Generated, Some(out)).fold(m => fail(m.value), identity)
    val walk = Files.walk(out)
    val paths =
      try walk.iterator.asScala.toList.filter(p => p != out).sortBy(p => out.relativize(p).toString)
      finally walk.close()
    val lines = paths.map { p =>
      val rel = out.relativize(p).toString
      if Files.isDirectory(p) then s"$rel/"
      else if rel == "manifest.json" then
        val text = Files.readString(p, UTF_8).replaceAll("\"generatedAt\" *: *\"[^\"]*\"", "\"generatedAt\": \"TS\"")
        s"$rel\t${text.length}\t${sha256(text.getBytes(UTF_8))}"
      else
        val b = Files.readAllBytes(p)
        s"$rel\t${b.length}\t${sha256(b)}"
    }
    val head = List(
      s"definitions\t${res.definitions}",
      s"answer\truntimes=${res.runtimes.mkString(",")} files=${res.files} written=${res.written} removed=${res.removed}",
      s"known tools\t${known.toList.sorted.mkString(",")}"
    )
    check("agent-trees.txt", (head ++ lines).mkString("", "\n", "\n"))

  test("workflows.txt: every workflow the engine runs, its roles and the tools its agents may use"):
    val defs = Generated.defs()
    val agents = defs.agents.sortBy(_.name.value).map(a => s"agent ${a.name}\ttools=${a.tools.map(_.value).mkString(",")}")
    val pipelines = Pipeline.values.toList.map { p =>
      s"workflow ${p.wire}\tv${p.version}\t${p.targetKind.wire}\troles=${p.roles.map(_.wire).mkString(",")}\n  ${p.describe}"
    }
    val tools = ToolboxOrder.names.map(t => s"tool ${t.value}")
    check("workflows.txt", (pipelines ++ agents ++ tools).mkString("", "\n", "\n"))
