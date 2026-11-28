package com.histmersion
package domain.library.workflows

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.{CandidateSweep, Source}
import com.histmersion.domain.CheckpointGolden
import com.histmersion.domain.CheckpointGolden.Golden
import com.histmersion.domain.library.{Citekey, Doi}
import com.histmersion.domain.library.search.Search
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.Problem
import AcquisitionSweepPipeline.*

class AcquisitionSweepGoldenSuite extends munit.FunSuite:

  private val candidates = CheckpointGolden.candidates.values

  private val picked = candidates.map(c => Picked(c, AcquisitionSweepRules.Rationale("It dates the tin trade.")))

  private val ingested = List(Ingested(Doi("10.1/x"), Citekey("smith1987_tin")))

  private val all: List[Golden[?]] = List(
    Golden(
      "acquisition-found.ndjson",
      List(
        Found(
          Goal.Title("Bronze Age tin"),
          Some(Goal.Notes("Sources of Aegean tin.")),
          List(Search.Query("bronze age tin"), Search.Query("tin ingots")),
          candidates,
          Map(CandidateSweep.SkippedKey("noDoi") -> AcquisitionSweepRules.SkipCount(2)),
          Map(Doi("10.1/x") -> Source.Candidate.Id(7L))
        ),
        Found(Goal.Title(""), None, Nil, Nil, Map.empty)
      )
    ),
    Golden("acquisition-picked.ndjson", List(picked, Nil)),
    Golden("acquisition-ingested.ndjson", List(ingested, Nil)),
    Golden(
      "acquisition-tried.ndjson",
      List(
        Tried(ingested, List(Failed(Doi("10.2/y"), Problem.Config.ProxyUndeclared(Problem.Raw("no legal copy")), noCopy = true))),
        Tried(
          Nil,
          List(Failed(Doi("10.3/z"), Problem.Config.WildcardOverlap(Problem.Raw("a"), Problem.Raw("b")), noCopy = false))
        ),
        Tried()
      )
    ),
    Golden("acquisition-parked.ndjson", List(Parked(Parked.Waiting(2), Parked.Known(1)), Parked(Parked.Waiting(0), Parked.Known(0))))
  )

  private val update = sys.env.get("HISTMERSION_UPDATE_GOLDEN").contains("1")

  private def file(name: String) =
    Iterator
      .iterate(Paths.get(sys.props("user.dir")).toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .map(_.resolve("modules/server/src/test/resources"))
      .find(Files.isDirectory(_))
      .get
      .resolve(s"golden/checkpoints/$name")

  private def encoded[A](g: Golden[A]): List[String] = g.values.map(_.asJson(using g.encoder).noSpaces)

  private def check[A](g: Golden[A]): Unit =
    val path = file(g.file)
    if update then
      Files.createDirectories(path.getParent)
      Files.write(path, encoded(g).mkString("", "\n", "\n").getBytes(UTF_8))
    val stored = Files.readAllLines(path, UTF_8).toArray(Array.empty[String]).toList.filter(_.nonEmpty)
    assert(stored.nonEmpty)
    stored.foreach { line =>
      val again = decode(line)(using g.decoder).fold(e => fail(s"${g.file}: $e\n$line"), _.asJson(using g.encoder).noSpaces)
      assertEquals(again, line)
    }
    assertEquals(encoded(g), stored)

  all.foreach(g => test(s"${g.file}: stored lines decode and re-encode byte for byte, the samples encode to them")(check(g)))
