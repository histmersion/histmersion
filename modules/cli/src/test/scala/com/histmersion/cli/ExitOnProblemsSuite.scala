package com.histmersion
package cli

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import util.problem.{Problem, Problems}
import util.problem.Problem.*

class ExitOnProblemsSuite extends munit.FunSuite:

  private val origins = ExitCode.Origin.values.toList
  private val documented = ExitCode.documented.map(_._1)

  test("the documented codes are distinct, 1 to 8, in order"):
    assertEquals(
      documented,
      List(ExitCode(1), ExitCode(2), ExitCode(3), ExitCode(4), ExitCode(5), ExitCode(6), ExitCode(7), ExitCode(8))
    )

  examples.foreach { p =>
    test(s"${p.group.productPrefix}.${p.productPrefix}: one documented exit code wherever it is met"):
      origins.foreach { o =>
        assertEquals(documented.count(_ == ExitCode.of(p, o)), 1, o.productPrefix)
        assertEquals(Cli.Error(p, o).exitCode, ExitCode.of(p, o))
      }
  }

  private val Local = ExitCode.Origin.Local
  private val Server = ExitCode.Origin.Server
  private def one[A <: Problem](using tt: scala.reflect.ClassTag[A]): A =
    examples.collectFirst { case a: A => a }.getOrElse(fail(s"no example of ${tt.runtimeClass.getSimpleName}"))

  test("the codes a caller branches on, by case and origin"):
    val cases: List[(Problem, ExitCode.Origin, ExitCode)] = List(
      (one[Invalid.Malformed], Local, ExitCode.Usage),
      (one[Invalid.Malformed], Server, ExitCode.Usage),
      (one[Unclassified], Local, ExitCode.Usage),
      (one[Unclassified], Server, ExitCode.Refused),
      (one[Refused.Policy], Server, ExitCode.Refused),
      (one[Upstream.Unreachable], Local, ExitCode.Unreachable),
      (one[Upstream.Unreachable], Server, ExitCode.Unavailable),
      (one[NotFound.Entity], Server, ExitCode.NotFound),
      (one[Conflict.Stale], Server, ExitCode.Conflict),
      (one[Conflict.AlreadyExists], Server, ExitCode.Conflict),
      (one[Conflict.Busy], Server, ExitCode.Unavailable),
      (one[Refused.Quota], Server, ExitCode.Unavailable),
      (one[Internal], Local, ExitCode.Fault),
      (one[Internal], Server, ExitCode.Fault),
      (one[Stored], Server, ExitCode.Fault),
      (one[Io.File], Local, ExitCode.Usage),
      (one[Io.File], Server, ExitCode.Fault),
      (one[Io.Process], Local, ExitCode.Refused),
      (one[Upstream.Unanswered], Local, ExitCode.OutcomeUnknown),
      (one[Upstream.Unanswered], Server, ExitCode.Unavailable),
      (one[Run.Failed], Server, ExitCode.Refused)
    )
    cases.foreach((p, o, code) =>
      assertEquals(ExitCode.of(p, o), code, s"${p.productPrefix} met at ${o.productPrefix}")
    )

  test("among several problems, an unknown outcome outweighs the server out of reach, which outweighs the first"):
    val unreachable = one[Upstream.Unreachable]
    val unanswered = one[Upstream.Unanswered]
    val missing = Invalid.Missing.here(Field("x"))
    assertEquals(ExitCode.of(Problems.of(missing, unreachable), Local), ExitCode.Unreachable)
    assertEquals(ExitCode.of(Problems.of(missing, unreachable, unanswered), Local), ExitCode.OutcomeUnknown)
    assertEquals(ExitCode.of(Problems.of(one[NotFound.Entity], missing), Server), ExitCode.NotFound)
    assertEquals(ExitCode.of(Problems.of(missing), Local), ExitCode.Usage)

  test("a send that never connected is 'unreachable'; a write cut off after it was sent is 'outcome unknown'"):
    val url = Client.ServerUrl("http://h:8000")
    val sent = Upstream.Url("http://h:8000/api/queue/tasks")
    val req = sttp.client4.basicRequest.get(sttp.model.Uri.unsafeParse("http://h:8000/api/queue/tasks"))
    val refused = new sttp.client4.SttpClientException.ConnectException(req, new java.net.ConnectException("refused"))
    val reset = new sttp.client4.SttpClientException.ReadException(req, new java.io.IOException("connection reset"))
    assertEquals(ExitCode.of(Client.transport(refused, url, sent, Client.Write(true)), Local), ExitCode.Unreachable)
    assertEquals(ExitCode.of(Client.transport(reset, url, sent, Client.Write(true)), Local), ExitCode.OutcomeUnknown)
    assertEquals(ExitCode.of(Client.transport(reset, url, sent, Client.Write(false)), Local), ExitCode.Unreachable)
    assert(
      Client.transport(refused, url, sent, Client.Write(false)).toString.contains("is the server running?"),
      "the hint stays"
    )

  private val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))

  private def scalaFiles(dir: Path): List[Path] =
    val walk = Files.walk(dir)
    try walk.iterator.asScala.filter(_.toString.endsWith(".scala")).toList.sorted
    finally walk.close()

  test("no site gives an exit code: every CLI failure's code is worked out from its problem"):
    val named = ("exit" + "Code\\s*=\\s").r
    val codes = "Usage|Refused|Unreachable|NotFound|Conflict|Unavailable|Fault|OutcomeUnknown"
    val positional = ("Error\\([^\\n]*,\\s*Exit" + s"Code\\.($codes)\\b").r
    val found =
      List("modules/cli/src/main/scala", "modules/cli/src/test/scala")
        .flatMap(d => scalaFiles(root.resolve(d)))
        .flatMap { f =>
          Files.readAllLines(f, UTF_8).asScala.zipWithIndex.collect {
            case (l, i) if named.findFirstIn(l).isDefined || positional.findFirstIn(l).isDefined => s"$f:${i + 1}: $l"
          }
        }
    assertEquals(found, Nil, found.mkString("\n"))

  test("the CLI reference lists every exit code, with its name, and no other"):
    val doc = Files.readString(root.resolve("docs/user-guide/reference/cli.md"), UTF_8)
    val listed =
      """(?m)^\| `(\d+)` \| ([a-z ]+) \|""".r.findAllMatchIn(doc).map(m => (m.group(1), m.group(2))).toList
    assertEquals(listed, ("0" -> "success") :: ExitCode.documented.map((c, n) => (s"$c", n)))
