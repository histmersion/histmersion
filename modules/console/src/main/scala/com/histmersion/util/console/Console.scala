package com.histmersion
package util.console

import io.github.iltotore.iron.constraint.any.Pure

trait Console:
  def readTerminalLine(prompt: Console.Prompt): Option[Console.ReadTerminalLineResult] = None
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
  def readLine(): Console.Line

object Console:
  type Line = Line.T
  object Line extends Newtype[String, Pure]
  type Prompt = Prompt.T
  object Prompt extends Newtype[String, Pure]
  type ReadTerminalLineResult = Line
  val ReadTerminalLineResult: Line.type = Line
  type StreamsInputResult = Line
  val StreamsInputResult: Line.type = Line

  trait Output:
    def print(value: => Any): Unit
    def println(value: => Any): Unit
    def println(): Unit
    def flush(): Unit

  def system: Console = Streams(
    () => System.out,
    () => System.err,
    () => Line(scala.io.StdIn.readLine()),
    prompt => Option(System.console()).flatMap(c => Option(Line(c.readLine((prompt).value))))
  )
  def standard: Console = Streams(() => scala.Console.out, () => scala.Console.err, () => Line(scala.io.StdIn.readLine()))
  def streams(out: java.io.PrintStream, err: java.io.PrintStream, input: () => StreamsInputResult): Console =
    Streams(() => out, () => err, input, prompt => { err.print(prompt); err.flush(); Option(input()) })

  final private class Streams(
      stdout: () => java.io.PrintStream,
      stderr: () => java.io.PrintStream,
      input: () => Streams.InputResult,
      terminal: Streams.TerminalArg1 => Option[Streams.TerminalResult] = _ => None
  ) extends Console:
    override def readTerminalLine(prompt: Prompt): Option[Line] =
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
    def readLine(): Line = input()

  object Streams:
    type InputResult = Line
    val InputResult: Line.type = Line
    type TerminalArg1 = Prompt
    val TerminalArg1: Prompt.type = Prompt
    type TerminalResult = Line
    val TerminalResult: Line.type = Line

    type ReadLineResult = Line
    val ReadLineResult: Line.type = Line
