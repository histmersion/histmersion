package consolerepaircontract

import com.histmersion.util.console.Console
import com.histmersion.fixtures.hellolib.Shout

object ConsoleContract:
  @main def verifyConsoleRepair(): Unit =
    assert(Shout(Shout.ApplyText("hello")).value == "HELLO!")
    val out = new java.io.ByteArrayOutputStream
    val err = new java.io.ByteArrayOutputStream
    val console = Console.streams(new java.io.PrintStream(out), new java.io.PrintStream(err), () => Console.ReadLineResult("answer"))
    console.out.print("out")
    console.out.println("put")
    console.err.print("err")
    console.err.println("or")
    assert(out.toString("UTF-8") == "output" + System.lineSeparator())
    assert(err.toString("UTF-8") == "error" + System.lineSeparator())
    assert(console.readLine().value == "answer")
    assert(console.readTerminalLine(Console.ReadTerminalLinePrompt("prompt> ")).map(_.value).contains("answer"))
    assert(err.toString("UTF-8").endsWith("prompt> "))
    val eof = Console.streams(new java.io.PrintStream(out), new java.io.PrintStream(err), () => Console.ReadLineResult(null))
    assert(eof.readLine().value == null)
    assert(eof.readTerminalLine(Console.ReadTerminalLinePrompt("end> ")).isEmpty)
    scala.Console.withIn(new java.io.ByteArrayInputStream("standard\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
      assert(Console.standard.readLine().value == "standard")
      assert(Console.standard.readLine().value == null)
    }
