package com.histmersion
package cli
import com.histmersion.util.nonempty.NonEmptyVector

import com.histmersion.api.Api
import com.histmersion.api.aliases.LabelRename
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Filter
import com.histmersion.api.research.{Article, ReviewGoal}
import com.histmersion.domain.research.Slug
import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.util.problem.Problem
import com.histmersion.cli.CommandLine.given
import org.rogach.scallop.*

import java.nio.file.Path

/** `histmersion article …` through scallop ([[CommandLine]]): the typed command for each argv, and the failures as
  * typed problems.
  */
class CommandLineSuite extends munit.FunSuite:

  private def scallop(args: String*): CommandLine.Outcome = CommandLine.parse("article" +: args)

  private def run(args: String*): ArticleCommand = scallop(args*) match
    case CommandLine.Outcome.Run(_, _, cmd: ArticleCommand) => cmd
    case other                                              => fail(s"${args.mkString(" ")}: ${other.toString}")

  private def problem(args: String*): Problem = scallop(args*) match
    case CommandLine.Outcome.Failed(p, _) => p
    case other                            => fail(s"${args.mkString(" ")}: ${other.toString}")

  private val json = Cli.JsonOutput(false)
  private val slug = ReviewGoal.Slug("sea_peoples")

  /** Every subcommand, with its optional, repeated and flag arguments, in the shapes people write them. */
  private val argvs: List[List[String]] = List(
    List("list"),
    List("list", "bronze age", "--kind", "myth", "--json"),
    List("list", "--tag", "a", "--tag", "b,c", "--place", "Crete", "--from", "-1600", "--to", "-1100", "q"),
    List("list", "--bbox", "20,30,40,50", "--sort", "year", "--limit", "5", "--offset=10", "--goal", "g1"),
    List("list", "--year-from", "1990", "--year-to", "2020", "--title", "Sea", "--status", "current"),
    List("get", "sea_peoples"),
    List("get", "sea_peoples", "--parts", "body, Summary", "--json"),
    List("put", "wiki/myths/a.md"),
    List("put", "--kind", "myth", "a.md", "--strict"),
    List("patch", "sea_peoples"),
    List(
      "patch",
      "sea_peoples",
      "--title",
      "The Sea Peoples",
      "--summary",
      "Who they were",
      "--kind",
      "concept",
      "--period",
      "-1300,-1100",
      "--tags-add",
      "a,b",
      "--strict",
      "--reason",
      "x2001_a=off topic",
      "--reason",
      "y2002_b=",
      "--extra-query",
      "q1",
      "--extra-query",
      "q2",
      "--filter-query",
      "sea peoples",
      "--filter-period=-1300,-1000",
      "--critique-reason",
      "a>b=not relevant",
      "--if-updated-at",
      "2026-11-01T00:00:00Z"
    ),
    List("patch", "s", "--json", "{}", "--body-file", "b.md", "--append-file", "c.md", "--clear-filter"),
    List("patch", "s", "--tags-remove", "x", "--tags-replace", "y", "--regions", "Aegean", "--filter-tags", "t"),
    List("patch", "s", "--filter-topics", "t", "--filter-status", "current", "--filter-places", "Crete"),
    List("lint", "sea_peoples"),
    List("lint", "sea_peoples", "--fix", "--json"),
    List("check", "sea_peoples", "--edits-file", "e.json"),
    List("check", "sea_peoples", "--body-file", "b.md", "--kind", "index", "--json"),
    List("coverage", "sea_peoples", "--json"),
    List("review", "sea_peoples"),
    List("delete", "sea_peoples"),
    List("export", "out/wiki"),
    List("import", "wiki", "--force"),
    List("versions", "sea_peoples"),
    List("version", "sea_peoples", "42"),
    List("rename", " old_slug ", "new_slug", "--json")
  )

  test("every subcommand parses, in the shapes people write them"):
    argvs.foreach(argv => assert(scallop(argv*).isInstanceOf[CommandLine.Outcome.Run], argv.mkString(" ")))

  test("the values arrive typed: a repeated option in order, a negative year, an option written --name=value"):
    assertEquals(
      run("version", "sea_peoples", "42"),
      ArticleCommand.Version(slug, Article.VersionId(42L))
    )
    run("list", "--tag", "a", "--tag", "b,c", "--from", "-1600", "--offset=10", "q") match
      case ArticleCommand.Listing(text, None, filter, `json`) =>
        assertEquals(text, Some(Api.ArticleQuery.Text("q")))
        assertEquals(filter.tags, List(Filter.Tags("a"), Filter.Tags("b"), Filter.Tags("c")))
        assertEquals(filter.offset, Filter.Offset(10))
        assert(filter.period.isDefined)
      case other => fail(s"${other.toString}")
    run("patch", "s", "--reason", "a=1", "--reason", "b=", "--period", "-1300,-1100") match
      case ArticleCommand.Patch(_, edit, _, _, reasons, _) =>
        assertEquals(reasons, List(ArticlePatches.ReasonText("a=1"), ArticlePatches.ReasonText("b=")))
        assertEquals(edit.period, Some(ArticlePatches.FilterFlags.Period("-1300,-1100")))
      case other => fail(s"${other.toString}")

  test("the global options before the group"):
    assertEquals(
      CommandLine.parse(List("--server", "http://h:1", "--token=t", "article", "delete", "sea_peoples")),
      CommandLine.Outcome.Run(
        Some(Client.ServerUrl("http://h:1")),
        Some(Client.Token("t")),
        ArticleCommand.Delete(slug)
      )
    )

  test("a missing positional argument is Missing"):
    assertEquals(problem("get"), Problem.Invalid.Missing.here(Problem.Field("slug")))
    assertEquals(problem("version", "sea_peoples"), Problem.Invalid.Missing.here(Problem.Field("id")))

  test("an unknown option is Unknown, with the help of the subcommand it was given to"):
    scallop("get", "sea_peoples", "--bogus") match
      case CommandLine.Outcome.Failed(p, help) =>
        assertEquals(
          p,
          Problem.Invalid.Unknown.here(
            Problem.TypeName("CommandLine.OptionName"),
            Problem.Raw("--bogus"),
            Vector(Problem.Raw("--json"), Problem.Raw("--parts"))
          )
        )
        assert(help.mentions("--parts"), help.printed)
      case other => fail(s"${other.toString}")

  test("a value its newtype's base type cannot read is Malformed, naming the newtype and the argument"):
    problem("version", "sea_peoples", "forty-two") match
      case Problem.Invalid.Malformed(t, _, raw, at) =>
        assertEquals(t, Problem.TypeName("Article.VersionId"))
        assertEquals(raw, Problem.Raw("forty-two"))
        assertEquals(at, Vector(Problem.Field("id")))
      case other => fail(s"${other.toString}")
    problem("list", "--limit", "many") match
      case Problem.Invalid.Malformed(t, _, raw, at) =>
        assertEquals(t, Problem.TypeName("Filter.Limit"))
        assertEquals((raw, at), (Problem.Raw("many"), Vector(Problem.Field("limit"))))
      case other => fail(s"${other.toString}")

  test("a constrained newtype's converter checks the constraint"):
    val c = summon[ValueConverter[Slug]]
    assertEquals(c.parse(List("slug" -> List("sea_peoples"))), Right(Some(Slug("sea_peoples"))))
    assert(c.parse(List("slug" -> List("Sea Peoples"))).isLeft)
    assertEquals(summon[ValueConverter[Year]].parse(List("from" -> List("-3300"))), Right(Some(Year(-3300L))))

  test("rules across options are typed problems: check takes exactly one draft, rename checks the new slug"):
    assertEquals(
      problem("check", "sea_peoples"),
      Problem.Invalid.Inconsistent.here(
        NonEmptyVector.of(Problem.Field("--edits-file"), Problem.Field("--body-file")),
        Problem.Invalid.Exclusion.OneOf
      )
    )
    assert(scallop("rename", "a", "Not A Slug").isInstanceOf[CommandLine.Outcome.Failed])
    assertEquals(
      run("rename", "a", "b"),
      ArticleCommand.Rename(ReviewGoal.Slug("a"), LabelRename.To("b"), json)
    )

  test("--help is the help, not an error: of the group, and of each subcommand"):
    scallop("--help") match
      case CommandLine.Outcome.Help(text) =>
        List("list", "patch", "rename", "version").foreach(s => assert(text.mentions(s), text.printed))
      case other => fail(s"${other.toString}")
    scallop("patch", "--help") match
      case CommandLine.Outcome.Help(text) =>
        List("--filter-query", "--critique-reason", "--if-updated-at", "slug").foreach(s =>
          assert(text.mentions(s), text.printed)
        )
      case other => fail(s"${other.toString}")
    scallop("get", "sea_peoples", "--help") match
      case CommandLine.Outcome.Help(text) => assert(text.mentions("--parts"), text.printed)
      case other                          => fail(s"${other.toString}")

  test("a group with no subcommand is a missing command"):
    assertEquals(problem(), Problem.Invalid.Missing.here(Problem.Field("command")))

  test("put keeps the path as given"):
    assertEquals(run("put", "a.md"), ArticleCommand.Put(Path.of("a.md"), None, Cli.Strict(false)))
