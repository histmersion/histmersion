package com.histmersion.linting

import scalafix.v1._
import metaconfig.Configured
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}

final class G3Inventory(scalaVersion: String) extends SemanticRule("G3Inventory") {
  def this() = this("")

  override def withConfiguration(config: Configuration): Configured[Rule] =
    Configured.ok(new G3Inventory(config.scalaVersion))

  override def fix(using doc: SemanticDocument): Patch = {
    val rel = Project.relative(doc)
    val rows = Guidelines.findings(doc, scalaVersion).filter(_.rule == "G3Misplaced").map { f =>
      s"$rel\t${f.pos.startLine + 1}\t${f.message}\n"
    }
    if (rows.nonEmpty) G3Inventory.synchronized {
      val out = Path.of(sys.env.getOrElse("G3_INVENTORY", "refactor-audit/lane-objects/g3.tsv"))
      Files.createDirectories(out.getParent)
      Files.write(out, rows.mkString.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
    Patch.empty
  }
}

object G3Inventory
