import scalafix.v1._
import scala.meta._

class ConsoleApi extends SyntacticRule("ConsoleApi") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val channels = """final lazy val out: Console.Output = new Console.Output:
    def print(value: => Any): Unit = write(value)
    def println(value: => Any): Unit = writeLine(value)
    def println(): Unit = writeLine()
    def flush(): Unit = outputFlush()
  final lazy val err: Console.Output = new Console.Output:
    def print(value: => Any): Unit = errorWrite(String.valueOf(value))
    def println(value: => Any): Unit = errorLine(String.valueOf(value))
    def println(): Unit = errorLine()
    def flush(): Unit = errorFlush()
  def outputFlush(): Unit = ()
  """
  private val output = """trait Output:
    def print(value: => Any): Unit
    def println(value: => Any): Unit
    def println(): Unit
    def flush(): Unit

  """
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case x => x.toString }
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    if (path.endsWith("/util/console/Console.scala")) {
      doc.tree.collect {
        case t: Defn.Trait if t.name.value == "Console" && !t.templ.stats.exists(_.syntax.contains("val out:")) =>
          patches += Patch.addLeft(t.templ.stats.head, channels)
        case o: Defn.Object if o.name.value == "Console" && !o.templ.stats.exists { case t: Defn.Trait => t.name.value == "Output"; case _ => false } =>
          patches += Patch.addLeft(o.templ.stats.head, output)
        case c: Defn.Class if c.name.value == "Streams" && !c.templ.stats.exists { case d: Defn.Def => d.name.value == "outputFlush"; case _ => false } =>
          patches += Patch.addLeft(c.templ.stats.head, "override def outputFlush(): Unit = out().flush()\n    ")
      }
    }
    if (path.contains("/cli/src/main/")) {
      doc.tree.collect {
        case o: Defn.Object if o.name.value == "Cli" && !o.templ.stats.exists { case d: Defn.Def => d.name.value == "out"; case _ => false } =>
          patches += Patch.addLeft(o.templ.stats.head, "def out(using console: com.histmersion.util.console.Console): com.histmersion.util.console.Console.Output = console.out\n  def err(using console: com.histmersion.util.console.Console): com.histmersion.util.console.Console.Output = console.err\n\n  ")
        case s: Term.Select if s.qual.syntax == "console" && Set("write", "writeLine", "errorWrite", "errorLine", "errorFlush")(s.name.value) =>
          val (stream, op) = s.name.value match {
            case "write" => ("out", "print")
            case "writeLine" => ("out", "println")
            case "errorWrite" => ("err", "print")
            case "errorLine" => ("err", "println")
            case "errorFlush" => ("err", "flush")
          }
          patches += Patch.replaceTree(s, "Cli." + stream + "." + op)
        case d: Defn.Def if d.name.value == "histmersionCli" && !d.body.syntax.contains("clearHandlers") =>
          patches += Patch.addLeft(d.body.asInstanceOf[Term.Block].stats.head, "val _ = scribe.Logger.root.clearHandlers().withHandler(writer = scribe.writer.SystemErrWriter).replace()\n  ")
      }
    }
    if (path.endsWith("/server/Main.scala")) doc.tree.collect {
      case d: Defn.Def if d.name.value == "histmersionServer" && !d.body.syntax.contains("Console.system") =>
        patches += Patch.addLeft(d.body.asInstanceOf[Term.Block].stats.head, "val console = com.histmersion.util.console.Console.system\n  ")
      case s: Term.Select if s.syntax == "System.out.println" => patches += Patch.replaceTree(s, "console.out.println")
      case s: Term.Select if s.syntax == "System.err.println" => patches += Patch.replaceTree(s, "console.err.println")
    }
    patches.toList.asPatch
  }
}
