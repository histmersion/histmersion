package com.histmersion.cli

import com.histmersion.api.catalogue.EventPatch
import com.monovore.decline.Command

/** `histmersion events patch <id> …` (#1440): the flags make the patch the server is sent — a scale, a place, a point
  * from --lat/--lon — and a patch that changes nothing, a scale that is no scale or a half point is refused before
  * anything is sent.
  */
class EventsCmdSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private val cmd = Command("patch", "test")(EventsCmd.patchOpts)
  private def parse(args: String*) = cmd.parse(args.toList, Map.empty)

  test("--scale and --place-id make the patch"):
    assertEquals(
      parse("battle_of_kadesh", "--scale", "local", "--place-id", "pleiades_678125"),
      Right(("battle_of_kadesh", Right(EventPatch(scale = Some("local"), placeId = Some("pleiades_678125")))))
    )
    assertEquals(parse("x", "--scale", "auto").map(_._2), Right(Right(EventPatch(scale = Some("auto")))))

  test("--lat/--lon is the event's own point, longitude first; a negative is written --lon=-1.5"):
    assertEquals(
      parse("x", "--lat", "36.5", "--lon=-1.5").map(_._2.map(_.geometry)),
      Right(Right(Some("""{"type":"Point","coordinates":[-1.5,36.5]}""")))
    )

  test("nothing to change, a scale that is none, or half a point is refused before any request"):
    def refused(args: String*) = parse(args*) match
      case Right((_, Left(e))) => e.message
      case other               => fail(s"not refused: $other")
    assert(refused("x").contains("nothing to change"))
    assert(refused("x", "--scale", "global").contains("local, regional or auto"))
    assert(refused("x", "--lat", "36.5").contains("lat and lon go together"))
    assert(refused("x", "--lat", "1", "--lon", "2", "--geometry", "{}").contains("not both"))

  test("the subcommand is registered under `events patch`"):
    val top = Command("histmersion", "test")(EventsCmd.command)
    assert(top.parse(List("events", "patch", "x", "--scale", "regional"), Map.empty).isRight)
