package com.histmersion.config.edit

class SettingValueSuite extends munit.FunSuite:

  test("text is taken as typed; numbers, flags and lists are read"):
    assertEquals(
      SettingValue.parse("a b # not a comment", SettingKind.Text),
      Right(SettingValue.Text("a b # not a comment"))
    )
    assertEquals(SettingValue.parse(" 12 ", SettingKind.Number), Right(SettingValue.Number(BigDecimal(12))))
    assertEquals(SettingValue.parse("-3.5", SettingKind.Number), Right(SettingValue.Number(BigDecimal("-3.5"))))
    assert(SettingValue.parse("twelve", SettingKind.Number).isLeft)
    assertEquals(SettingValue.parse("yes", SettingKind.Flag), Right(SettingValue.Flag(true)))
    assertEquals(SettingValue.parse("Off", SettingKind.Flag), Right(SettingValue.Flag(false)))
    assert(SettingValue.parse("maybe", SettingKind.Flag).isLeft)
    assertEquals(
      SettingValue.parse("[openalex, \"cross ref\", 3]", SettingKind.Items),
      Right(
        SettingValue.Items(
          List(SettingValue.Text("openalex"), SettingValue.Text("cross ref"), SettingValue.Number(BigDecimal(3)))
        )
      )
    )
    assertEquals(
      SettingValue.parse("a, b ,c", SettingKind.Items),
      Right(SettingValue.Items(List("a", "b", "c").map(SettingValue.Text(_))))
    )
    assertEquals(SettingValue.parse("", SettingKind.Items), Right(SettingValue.Items(Nil)))
    assert(SettingValue.parse("[a, {b = 1}]", SettingKind.Items).isLeft, "an object in a list is no plain value")
    assert(SettingValue.parse("x", SettingKind.Block).isLeft)

  test("an open block's key guesses the kind"):
    assertEquals(SettingValue.parse("true", SettingKind.Any), Right(SettingValue.Flag(true)))
    assertEquals(SettingValue.parse("7", SettingKind.Any), Right(SettingValue.Number(BigDecimal(7))))
    assertEquals(SettingValue.parse("env:X", SettingKind.Any), Right(SettingValue.Text("env:X")))

  test("rendered as HOCON reads back the same"):
    val vs = List(
      SettingValue.Text("quote \" and \\ and ${HOME}"),
      SettingValue.Number(BigDecimal(5)),
      SettingValue.Number(BigDecimal("2.5")),
      SettingValue.Flag(false),
      SettingValue.Items(List(SettingValue.Text("x"), SettingValue.Number(BigDecimal(1))))
    )
    vs.foreach { v =>
      val back = SettingValue.fromConfig(org.ekrich.config.ConfigFactory.parseString(s"v = ${v.hocon}").getValue("v"))
      assert(back.exists(SettingValue.same(_, v)), s"$v → ${v.hocon} → $back")
    }

  test("5 and 5.0 are the same number; \"5\" is not 5"):
    assert(SettingValue.same(SettingValue.Number(BigDecimal(5)), SettingValue.Number(BigDecimal("5.0"))))
    assert(!SettingValue.same(SettingValue.Text("5"), SettingValue.Number(BigDecimal(5))))
