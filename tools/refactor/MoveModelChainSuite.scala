import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}

class MoveModelChainSuite extends SyntacticRule("MoveModelChainSuite") {
  private val from = Paths.get("modules/server/src/test/scala/com/histmersion/server/agents/ModelChainRunSuite.scala").toAbsolutePath.normalize
  private val to = Paths.get("modules/server/src/test/scala/com/histmersion/domain/automation/agents/ModelChainRunSuite.scala").toAbsolutePath.normalize
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match {
      case Input.File(p, _) => p.toNIO.toAbsolutePath.normalize
      case Input.VirtualFile(p, _) => Paths.get(p).toAbsolutePath.normalize
    }
    if (path == from) {
      require(doc.tree.collect { case p: Pkg => p.ref.syntax } == List("com.histmersion.domain.automation.agents"), "Change ownership before moving")
      require(doc.tree.collect { case c: Defn.Class => c.name.value } == List("ModelChainRunSuite"), "Unexpected declarations")
      require(Files.readString(from) == doc.input.text, "Source changed")
      require(!Files.exists(to), "Destination exists")
      Files.createDirectories(to.getParent)
      Files.move(from, to)
    }
    Patch.empty
  }
  override def afterComplete(): Unit = require(!Files.exists(from) && Files.exists(to), "Move incomplete")
}
