import scalafix.v1._
import scala.meta._

class ConsoleRegression extends SyntacticRule("ConsoleRegression") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val regression = """test("injected consoles isolate command output, progress, input and errors") {
    val a = new java.io.ByteArrayOutputStream
    val b = new java.io.ByteArrayOutputStream
    val errors = new java.io.ByteArrayOutputStream
    val first = com.histmersion.util.console.Console.streams(new java.io.PrintStream(a, true, "UTF-8"), new java.io.PrintStream(errors, true, "UTF-8"), () => "yes")
    val second = com.histmersion.util.console.Console.streams(new java.io.PrintStream(b, true, "UTF-8"), new java.io.PrintStream(errors, true, "UTF-8"), () => "no")
    def answer(text: String)(using com.histmersion.util.console.Console): Unit = {
      Cli.out.println(text)
      Cli.out.flush()
    }
    answer("first")(using first)
    answer("second")(using second)
    answer("again")(using first)
    Cli.err(using first).println("problem")
    JobFollow.Progress.line("working")(using first)
    assertEquals(first.readLine(), "yes")
    assertEquals(second.readLine(), "no")
    val nl = System.lineSeparator()
    assertEquals(a.toString("UTF-8"), s"first${nl}again${nl}")
    assertEquals(b.toString("UTF-8"), s"second${nl}")
    assertEquals(errors.toString("UTF-8"), s"problem${nl}\\r${" " * JobFollow.Progress.Width}\\rworking".replace("\\r", "\r"))
  }"""
  regression.parse[Stat].get
  private val terminal = """test("injected terminal prompts use supplied streams and input") {
    val out = new java.io.ByteArrayOutputStream
    val err = new java.io.ByteArrayOutputStream
    val console = com.histmersion.util.console.Console.streams(new java.io.PrintStream(out), new java.io.PrintStream(err), () => "yes")
    assertEquals(console.readTerminalLine("Proceed? "), Some("yes"))
    assertEquals(out.toString("UTF-8"), "")
    assertEquals(err.toString("UTF-8"), "Proceed? ")
  }"""
  terminal.parse[Stat].get
  override def fix(implicit doc: SyntacticDocument): Patch = doc.tree.collect {
    case c: Defn.Class if c.name.value == "OutputSuite" && !c.syntax.contains("injected consoles isolate command output") =>
      Patch.addRight(c.templ.stats.last, "\n\n  " + regression)
  }.asPatch + doc.tree.collect {
    case c: Defn.Class if c.name.value == "OutputSuite" && !c.syntax.contains("injected terminal prompts use supplied") =>
      Patch.addRight(c.templ.stats.last, "\n\n  " + terminal)
  }.asPatch
}
