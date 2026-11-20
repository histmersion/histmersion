import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._

/** G94: writes the reviewed reason of each dropped write answer the guideline inventory found (`discardedOutcome`), as
  * `Unused(<answer>, Unused.Why.<Why>)` in place of `val _ = <answer>` or of the bare statement. The plan is a reviewed
  * TSV of `path, line, callee, why` rows (the inventory's path and line, the callee's name as a check, the reason);
  * every row must match exactly one call starting on its line, or the run fails naming it. The import of `Unused` is
  * added where the file lacks it. A rewrite only: the reasons are the plan's, decided site by site.
  */
object AcceptOutcome {
  case class Policy(plan: String = "")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  case class Row(path: String, line: Int, callee: String, why: String)
  val whys = Set("Idempotent", "Held", "Reported", "ReadBack")
  def read(plan: String): List[Row] =
    Files.readAllLines(Paths.get(plan), UTF_8).asScala.toList.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#")).map { l =>
      val c = l.split("\t", -1)
      require(c.length == 4 && whys(c(3)), "Malformed plan row: " + l)
      Row(c(0), c(1).toInt, c(2), c(3))
    }
}

class AcceptOutcome(rows: List[AcceptOutcome.Row]) extends SyntacticRule("AcceptOutcome") {
  def this() = this(Nil)
  import AcceptOutcome._

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("AcceptOutcome")(Policy()).map(p => new AcceptOutcome(if (p.plan.isEmpty) Nil else read(p.plan)))

  private def relative(p: String): String = {
    val here = Paths.get("").toAbsolutePath.normalize
    val abs = Paths.get(p).toAbsolutePath.normalize
    if (abs.startsWith(here)) here.relativize(abs).toString else p
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = relative(doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case o => o.toString })
    val mine = rows.filter(_.path == path)
    if (mine.isEmpty) Patch.empty
    else {
      def calleeName(t: Term): String = t match {
        case a: Term.Apply => calleeName(a.fun)
        case Term.ApplyType(f, _) => calleeName(f)
        case Term.Select(_, n) => n.value
        case n: Term.Name => n.value
        case _ => ""
      }
      // the outermost call starting on the line whose callee is the plan's
      val calls = doc.tree.collect { case a: Term.Apply => a }
      val patches = mine.map { r =>
        val found = calls.filter(a => a.pos.startLine + 1 == r.line && calleeName(a) == r.callee)
          .filterNot(a => a.parent.exists { case p: Term.Apply => (p.fun eq a) && calleeName(p) == r.callee; case _ => false })
        require(found.size == 1, s"$path:${r.line}: ${found.size} calls of ${r.callee} start on the line, expected one")
        val call = found.head
        val wrapped = s"Unused(${call.syntax}, Unused.Why.${r.why})"
        call.parent match {
          case Some(v: Defn.Val) if (v.rhs eq call) && (v.pats match { case List(_: Pat.Wildcard) => true; case _ => false }) =>
            Patch.replaceTree(v, wrapped)
          case _ => Patch.replaceTree(call, wrapped)
        }
      }
      val text = doc.input.text
      val inPackage = text.contains("package util.database") && text.contains("package com.histmersion")
      val imported = text.contains("import com.histmersion.util.database.Unused") ||
        "import com\\.histmersion\\.util\\.database\\.\\{[^}]*\\bUnused\\b".r.findFirstIn(text).nonEmpty
      val importPatch =
        if (inPackage || imported) Patch.empty
        else {
          val imports = doc.tree.collect { case i: Import => i }.filter(i => i.parent.exists(p => p.is[Pkg.Body] || p.is[Pkg] || p.is[Source]))
          imports.lastOption.map(i => Patch.addRight(i, "\nimport com.histmersion.util.database.Unused"))
            .orElse(doc.tree.collect { case p: Pkg => p }.lastOption.map(p => Patch.addRight(p.ref, "\n\nimport com.histmersion.util.database.Unused")))
            .getOrElse(Patch.empty)
        }
      (patches :+ importPatch).asPatch
    }
  }
}
