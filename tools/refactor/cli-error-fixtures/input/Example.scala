//> using scala 3.10.0
package fixture

object Example:
  def exercise(second: java.io.PrintStream): Unit =
    Console.err.print("progress\r")
    Console.err.println("雪")
    Console.err.println()
    Console.err.flush()
    System.err.println("system")
    System.err.println({ System.setErr(second); "old-stream" })
    System.err.print("new-stream")
    System.err.flush()
    val callback: String => Unit = Console.err.println
    callback("callback")
    Console.err.print(Array('x'))
    object Other:
      val err = second
    Other.err.print("other")
    val literal = "Console.err.println"
    assert(literal.nonEmpty)
