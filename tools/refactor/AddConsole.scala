import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8

class AddConsole extends SyntacticRule("AddConsole") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val source = """package com.histmersion.util.console

trait Console:
  def write(value: Any): Unit
  def writeLine(value: Any): Unit
  def writeLine(): Unit
  def errorWrite(value: => String): Unit
  def errorLine(value: => String): Unit
  def errorLine(): Unit
  def errorFlush(): Unit
  def readLine(): String

object Console:
  def system: Console = Streams(() => System.out, () => System.err, () => scala.io.StdIn.readLine())
  def standard: Console = Streams(() => scala.Console.out, () => scala.Console.err, () => scala.io.StdIn.readLine())
  def streams(out: java.io.PrintStream, err: java.io.PrintStream, input: () => String): Console =
    Streams(() => out, () => err, input)

  private final class Streams(out: () => java.io.PrintStream, err: () => java.io.PrintStream, input: () => String) extends Console:
    def write(value: Any): Unit = out().print(String.valueOf(value))
    def writeLine(value: Any): Unit = out().println(String.valueOf(value))
    def writeLine(): Unit = out().println()
    def errorWrite(value: => String): Unit =
      val stream = err()
      stream.print(value)
    def errorLine(value: => String): Unit =
      val stream = err()
      stream.println(value)
    def errorLine(): Unit = err().println()
    def errorFlush(): Unit = err().flush()
    def readLine(): String = input()
"""
  override def fix(implicit doc: SyntacticDocument): Patch = Patch.empty
  override def afterComplete(): Unit = {
    source.parse[Source].get
    val path = Paths.get("modules/console/src/main/scala/com/histmersion/util/console/Console.scala")
    Files.createDirectories(path.getParent)
    if (Files.exists(path)) require(Files.readString(path) == source, "Console already exists with different content")
    else Files.writeString(path, source, UTF_8)
  }
}
