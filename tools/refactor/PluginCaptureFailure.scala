import scalafix.v1._
import scala.meta._

class PluginCaptureFailure extends SyntacticRule("PluginCaptureFailure") {
  private var count = 0
  override def fix(implicit doc: SyntacticDocument): Patch = {
    if(!doc.input.syntax.endsWith("/pluginhost/SystemProcesses.scala")) return Patch.empty
    doc.tree.collect { case d: Defn.Def if d.name.value == "capture" =>
      count += 1
      val body = """val tool = argv.headOption.map(a => Path.of(a).getFileName.toString).getOrElse("?")
Try {
  val proc = new ProcessBuilder(argv.asJava).start()
  val out = drain(proc.getInputStream)
  val err = drain(proc.getErrorStream)
  proc.getOutputStream.close()
  val exit = proc.waitFor()
  Result(exit, out(), err())
}.toEither.left.map(e => s"$tool could not be run: ${e.getMessage}")"""
      val rendered = body.linesIterator.zipWithIndex.map { case (line, i) => (if(i == 0) "" else " " * d.body.pos.startColumn) + line }.mkString("\n")
      Patch.replaceTree(d.body, rendered)
    }.asPatch
  }
  override def afterComplete(): Unit = require(count == 1, "Expected one process capture implementation")
}
