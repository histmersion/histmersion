package com.histmersion.domain.automation.agents

import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite}

/** The contract the agents' scratch state keeps: set, replace and read back by key (the value as `jsonb` prints it),
  * the listing by key, a delete, a value that is not JSON refused.
  */
abstract class AgentStateServiceContract extends EngineStoreSuite:
  private def a(s: EngineStores) = s.agentState

  stest("a value is set, replaced, listed by key and deleted; it reads back as jsonb prints it") { s =>
    a(s).set("zz-b", """{"zeta":1,"a":[1,2,{"bb":true,"c":null}]}""")
    a(s).set("zz-a", "\"one\"")
    a(s).set("zz-a", "[1,  2]")
    assertEquals(a(s).get("zz-b").map(_.value), Some("""{"a": [1, 2, {"c": null, "bb": true}], "zeta": 1}"""))
    assertEquals(a(s).list().map(e => (e.key, e.value)), List(("zz-a", "[1, 2]"), ("zz-b", a(s).get("zz-b").get.value)))
    assert(a(s).get("zz-a").flatMap(_.updatedAt).isDefined)
    assert(a(s).delete("zz-a"))
    assert(!a(s).delete("zz-a"))
    assertEquals(a(s).get("zz-a"), None)
    assertEquals(a(s).get("zz-none"), None)
  }

  stest("a value that is not JSON fails and leaves nothing") { s =>
    assert(scala.util.Try(a(s).set("zz-bad", "not json")).isFailure)
    assertEquals(a(s).get("zz-bad"), None)
  }
