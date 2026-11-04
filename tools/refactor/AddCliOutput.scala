import scalafix.v1._
import scala.meta._

class AddCliOutput extends SyntacticRule("AddCliOutput") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val methods = """private[cli] def writeLine(value: Any): Unit = scala.Predef.println(value)
  private[cli] def writeLine(): Unit = scala.Predef.println()
  private[cli] def write(value: Any): Unit = scala.Predef.print(value)

  """
  private val regression = """test("CLI output boundary preserves bytes, channels, callbacks and evaluation") {
    val out = new java.io.ByteArrayOutputStream
    val err = new java.io.ByteArrayOutputStream
    val stdout = new java.io.PrintStream(out, true, "UTF-8")
    val stderr = new java.io.PrintStream(err, true, "UTF-8")
    var evaluated = 0
    var rendered = 0
    def value(): Any = {
      evaluated += 1
      new Object {
        override def toString: String = { rendered += 1; "雪" }
      }
    }
    Console.withOut(stdout) {
      Console.withErr(stderr) {
        Cli.write(value())
        Cli.writeLine()
        Cli.writeLine(null)
        List("a", "b").foreach(Cli.writeLine)
        Output.emit(json = false, "answer")(identity)
        Output.emit(json = true, "answer")(_ => fail("JSON must not render Markdown"))
      }
    }
    val nl = System.lineSeparator()
    assertEquals(out.toString("UTF-8"), s"雪${nl}null${nl}a${nl}b${nl}answer${nl}\"answer\"${nl}")
    assertEquals(err.toString("UTF-8"), "")
    assertEquals(evaluated, 1)
    assertEquals(rendered, 1)
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
        val existing = o.templ.stats.collect { case d: Defn.Def if Set("write", "writeLine")(d.name.value) => d }
        require(existing.isEmpty || existing.size == 3, "Partial CLI output boundary")
        if (existing.isEmpty) Patch.addLeft(o.templ.stats.head, methods).atomic else Patch.empty
    }.asPatch
    else if (path.endsWith("/cli/OutputSuite.scala")) doc.tree.collect {
      case c: Defn.Class if c.name.value == "OutputSuite" =>
        if (c.collect { case Lit.String(s) if s == "CLI output boundary preserves bytes, channels, callbacks and evaluation" => s }.nonEmpty) Patch.empty
        else Patch.addRight(c.templ.stats.last, "\n\n  " + regression).atomic
    }.asPatch
    else Patch.empty
  }
}
