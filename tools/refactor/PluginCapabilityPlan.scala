import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PluginCapabilityPlan extends SemanticRule("PluginCapabilityPlan") {
  private val output = Paths.get("refactor-audit/plugin-capabilities")
  private val calls = scala.collection.mutable.ListBuffer.empty[List[String]]
  private val files = scala.collection.mutable.ListBuffer.empty[String]
  private def escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = Paths.get("").toAbsolutePath.normalize.relativize(Paths.get(doc.input.syntax).toAbsolutePath.normalize).toString
    files += path
    val plugin = path.startsWith("modules/plugin-vaults/") || path.startsWith("modules/plugins/calibre/")
    def selected(symbol: String): Boolean =
      symbol.startsWith("com/histmersion/plugins/vaults/Subprocess.") ||
      symbol.startsWith("com/histmersion/plugins/calibre/Shell.") ||
      symbol.startsWith("com/histmersion/pluginhost/HostServices") ||
      symbol == "com/histmersion/plugin/Plugin.Context#" ||
      plugin && (symbol.startsWith("java/nio/file/Files#") || symbol.startsWith("java/lang/ProcessBuilder#"))
    def owner(tree: Tree): String = tree.parent match {
      case Some(d: Defn.Def) => d.name.symbol.value
      case Some(d: Defn.Class) => d.name.symbol.value
      case Some(d: Defn.Object) => d.name.symbol.value
      case Some(parent) => owner(parent)
      case None => ""
    }
    doc.tree.collect { case name: Name if selected(name.symbol.value) =>
      val surrounding = name.parent.getOrElse(name)
      val expression = surrounding.parent match {
        case Some(a: Term.Apply) => a
        case Some(a: Term.ApplyType) => a
        case _ => surrounding
      }
      val role = surrounding match {
        case _: Init => "construction-or-parent"
        case _: Importee => "import"
        case _ => "reference-or-declaration"
      }
      calls += List(path, if(path.contains("/src/test/")) "test" else "production", (name.pos.startLine + 1).toString,
        owner(name), name.symbol.value, role, expression.syntax, expression.structure)
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    Files.createDirectories(output)
    require(files.distinct.size == files.size, "Duplicate scanned paths")
    val stream = Files.walk(Paths.get("modules"))
    val expected = try stream.iterator.asScala.filter(p => p.toString.endsWith(".scala") &&
      (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toSet finally stream.close()
    require(files.toSet == expected, "Missing or unexpected project sources")
    val decisions = Files.readAllLines(Paths.get("refactor-audit/capability-treatment/review-1/decisions.tsv")).asScala.drop(1)
      .map(_.split("\t", -1).toList).toList
    val explicitIds = decisions.filter(_.head == "id").map(_(1)).toSet
    val review = decisions.filter(_(5) == "plugin-local-capabilities")
    val findingRows = Files.readAllLines(Paths.get("refactor-audit/capability-history/observations/2026-11-04T23-54-37.816223Z/findings.tsv"))
      .asScala.drop(1).map(_.split("\t", -1).toList).toList
    val findings = review.flatMap { decision =>
      require(decision.head == "group" && decision(2) == "confirmed")
      val found = findingRows.filter(r => !explicitIds(r.head) && r(4) + ":" + r(5) == decision(1))
      require(found.map(_(1).toInt).sum == decision(4).toInt, "Frozen review multiplicity changed")
      found
    }
    require(findings.map(_(1).toInt).sum == 25 && findings.map(_.head).distinct.size == findings.size)
    require(findings.forall(f => calls.exists(r => r.head == f(5) && r(4) == f(7))), "Confirmed symbol lacks AST evidence")
    def write(name: String, rows: List[String]): Unit = Files.write(output.resolve(name), rows.asJava)
    write("calls.tsv", "path\tscope\tline\towner\tsymbol\trole\tsyntax\tAST" :: calls.toList.sortBy(r => (r.head, r(2).toInt, r(4))).map(_.map(escape).mkString("\t")))
    write("confirmed.tsv", "id\tcount\tscope\tidentity-confidence\trule\tpath\towner\tsymbol\tast" :: findings.sortBy(_.head).map(_.mkString("\t")))
    write("files.txt", files.toList.sorted)
    def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    write("sources.txt", List("sources=" + hash(files.toList.sorted.map(p => p + "\t" + hash(Files.readString(Paths.get(p)))).mkString("\n"))))
    val summary = List("PASS: complete source traversal; every frozen plugin finding has resolved AST evidence", s"source-files=${files.size}",
      s"confirmed-occurrences=${findings.map(_(1).toInt).sum}", s"confirmed-anchors=${findings.size}",
      s"production-references=${calls.count(_(1) == "production")}", s"test-references=${calls.count(_(1) == "test")}",
      "References include declarations/imports and are not additional violations.",
      "No dynamic-dispatch, reflection or complete transitive effect claim; no detector precision/recall claim.")
    write("status.txt", summary); println(summary.mkString("\n"))
  }
}
