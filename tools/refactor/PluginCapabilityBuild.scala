import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}

class PluginCapabilityBuild extends SyntacticRule("PluginCapabilityBuild") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3.withAllowToplevelTerms(true)
    val path = Paths.get("build.sbt")
    val input = Files.readString(path)
    val tree = input.parse[Source].get
    val projects = tree.collect { case v: Defn.Val if v.pats.exists {
      case Pat.Var(Term.Name("pluginVaults")) => true
      case _ => false
    } => v }
    val dependencies = projects.flatMap(_.rhs.collect {
      case call: Term.Apply if call.fun.isInstanceOf[Term.Select] && call.fun.asInstanceOf[Term.Select].name.value == "dependsOn" =>
        require(call.argClause.values.map(_.syntax) == List("pluginApi"), "Unexpected vault test dependency state")
        call
    })
    require(dependencies.size == 1, "Expected exactly one vault dependency declaration")
    val result = input.patch(dependencies.head.argClause.values.last.pos.end, ", pluginHost % Test", 0)
    result.parse[Source].get
    require(Files.readString(path) == input, "Build changed during rewrite")
    Files.writeString(path, result)
    println("Applied AST-located vault Test dependency; production dependency unchanged")
  }
}
