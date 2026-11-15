package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.api.library.{AuthorName, CandidateSweep, CollectionEntry, Title}

/** A map keyed by a newtype, with no codec or schema of its own: derived, written and read as an object keyed by the
  * base strings.
  */
class NewtypeKeyedMapsSuite extends munit.FunSuite:

  test("a collection entry's identifiers are an object keyed by scheme, and read back"):
    val entry = CollectionEntry(
      CollectionEntry.Id("7"),
      Title("Hattusa"),
      List(AuthorName("Bittel, Kurt")),
      None,
      None,
      Nil,
      Map(CollectionEntry.IdentifiersKey("isbn") -> CollectionEntry.IdentifiersValue("9780000000000")),
      Nil
    )
    val json = entry.asJson.noSpaces
    assert(json.contains(""""identifiers":{"isbn":"9780000000000"}"""), json)
    assertEquals(RawJson.decode[CollectionEntry](json).map(_.identifiers), Right(entry.identifiers))

  test("a sweep's skipped counts are an object keyed by reason, and read back"):
    val json =
      """{"goal":"g","watches":[],"found":1,"added":0,"again":0,"skipped":{"known":2},"unseen":0,"dryRun":false,"notes":[]}"""
    val sweep = RawJson.decode[CandidateSweep](json)
    assertEquals(
      sweep.map(_.skipped),
      Right(Map(CandidateSweep.SkippedKey("known") -> CandidateSweep.SkippedValue(2)))
    )
    assert(sweep.exists(_.asJson.noSpaces.contains(""""skipped":{"known":2}""")), s"$sweep")
