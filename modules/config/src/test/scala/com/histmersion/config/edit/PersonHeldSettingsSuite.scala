package com.histmersion
package config.edit

import com.histmersion.lint.{tolerated, LintRule}

/** The settings only a person changes: what an agent's tool call is refused. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PersonHeldSettingsSuite extends munit.FunSuite:

  private def key(raw: SettingKey.ParseRaw): SettingKey = SettingKey.parse(raw).toOption.get

  test("the switches that decide what agents may fetch are a person's, written either way"):
    assert(key(SettingKey.ParseRaw("acquire.allow-unauthorised")).personHeld)
    assert(key(SettingKey.ParseRaw("histmersion.acquire.allow-unauthorised")).personHeld)
    assert(key(SettingKey.ParseRaw("acquire.agents-may-use-unauthorised")).personHeld)

  test("the other settings, beside or above them, are not"):
    assert(!key(SettingKey.ParseRaw("acquire.chain")).personHeld)
    assert(!key(SettingKey.ParseRaw("acquire")).personHeld)
    assert(!key(SettingKey.ParseRaw("acquire.allow-unauthorised-x")).personHeld)
    assert(!key(SettingKey.ParseRaw("backups.keep")).personHeld)

  test("every person-held key is a setting of the reference schema"):
    SettingPolicy.PersonHeld.foreach(k => assert(ConfigSchema.reference.field(k).isDefined, s"$k is no setting"))
