package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import com.histmersion.domain.research.Slug
import com.histmersion.Year
import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.ConfigValueFactory
import com.histmersion.util.plugins.Plugin

class NewtypeReadersSuite extends munit.FunSuite:

  private def read[T: ConfigReader](raw: AnyRef) = summon[ConfigReader[T]].from(ConfigValueFactory.fromAnyRef(raw))

  test("a newtype setting reads as its base type, constraint checked"):
    assertEquals(read[Slug]("sea_peoples"), Right(Slug("sea_peoples")))
    assertEquals(read[Plugin.Id]("oa-core"), Right(Plugin.Id("oa-core")))
    assert(read[Slug]("Sea Peoples").isLeft)

  test("a number from an environment variable arrives as a string and still reads"):
    assertEquals(read[Year]("-3300"), Right(Year(-3300)))
    assertEquals(read[Year](Integer.valueOf(-3300)), Right(Year(-3300)))
