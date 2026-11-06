package com.histmersion.domain.media.imports

import com.histmersion.domain.Licence

/** A collection's allowlist as a policy: the families it names, never the two that are never stored. */
class LicencePolicySuite extends munit.FunSuite:

  test("a settings value is the policy it names; all rights reserved and unknown never pass"):
    val p = LicencePolicy.of(com.histmersion.domain.Licence.AllowList("cc0, CC-BY ,all-rights-reserved,unknown,bogus"))
    assertEquals(p.allowed, Set(Licence.Family.Cc0, Licence.Family.CcBy))
    assert(p.allows(Licence.classify("cc-by-4.0")))
    assert(!p.allows(Licence.classify("cc-by-sa-4.0")))
    assert(!p.allows(Licence.classify("All rights reserved")))
    assert(!LicencePolicy(Set(Licence.Family.Unknown)).allows(Licence.unknown(com.histmersion.domain.Licence.Statement("x"))))
    assertEquals(LicencePolicy.of(com.histmersion.domain.Licence.AllowList("")).allowed, Set.empty[Licence.Family])
