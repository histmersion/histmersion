import scalafix.v1._
import scala.meta._

class AddCliError extends SyntacticRule("AddCliError") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val methods = """private[cli] def errorWrite(value: => String): Unit =
    val stream = Console.err
    stream.print(value)
  private[cli] def errorLine(value: => String): Unit =
    val stream = Console.err
    stream.println(value)
  private[cli] def errorLine(): Unit = Console.err.println()
  private[cli] def errorFlush(): Unit = Console.err.flush()
  private[cli] def processErrorWrite(value: => String): Unit =
    val stream = System.err
    stream.print(value)
  private[cli] def processErrorLine(value: => String): Unit =
    val stream = System.err
    stream.println(value)
  private[cli] def processErrorLine(): Unit = System.err.println()
  private[cli] def processErrorFlush(): Unit = System.err.flush()

  """
  private val regression = """test("CLI stderr boundary preserves progress, overloads, flush and single evaluation") {
    val bytes = new java.io.ByteArrayOutputStream
    var flushes = 0
    var lines = 0
    val stream = new java.io.PrintStream(bytes, false, "UTF-8") {
      override def flush(): Unit = { flushes += 1; super.flush() }
      override def println(value: String): Unit = { lines += 1; super.println(value) }
    }
    var evaluated = 0
    Console.withErr(stream) {
      Cli.errorWrite({ evaluated += 1; "雪" })
      Cli.errorLine("line")
      Cli.errorLine()
      Cli.errorFlush()
      JobFollow.Progress.line("working")
      JobFollow.Progress.clear()
      intercept[IllegalArgumentException] { Cli.errorLine(throw new IllegalArgumentException("failed")) }
    }
    val nl = System.lineSeparator()
    val blank = " " * JobFollow.Progress.Width
    assertEquals(bytes.toString("UTF-8"), s"雪line${nl}${nl}\r${blank}\rworking\r${blank}\r")
    assertEquals(flushes, 1)
    assertEquals(lines, 1)
    assertEquals(evaluated, 1)
  }"""
  methods.parse[Source].get
  regression.parse[Stat].get
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match {
      case Input.File(p, _) => p.toString
      case Input.VirtualFile(p, _) => p
      case x => x.toString
    }
    if (path.endsWith("/cli/Cli.scala")) doc.tree.collect {
      case o: Defn.Object if o.name.value == "Cli" =>
        val existing = o.templ.stats.collect { case d: Defn.Def if Set("errorWrite", "errorLine", "errorFlush", "processErrorWrite", "processErrorLine", "processErrorFlush")(d.name.value) => d }
        require(existing.isEmpty || existing.size == 8, "Partial CLI stderr boundary")
        if (existing.isEmpty) Patch.addLeft(o.templ.stats.head, methods).atomic else Patch.empty
    }.asPatch
    else if (path.endsWith("/cli/OutputSuite.scala")) doc.tree.collect {
      case c: Defn.Class if c.name.value == "OutputSuite" =>
        if (c.collect { case Lit.String(s) if s == "CLI stderr boundary preserves progress, overloads, flush and single evaluation" => s }.nonEmpty) Patch.empty
        else Patch.addRight(c.templ.stats.last, "\n\n  " + regression).atomic
    }.asPatch
    else Patch.empty
  }
}
