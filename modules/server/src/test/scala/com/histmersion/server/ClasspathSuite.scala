package com.histmersion
package server

import scala.io.Source

/** JSON is jsoniter-scala, so circe (and, through it, cats) is on a shipped class path only where a library that still
  * speaks it brings it: Chimp (the MCP server) and sttp-ai (the agent loop) and its test kit. Any other route — a
  * module of ours, a new dependency — fails, naming the chain that brought it.
  */
class ClasspathSuite extends munit.FunSuite:
  import ClasspathSuite.*

  test("circe arrives only through Chimp and sttp-ai"):
    val bad = rows.groupBy(r => (r.project, r.conf)).toList.sortBy(_._1).flatMap { case ((project, conf), rs) =>
      val byName = rs.map(r => r.module -> r).toMap
      rs.filter(_.organization == "io.circe").flatMap { r =>
        unexplained(r.module, byName).map(chain => s"$project/$conf: ${chain.mkString(" <- ")}")
      }
    }
    assert(rows.nonEmpty, "dependency-callers.tsv is empty")
    assert(bad.isEmpty, bad.mkString("circe on a class path through something else than Chimp or sttp-ai:\n", "\n", ""))

  test("a module of ours bringing circe would be caught"):
    val rs = List(
      Row("x", "runtime", "io.circe", "circe-core_3", "0.14.17", List("com.histmersion:histmersion-api_3")),
      Row("x", "runtime", "com.histmersion", "histmersion-api_3", "0.1.0", Nil)
    )
    assertEquals(unexplained("io.circe:circe-core_3", rs.map(r => r.module -> r).toMap).map(_.mkString(" <- ")),
      List("io.circe:circe-core_3 <- com.histmersion:histmersion-api_3"))

object ClasspathSuite:
  final case class Row(project: String, conf: String, organization: String, name: String, revision: String, callers: List[String]):
    def module: String = s"$organization:$name"

  /** The libraries that may still bring circe: they speak it, and Histmersion meets them in one adapter each. */
  val Carriers: Set[String] = Set(
    "com.softwaremill.chimp:chimp-core_3",
    "com.softwaremill.chimp:chimp-server_3",
    "com.softwaremill.sttp.ai:core_3",
    "com.softwaremill.sttp.ai:agent-testkit_3"
  )

  lazy val rows: List[Row] =
    Source.fromResource("dependency-callers.tsv").getLines().toList.filter(_.nonEmpty).map { l =>
      val c = l.split("\t", -1)
      Row(c(0), c(1), c(2), c(3), c(4), c(5).split(",").toList.filter(_.nonEmpty))
    }

  /** The chains (module first) by which `module` arrives other than through a carrier: up its callers, past any other
    * library, to a module of ours or the project itself.
    */
  def unexplained(module: String, byName: Map[String, Row], seen: Set[String] = Set.empty): List[List[String]] =
    if Carriers(module) || seen(module) then Nil
    else
      byName.get(module) match
        case Some(r) if r.organization != "com.histmersion" && r.callers.nonEmpty =>
          r.callers.flatMap(c => unexplained(c, byName, seen + module).map(module :: _))
        case _ => List(List(module))
