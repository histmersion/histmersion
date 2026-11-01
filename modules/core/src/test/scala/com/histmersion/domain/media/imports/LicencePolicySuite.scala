package com.histmersion.domain.media.imports

import com.histmersion.domain.{Licence, LicenceFamily}

/** A collection's allowlist as a policy: the families it names, never the two that are never stored. */
class LicencePolicySuite extends munit.FunSuite:

  test("a settings value is the policy it names; all rights reserved and unknown never pass"):
    val p = LicencePolicy.of("cc0, CC-BY ,all-rights-reserved,unknown,bogus")
    assertEquals(p.allowed, Set(LicenceFamily.Cc0, LicenceFamily.CcBy))
    assert(p.allows(Licence.classify("cc-by-4.0")))
    assert(!p.allows(Licence.classify("cc-by-sa-4.0")))
    assert(!p.allows(Licence.classify("All rights reserved")))
    assert(!LicencePolicy(Set(LicenceFamily.Unknown)).allows(Licence.unknown("x")))
    assertEquals(LicencePolicy.of("").allowed, Set.empty[LicenceFamily])
