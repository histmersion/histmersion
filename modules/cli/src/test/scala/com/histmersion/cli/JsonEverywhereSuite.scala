package com.histmersion
package cli

import scala.annotation.tailrec

import com.histmersion.api.problem.Answer
import com.histmersion.api.problem.Answer.decoded
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class JsonEverywhereSuite extends munit.FunSuite:

  private given console: util.console.Console = util.console.Console.standard

  private val PatchJson = Set(List("patch"), List("article", "patch"))

  private val Asked = Cli.JsonOutput.Json

  private def help(path: List[String]): String =
    CommandLine.parse(path :+ "--help") match
      case CommandLine.Outcome.Help(text) => text.printed
      case other                          => s"(no help for ${path.mkString(" ")}: ${other.toString})"

  private def subcommands(text: String): List[String] =
    text.linesIterator
      .dropWhile(l => !l.startsWith("Subcommands:"))
      .drop(1)
      .collect { case l if l.matches("    [a-z][a-z0-9-]*") => l.trim }
      .toList

  private lazy val leaves: List[List[String]] =
    @tailrec def loop(todo: List[List[String]], done: List[List[String]]): List[List[String]] =
      todo match
        case Nil          => done.reverse
        case path :: rest =>
          val text = help(path)
          val subs = subcommands(text)
          val runs = path.nonEmpty && (subs.isEmpty || text.linesIterator.exists(_.trim.startsWith("--json")))
          loop(subs.map(path :+ _) ++ rest, if runs then path :: done else done)
    loop(List(Nil), Nil)

  test("the tree has its commands"):
    assert(leaves.size > 150, s"${leaves.size}")

  test("every command lists --json in its help (the two patch commands: their --json is the patch)"):
    val missing = leaves.filterNot(p => PatchJson(p)).filterNot { p =>
      help(p).linesIterator.exists(l => l.trim.startsWith("--json ") || l.trim == "--json")
    }
    assertEquals(missing.map(_.mkString(" ")), Nil)
    PatchJson.foreach(p => assert(help(p).contains("--json <"), p.mkString(" ")))

  test("every command takes --json: a failure under it is answered in JSON, never 'unknown option --json'"):
    val wrong = leaves.filterNot(PatchJson).flatMap { p =>
      CommandLine.parse(p ++ List("--json", "--no-such-option-g112")) match
        case CommandLine.Outcome.Failed(problems, _, json) =>
          val unknownJson = problems.toVector.exists {
            case Problem.Invalid.Unknown(_, raw, _, _) => s"$raw".contains("--json")
            case _                                     => false
          }
          Option.when(unknownJson || json != Asked)(s"${p.mkString(" ")}: $problems (json ${json.asJson})")
        case other => Some(s"${p.mkString(" ")}: ${other.toString}")
    }
    assertEquals(wrong, Nil, wrong.mkString("\n"))

  test("a command that runs knows it was asked for --json"):
    CommandLine.parse(List("version", "--json")) match
      case CommandLine.Outcome.Run(_, _, _, json) => assertEquals(json, Asked)
      case other                                  => fail(other.toString)
    CommandLine.parse(List("version")) match
      case CommandLine.Outcome.Run(_, _, _, json) => assertEquals(json, Cli.JsonOutput.Text)
      case other                                  => fail(other.toString)

  test("a failure in JSON is the API's body: every typed entry decodes back to its problem, in order"):
    val all: Problems = Problems.of(Problem.examples.head, Problem.examples.tail*)
    assertEquals(RawJson.decode[Answer](Cli.Error(all).json).flatMap(_.decoded), Right(all))
    val one: Problems = Problem.Invalid.Missing.here(Problem.Field("citekey"))
    assertEquals(RawJson.decode[Answer](Cli.Error(one).json).flatMap(_.decoded), Right(one))

  test("a command line that does not parse, under --json, is answered with its typed problem"):
    CommandLine.parse(List("search", "--limit", "many", "--json")) match
      case CommandLine.Outcome.Failed(problems, _, json) =>
        assertEquals(json, Asked)
        assertEquals(RawJson.decode[Answer](Cli.Error(problems).json).flatMap(_.decoded), Right(problems))
      case other => fail(other.toString)
