import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PluginCapabilityAdapters extends SyntacticRule("PluginCapabilityAdapters") {
  private implicit val dialect: Dialect = dialects.Scala3
  override def afterComplete(): Unit = {
    val sourcePath = Paths.get("modules/plugin-vaults/src/main/scala/com/histmersion/plugins/vaults/Subprocess.scala")
    val input = Files.readString(sourcePath)
    val source = input.parse[Source].get
    val subprocess = source.collect { case obj: Defn.Object if obj.name.value == "Subprocess" => obj }.head
    val methods = subprocess.templ.body.stats.collect { case d: Defn.Def => d.name.value -> d }.toMap
    val session = subprocess.templ.body.stats.collect { case c: Defn.Class if c.name.value == "Session" => c }.head
    val companion = subprocess.templ.body.stats.collect { case o: Defn.Object if o.name.value == "Session" => o }.head
    def normalized(tree: Tree, edits: List[(Int, Int, String)] = Nil): String = {
      val text = edits.sortBy(-_._1).foldLeft(tree.syntax) { case (s, (from, until, replacement)) =>
        s.patch(from - tree.pos.start, replacement, until - from)
      }
      text.linesIterator.zipWithIndex.map { case (line, i) =>
        "  " + (if(i == 0) line else line.drop(tree.pos.startColumn))
      }.mkString("\n")
    }
    val qualifiers = session.collect { case p: Mod.Private if p.within.syntax == "Subprocess" =>
      (p.within.pos.start, p.within.pos.end, "SystemProcesses")
    }
    val sessionText = normalized(session, (session.ctor.pos.end, session.ctor.pos.end, " extends Processes.Session") :: qualifiers)
    val capture = """  def start(argv: List[String], env: Map[String, String]): Either[String, Processes.Session] = Session.start(argv, env)

  def capture(argv: List[String]): Either[String, Result] =
    val tool = argv.headOption.map(a => Path.of(a).getFileName.toString).getOrElse("?")
    Try {
        val proc = new ProcessBuilder(argv.asJava).start()
        val out = drain(proc.getInputStream)
        val err = drain(proc.getErrorStream)
        proc.getOutputStream.close()
        val exit = proc.waitFor()
        Result(exit, out(), err())
      }.toEither.left.map(e => s"$tool could not be run: ${e.getMessage}")
"""
    val content = """package com.histmersion.pluginhost

import com.histmersion.plugin.Processes
import com.histmersion.plugin.Processes.Result
import java.io.{InputStream, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.util.Try

final class SystemProcesses extends Processes:
""" + normalized(methods("run")) + "\n\n" + sessionText + "\n\n" + normalized(companion) + "\n\n" + normalized(methods("drain")) + "\n\n" + capture
    content.parse[Source].get
    val root = Paths.get("target/plugin-capability-adapters")
    Files.createDirectories(root)
    Files.writeString(root.resolve("SystemProcesses.scala"), content)
    List("LocalFiles.scala", "Processes.scala", "DiskLocalFiles.scala").foreach { name =>
      val template = Files.readString(Paths.get("tools/refactor/plugin-capabilities", name))
      template.parse[Source].get
      Files.writeString(root.resolve(name), template)
    }
    def hash(text: String) = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val report = Paths.get("refactor-audit/plugin-capabilities/adapters")
    Files.createDirectories(report)
    Files.write(report.resolve("extraction.txt"), List("Subprocess.run, Session and drain extracted through Scalameta trees; application unchanged.",
      "source-sha256=" + hash(input), "generated-sha256=" + hash(content),
      "Interactive session implements the plugin-api contract; host owns process creation and monotonic waiting.").asJava)
    println("Generated four capability/adapter sources; application unchanged")
  }
}
