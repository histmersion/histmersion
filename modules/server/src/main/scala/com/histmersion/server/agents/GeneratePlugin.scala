package com.histmersion.server.agents

import io.circe.syntax.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The repository's checked-in copy of the interactive Claude Code plugin — `plugin/` and the root
  * `.claude-plugin/marketplace.json` — for people without a running server. A generator output like the rest;
  * `PluginTreeSuite` fails when it is out of date. Regenerate with `just plugin` (`sbt "server/Test/runMain
  * com.histmersion.server.agents.GeneratePlugin <repo root>"` — the test classpath runs it in process, outside the
  * native-image tracing agent).
  */
object GeneratePlugin:

  /** The tree from the bundled definitions (no server, no override directory). */
  def tree(): Either[String, Map[String, String]] =
    val defs = AgentDefinitions(Prompts(""))
    for
      as <- defs.agents
      fs <- defs.fragments
      ss <- defs.skills
    yield AgentGenerator.repoPlugin(AgentGenerator.Defs(as, fs, ss))

  /** The generated directories the tree owns: anything else in them is stale. */
  val Owned: List[String] = List("plugin/agents", "plugin/skills")

  def main(args: Array[String]): Unit =
    val root = Path.of(args.headOption.getOrElse(".")).toAbsolutePath.normalize
    tree() match
      case Left(m)  => System.err.println(s"cannot generate the plugin: $m"); sys.exit(1)
      case Right(t) =>
        Owned.map(root.resolve).filter(Files.exists(_)).foreach { d =>
          val all = Files.walk(d)
          try
            all.sorted(java.util.Comparator.reverseOrder()).forEach { p =>
              val rel = root.relativize(p).toString
              if Files.isSymbolicLink(p) || (Files.isRegularFile(p) && !t
                  .contains(rel)) || (Files.isDirectory(p) && p != d && !t.keys.exists(_.startsWith(rel + "/")))
              then
                val _ = Files.deleteIfExists(p)
            }
          finally all.close()
        }
        t.foreach { (rel, text) =>
          val f = root.resolve(rel)
          if Files.isSymbolicLink(f.getParent) then Files.delete(f.getParent)
          Files.createDirectories(f.getParent)
          Files.writeString(f, text, UTF_8)
        }
        System.err.println(s"wrote ${t.size} file(s) under $root")
