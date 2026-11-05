package com.histmersion.util.console

trait Console:
  def readTerminalLine(prompt: com.histmersion.util.console.Console.ReadTerminalLinePrompt): Option[com.histmersion.util.console.Console.ReadTerminalLineResult] = None
  final lazy val out: Console.Output = new Console.Output:
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
  def write(value: Any): Unit
  def writeLine(value: Any): Unit
  def writeLine(): Unit
  def errorWrite(value: => String): Unit
  def errorLine(value: => String): Unit
  def errorLine(): Unit
  def errorFlush(): Unit
  def readLine(): com.histmersion.util.console.Console.ReadLineResult

object Console:
  type ReadTerminalLineResult = ReadTerminalLineResult.T
  object ReadTerminalLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadTerminalLinePrompt = ReadTerminalLinePrompt.T
  object ReadTerminalLinePrompt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadLineResult = ReadLineResult.T
  object ReadLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StreamsInputResult = StreamsInputResult.T
  object StreamsInputResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  trait Output:
    def print(value: => Any): Unit
    def println(value: => Any): Unit
    def println(): Unit
    def flush(): Unit

  def system: Console = Streams(
    () => System.out,
    () => System.err,
    () => scala.io.StdIn.readLine(),
    prompt => Option(System.console()).flatMap(c => Option(c.readLine(prompt)))
  )
  def standard: Console = Streams(() => scala.Console.out, () => scala.Console.err, () => scala.io.StdIn.readLine())
  def streams(out: java.io.PrintStream, err: java.io.PrintStream, input: () => com.histmersion.util.console.Console.StreamsInputResult): Console =
    Streams(() => out, () => err, input, prompt => { err.print(prompt); err.flush(); Option(input()) })

  final private class Streams(
      stdout: () => java.io.PrintStream,
      stderr: () => java.io.PrintStream,
      input: () => String,
      terminal: String => Option[String] = _ => None
  ) extends Console:
    override def readTerminalLine(prompt: String): Option[String] =
      terminal(prompt)
    override def outputFlush(): Unit = stdout().flush()
    def write(value: Any): Unit = stdout().print(String.valueOf(value))
    def writeLine(value: Any): Unit = stdout().println(String.valueOf(value))
    def writeLine(): Unit = stdout().println()
    def errorWrite(value: => String): Unit =
      val stream = stderr()
      stream.print(value)
    def errorLine(value: => String): Unit =
      val stream = stderr()
      stream.println(value)
    def errorLine(): Unit = stderr().println()
    def errorFlush(): Unit = stderr().flush()
    def readLine(): com.histmersion.util.console.Console.Streams.ReadLineResult = input()

  object Streams:
    type ReadLineResult = ReadLineResult.T
    object ReadLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
