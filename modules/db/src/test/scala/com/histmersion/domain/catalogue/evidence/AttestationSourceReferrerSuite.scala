package com.histmersion
package domain.catalogue.evidence

import com.histmersion.domain.library.sources.referrers.SourceKeys
import com.histmersion.domain.library.Citekey

/** #969: which attestations of other sources lose their target when a source is deleted. */
class AttestationSourceReferrerSuite extends munit.FunSuite:

  private val Ck = Citekey("beckman2011_ahhiyawa")
  private val source = SourceKeys(Ck, List(Ck), _ => Map.empty)

  private def target(raw: String): BAttestationFullRow.TargetId = BAttestationFullRow.TargetId(raw)

  test("attestations of its findings, of edges FROM it and of its datings go; of edges TO it they stay"):
    assert(AttestationSourceReferrer.goes(BAttestationFullRow.TargetType("finding"), target(s"$Ck#3"), source))
    assert(!AttestationSourceReferrer.goes(BAttestationFullRow.TargetType("finding"), target(s"${Ck}x#3"), source))
    assert(
      AttestationSourceReferrer.goes(
        BAttestationFullRow.TargetType("edge"),
        target(s"$Ck|cline2014_1177|challenges"),
        source
      )
    )
    assert(
      !AttestationSourceReferrer
        .goes(BAttestationFullRow.TargetType("edge"), target(s"cline2014_1177|$Ck|challenges"), source),
      "an incoming edge is parked, not deleted"
    )
    assert(
      AttestationSourceReferrer.goes(
        BAttestationFullRow.TargetType("event_dating"),
        target(s"fall_of_troy|$Ck"),
        source
      )
    )
    assert(
      !AttestationSourceReferrer.goes(
        BAttestationFullRow.TargetType("event_dating"),
        target(s"fall_of_troy|${Ck}_v2"),
        source
      )
    )
    assert(!AttestationSourceReferrer.goes(BAttestationFullRow.TargetType("place"), target(Ck.value), source))
