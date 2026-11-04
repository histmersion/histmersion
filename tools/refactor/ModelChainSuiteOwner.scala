import scalafix.v1._
import scala.meta._

class ModelChainSuiteOwner extends SemanticRule("ModelChainSuiteOwner") {
  override def fix(implicit doc: SemanticDocument): Patch = {
    val suites = doc.tree.collect { case c: Defn.Class if c.name.symbol.value == "com/histmersion/server/agents/ModelChainRunSuite#" => c }
    if (suites.isEmpty) Patch.empty
    else {
      require(suites.size == 1, "Expected one model-chain suite")
      val pkg = doc.tree.collect { case p: Pkg => p }
      require(pkg.size == 1 && pkg.head.ref.syntax == "com.histmersion.server.agents", "Unexpected package")
      val helpers = Set("RunTranscripts", "ScriptedClient", "ServerEngineSite", "TestRuns")
      val resolved = doc.tree.collect {
        case n: Name if n.symbol.value.startsWith("com/histmersion/server/agents/") && helpers(n.value) => n.value
      }.toSet
      require(resolved == helpers, "Shared fixture symbols changed; review imports")
      (Patch.replaceTree(pkg.head.ref, "com.histmersion.domain.automation.agents") +
        Patch.addRight(pkg.head.ref, "\n\nimport com.histmersion.server.agents.{RunTranscripts, ScriptedClient, ServerEngineSite, TestRuns}")).atomic
    }
  }
}
