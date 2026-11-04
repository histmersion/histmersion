//> using scala 3.10.0
package fixture

object Terminal:
  def errorWrite(value: => String): Unit =
    val stream = Console.err
    stream.print(value)
  def errorLine(value: => String): Unit =
    val stream = Console.err
    stream.println(value)
  def errorLine(): Unit = Console.err.println()
  def errorFlush(): Unit = Console.err.flush()
  def processErrorWrite(value: => String): Unit =
    val stream = System.err
    stream.print(value)
  def processErrorLine(value: => String): Unit =
    val stream = System.err
    stream.println(value)
  def processErrorLine(): Unit = System.err.println()
  def processErrorFlush(): Unit = System.err.flush()

object Verify:
  final class Capture(val bytes: java.io.ByteArrayOutputStream = new java.io.ByteArrayOutputStream)
      extends java.io.PrintStream(bytes, false, "UTF-8"):
    var flushes = 0
    var lines = 0
    override def flush(): Unit = { flushes += 1; super.flush() }
    override def println(value: String): Unit = { lines += 1; super.println(value) }
    def text: String = bytes.toString("UTF-8")
  def main(args: Array[String]): Unit =
    val dynamic = new Capture
    val process = new Capture
    val second = new Capture
    val out = new Capture
    val original = System.err
    try
      System.setErr(process)
      Console.withOut(out) { Console.withErr(dynamic) { Example.exercise(second) } }
    finally System.setErr(original)
    val nl = System.lineSeparator()
    assert(dynamic.text == s"progress\r雪${nl}${nl}callback${nl}x", dynamic.text)
    assert(process.text == s"system${nl}old-stream${nl}", process.text)
    assert(second.text == "new-streamother", second.text)
    assert(out.text.isEmpty)
    assert(dynamic.flushes == 1 && second.flushes == 1 && process.flushes == 0)
    assert(dynamic.lines == 2 && process.lines == 2)
