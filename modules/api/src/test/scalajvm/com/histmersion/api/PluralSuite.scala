package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** [[Plural]] (#1946): one rule for a noun that agrees with its count, and no count spelling it by hand. */
class PluralSuite extends munit.FunSuite:

  test("the noun agrees with its count; an irregular plural is given"):
    assertEquals(Plural.count(com.histmersion.api.Plural.CountN(1), com.histmersion.api.Plural.CountSingular("turn")), "1 turn")
    assertEquals(Plural.count(com.histmersion.api.Plural.CountN(0), com.histmersion.api.Plural.CountSingular("turn")), "0 turns")
    assertEquals(Plural.count(com.histmersion.api.Plural.CountN(3), com.histmersion.api.Plural.CountSingular("match"), com.histmersion.api.Plural.CountPlural("matches")), "3 matches")
    assertEquals(Plural.noun(com.histmersion.api.Plural.Count(2), com.histmersion.api.Plural.Noun.Singular("category"), com.histmersion.api.Plural.Noun.Plural("categories")), "categories")

  test("no count picks its noun's ending by hand"):
    val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", ".")).resolve("modules")
    val handRolled = List("""== 1 then "" else "s"""", """== 1 then "y" else "ies"""")
    val offenders = Files
      .walk(root)
      .iterator
      .asScala
      // the web module's three are the web stream's to move (#1946 left them to it)
      .filter(p =>
        p.toString.endsWith(".scala") && p.toString.contains("/src/main/") && !p.toString.contains("/modules/web/")
      )
      .flatMap(p =>
        Files.readAllLines(p).asScala.zipWithIndex.collect {
          case (l, i) if handRolled.exists(l.contains) => s"$p:${i + 1}: ${l.trim}"
        }
      )
      .toList
    assertEquals(offenders, Nil, "use Plural.noun / Plural.count")
