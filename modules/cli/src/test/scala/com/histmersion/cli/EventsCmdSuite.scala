package com.histmersion
package cli

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.catalogue.EventPatch
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.catalogue.Place

/** `histmersion events patch <id> …` (#1440): the flags make the patch the server is sent — a scale, a place, a point
  * from --lat/--lon — and a patch that changes nothing, a scale that is no scale or a half point is refused before
  * anything is sent.
  */
class EventsCmdSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  /** The id and the patch `events patch` builds, or the problem it refuses the flags with. */
  private def parse(args: String*): Either[String, (String, EventPatch)] =
    Parsing.parse("events" +: "patch" +: args*) match
      case Right(EventsCmd.Command.Patch(id, p))        => Right((id, p))
      case Left(CommandLine.Outcome.Failed(problem, _)) => Left(problem.sentence)
      case other                                        => fail(s"${other.toString}")

  test("--scale and --place-id make the patch"):
    assertEquals(
      parse("battle_of_kadesh", "--scale", "local", "--place-id", "pleiades_678125"),
      Right(
        (
          "battle_of_kadesh",
          EventPatch(scale = Some(EventSummary.Scale("local")), placeId = Some(Place.Id("pleiades_678125")))
        )
      )
    )
    assertEquals(
      parse("x", "--scale", "auto").map(_._2),
      Right(EventPatch(scale = Some(EventSummary.Scale("auto"))))
    )

  test("--lat/--lon is the event's own point, longitude first; a negative is written --lon=-1.5"):
    assertEquals(
      parse("x", "--lat", "36.5", "--lon=-1.5").map(_._2.geometry),
      Right(Some(EventPatch.Geometry("""{"type":"Point","coordinates":[-1.5,36.5]}""")))
    )

  test("nothing to change, a scale that is none, or half a point is refused before any request"):
    def refused(args: String*) = parse(args*) match
      case Left(sentence) => sentence
      case other          => fail(s"not refused: ${other.toString}")
    assert(refused("x").contains("nothing to change"))
    assert(refused("x", "--scale", "global").contains("local, regional or auto"))
    assert(refused("x", "--lat", "36.5").contains("give lat, lon together, or none of them"))
    assert(
      refused("x", "--lat", "1", "--lon", "2", "--geometry", "{}").contains("give at most one of geometry, lat/lon")
    )

  test("the subcommand is registered under `events patch`"):
    assert(Parsing.parses("events", "patch", "x", "--scale", "regional"))
