//> using scala 3.10.0
package fixture

object Example:
  def exercise(second: java.io.PrintStream): Unit =
    fixture.Terminal.errorWrite("progress\r")
    fixture.Terminal.errorLine("雪")
    fixture.Terminal.errorLine()
    fixture.Terminal.errorFlush()
    fixture.Terminal.processErrorLine("system")
    fixture.Terminal.processErrorLine({ System.setErr(second); "old-stream" })
    fixture.Terminal.processErrorWrite("new-stream")
    fixture.Terminal.processErrorFlush()
    val callback: String => Unit = Console.err.println
    callback("callback")
    Console.err.print(Array('x'))
    object Other:
      val err = second
    Other.err.print("other")
    val literal = "Console.err.println"
    assert(literal.nonEmpty)
