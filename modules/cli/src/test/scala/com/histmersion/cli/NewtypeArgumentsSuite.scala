package com.histmersion.cli

import cats.data.Validated
import com.histmersion.cli.NewtypeArguments.given
import com.histmersion.values.{PluginId, Slug, Year}
import com.monovore.decline.Argument

class NewtypeArgumentsSuite extends munit.FunSuite:

  test("a newtype argument parses as its base type, constraint checked, with the base type's metavar"):
    assertEquals(summon[Argument[Slug]].read("sea_peoples"), Validated.validNel(Slug("sea_peoples")))
    assertEquals(summon[Argument[Year]].read("-3300"), Validated.validNel(Year(-3300)))
    assert(summon[Argument[Slug]].read("Sea Peoples").isInvalid && summon[Argument[Year]].read("x").isInvalid)
    assertEquals(summon[Argument[PluginId]].defaultMetavar, summon[Argument[String]].defaultMetavar)
