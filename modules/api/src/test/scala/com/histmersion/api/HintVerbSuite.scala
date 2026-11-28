package com.histmersion
package api

import com.histmersion.api.automation.managers.HintEffect

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*

import com.histmersion.api.automation.managers.HintEffect.{Action, Verb}

class HintVerbSuite extends munit.FunSuite:

  test("an action word names its verb, trimmed and lower-cased; any other word names none"):
    assertEquals(Action.verb(Action(" Hold ")), Some(Verb.Hold))
    assertEquals(Action.verb(Action("prioritise")), Some(Verb.Prioritise))
    assertEquals(Action.verb(Action("SKIP")), Some(Verb.Skip))
    assertEquals(Action.verb(Action("config")), Some(Verb.Config))
    assertEquals(Action.verb(Action("frobnicate")), None)
    assertEquals(Action.verb(Action("none")), None)

  test("blank and `none` ask for no action; a verb or another word does not"):
    assert(Action.none(Action("")))
    assert(Action.none(Action(" None ")))
    assert(!Action.none(Action("hold")))
    assert(!Action.none(Action("frobnicate")))

  test("a person's hint asks for prioritise, hold or skip; a model may also propose a settings change"):
    assertEquals(Verb.Actions.map(_.word), Vector(Action("prioritise"), Action("hold"), Action("skip")))
    assertEquals(Verb.Proposable, Verb.Actions :+ Verb.Config)

  test("each verb's wire spelling is its word, and reads back"):
    val wire = summon[WireEnumValues[Verb]]
    Verb.values.foreach(v => assertEquals(wire.fromWire(wire.wire(v)), Some(v)))
    assertEquals(wire.wire(Verb.Hold), WireEnumValues.Wire("hold"))
    assertEquals(wire.fromWire(WireEnumValues.Wire("delete")), None)

  test("an effect's action is written on the wire as the verb's word"):
    val e = HintEffect(Verb.Hold.word, List(HintEffect.Targets("tin")))
    assert(e.asJson.noSpaces.startsWith("""{"action":"hold","targets":["tin"]"""), e.asJson.noSpaces)
    assertEquals(RawJson.decode[HintEffect](e.asJson.noSpaces), Right(e))
