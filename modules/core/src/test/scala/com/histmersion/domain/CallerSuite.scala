package com.histmersion
package domain

import com.histmersion.UserId
import com.histmersion.domain.automation.Run

class CallerSuite extends munit.FunSuite:

  test("the local caller is the one local user with every permission"):
    assertEquals(Caller.Local, Caller.Person(UserId("local"), Caller.Role.Admin))
    assert(Caller.Role.values.forall(Caller.Local.role.includes))

  test("each role includes the ones before it and none after"):
    assert(
      Caller.Role.Researcher.includes(Caller.Role.Contributor) && Caller.Role.Researcher.includes(Caller.Role.Reader)
    )
    assert(!Caller.Role.Contributor.includes(Caller.Role.Researcher) && Caller.Role.Reader.includes(Caller.Role.Reader))

  test("an automated caller names its run"):
    val run = Run.Id("0b3f6a4e-2c1d-4e5f-8a9b-0c1d2e3f4a5b")
    assertEquals(Caller.Automation(run).run, run)

  test("a person asks for themselves; an automated run for nobody"):
    assertEquals(Caller.Local.person, Some(UserId("local")))
    assertEquals(Caller.Automation(Run.Id("0b3f6a4e-2c1d-4e5f-8a9b-0c1d2e3f4a5b")).person, None)
