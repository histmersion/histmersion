//> using scala 3.10.0
package fixture

object Terminal:
  def writeLine(value: Any): Unit = scala.Predef.println(value)
  def writeLine(): Unit = scala.Predef.println()
  def write(value: Any): Unit = scala.Predef.print(value)

object Example:
  def exercise(): Unit =
    var evaluated = 0
    def value(): String = { evaluated += 1; "雪" }
    print(value())
    println()
    scala.Predef.println(null)
    List("a", "b").foreach(println)
    Predef.print("tail")
    Console.err.print("progress")
    assert(evaluated == 1)
    object Other:
      def println(value: String): String = value
    assert(Other.println("unchanged") == "unchanged")
    val text = "println is not code"
    assert(text.nonEmpty)
    var receiverCalls = 0
    def receiver(): Predef.type = { receiverCalls += 1; Predef }
    receiver().println("receiver")
    assert(receiverCalls == 1)

  def main(args: Array[String]): Unit =
    val out = new java.io.ByteArrayOutputStream
    val err = new java.io.ByteArrayOutputStream
    val stdout = new java.io.PrintStream(out, true, "UTF-8")
    val stderr = new java.io.PrintStream(err, true, "UTF-8")
    Console.withOut(stdout) { Console.withErr(stderr) { exercise() } }
    val nl = System.lineSeparator()
    assert(out.toString("UTF-8") == s"雪${nl}null${nl}a${nl}b${nl}tailreceiver${nl}")
    assert(err.toString("UTF-8") == "progress")
