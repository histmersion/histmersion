import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PluginContextCapabilities extends SemanticRule("PluginContextCapabilities") {
  private var count = 0
  override def fix(implicit doc: SemanticDocument): Patch = {
    if(!doc.input.syntax.contains("/modules/plugins/calibre/src/test/")) return Patch.empty
    doc.tree.collect {
      case n: Term.NewAnonymous if n.templ.inits.exists(_.tpe.symbol.value == "com/histmersion/plugin/Plugin.Context#") =>
        require(!n.templ.body.stats.exists { case d: Defn.Def => Set("files", "processes")(d.name.value); case _ => false })
        count += 1
        val first = n.templ.body.stats.head
        val indent = " " * first.pos.startColumn
        Patch.addLeft(first, "override def files: com.histmersion.plugin.LocalFiles = summon[com.histmersion.plugin.LocalFiles]\n" + indent +
          "override def processes: com.histmersion.plugin.Processes = summon[com.histmersion.plugin.Processes]\n" + indent)
    }.asPatch
  }
  override def afterComplete(): Unit = {
    require(count == 1, "Expected one unbound anonymous test context")
    val dir = Paths.get("refactor-audit/plugin-capabilities/calibre-tests")
    List("tsv", "txt").foreach { extension => Files.copy(dir.resolve("after." + extension), dir.resolve("failed-attempt-1." + extension)) }
    Files.write(dir.resolve("correction.txt"), List("Anonymous Plugin.Context in CalibreCollectionSuite receives existing supplied capabilities.",
      "Original test assertions unchanged. First failing test reports retained; subsequent after reports describe the corrected wiring.",
      "This correction follows the initial frozen preview; applied-verification.txt records that earlier snapshot, not this corrected test file.").asJava)
    println("Wired one anonymous test context; preserved failed test evidence")
  }
}
