package com.histmersion
package cli

import scala.annotation.tailrec

import com.histmersion.api.problem.Answer
import com.histmersion.api.problem.Answer.decoded
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Problem, Problems}

/** G112: every command takes `--json`, and a failure under `--json` prints the body a failed HTTP request answers,
  * `{"problems": [...]}`, whose typed entries decode back to the very problems. Generated over the whole command tree.
  */
class JsonEverywhereSuite extends munit.FunSuite:

  private given console: util.console.Console = util.console.Console.standard

  /** The two commands whose `--json` takes a value (a whole patch as JSON); see the owner question in G112. */
  private val PatchJson = Set(List("patch"), List("article", "patch"))

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

  /** Every command that runs: the paths whose help lists options of their own (a group lists only subcommands). */
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
          Option.when(unknownJson || !json.value)(s"${p.mkString(" ")}: $problems (json ${json.value})")
        case other => Some(s"${p.mkString(" ")}: ${other.toString}")
    }
    assertEquals(wrong, Nil, wrong.mkString("\n"))

  test("a command that runs knows it was asked for --json"):
    CommandLine.parse(List("version", "--json")) match
      case CommandLine.Outcome.Run(_, _, json) => assert(json.value)
      case other                               => fail(other.toString)
    CommandLine.parse(List("version")) match
      case CommandLine.Outcome.Run(_, _, json) => assert(!json.value)
      case other                               => fail(other.toString)

  test("a failure in JSON is the API's body: every typed entry decodes back to its problem, in order"):
    val all: Problems = Problems.of(Problem.examples.head, Problem.examples.tail*)
    assertEquals(RawJson.decode[Answer](Cli.failureJson(all)).flatMap(_.decoded), Right(all))
    val one: Problems = Problem.Invalid.Missing.here(Problem.Field("citekey"))
    assertEquals(RawJson.decode[Answer](Cli.failureJson(one)).flatMap(_.decoded), Right(one))

  test("a command line that does not parse, under --json, is answered with its typed problem"):
    CommandLine.parse(List("search", "--limit", "many", "--json")) match
      case CommandLine.Outcome.Failed(problems, _, json) =>
        assert(json.value)
        assertEquals(RawJson.decode[Answer](Cli.failureJson(problems)).flatMap(_.decoded), Right(problems))
      case other => fail(other.toString)
