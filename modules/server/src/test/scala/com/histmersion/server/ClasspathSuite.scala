package com.histmersion
package server

import scala.io.Source

/** JSON is jsoniter-scala, so circe and Typelevel's libraries (cats, jawn) are on a shipped class path only where a
  * library that still speaks them brings them: Chimp (the MCP server) and sttp-ai (the agent loop) and its test kit —
  * and, until the CLI's own step of G62 replaces it, decline (the CLI's argument parser, on cats). Any other route — a
  * module of ours, a new dependency — fails, naming the chain that brought it.
  */
class ClasspathSuite extends munit.FunSuite:
  import ClasspathSuite.*

  test("circe and cats arrive only through Chimp, sttp-ai and (for now) decline"):
    val bad = rows.groupBy(r => (r.project, r.conf)).toList.sortBy(_._1).flatMap { case ((project, conf), rs) =>
      val byName = rs.map(r => r.module -> r).toMap
      rs.filter(r => Carried(r.organization)).flatMap { r =>
        unexplained(r.module, byName).map(chain => s"$project/$conf: ${chain.mkString(" <- ")}")
      }
    }
    assert(rows.nonEmpty, "dependency-callers.tsv is empty")
    assert(
      bad.isEmpty,
      bad.mkString("circe or cats on a class path through something else than a carrier:\n", "\n", "")
    )

  test("a module of ours bringing circe would be caught"):
    val rs = List(
      Row("x", "runtime", "io.circe", "circe-core_3", "0.14.17", List("com.histmersion:histmersion-api_3")),
      Row("x", "runtime", "com.histmersion", "histmersion-api_3", "0.1.0", Nil)
    )
    assertEquals(
      unexplained("io.circe:circe-core_3", rs.map(r => r.module -> r).toMap).map(_.mkString(" <- ")),
      List("io.circe:circe-core_3 <- com.histmersion:histmersion-api_3")
    )

object ClasspathSuite:
  final case class Row(
      project: String,
      conf: String,
      organization: String,
      name: String,
      revision: String,
      callers: List[String]
  ):
    def module: String = s"$organization:$name"

  /** The organisations whose libraries arrive only through a carrier. */
  val Carried: Set[String] = Set("io.circe", "org.typelevel")

  /** The libraries that may still bring them: Chimp and sttp-ai speak circe, and Histmersion meets each in one adapter;
    * decline is the CLI's parser until the CLI's step of G62 replaces it (then it leaves this list).
    */
  val Carriers: Set[String] = Set(
    "com.softwaremill.chimp:chimp-core_3",
    "com.softwaremill.chimp:chimp-server_3",
    "com.softwaremill.sttp.ai:core_3",
    "com.softwaremill.sttp.ai:agent-testkit_3",
    "com.monovore:decline_3"
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
