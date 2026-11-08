package com.histmersion
package config.edit

class SettingValueSuite extends munit.FunSuite:

  test("text is taken as typed; numbers, flags and lists are read"):
    assertEquals(
      SettingValue.parse(SettingValue.Typed("a b # not a comment"), SettingKind.Text),
      Right(SettingValue.Text(SettingValue.Text.Value("a b # not a comment")))
    )
    assertEquals(
      SettingValue.parse(SettingValue.Typed(" 12 "), SettingKind.Number),
      Right(SettingValue.Number(BigDecimal(12)))
    )
    assertEquals(
      SettingValue.parse(SettingValue.Typed("-3.5"), SettingKind.Number),
      Right(SettingValue.Number(BigDecimal("-3.5")))
    )
    assert(SettingValue.parse(SettingValue.Typed("twelve"), SettingKind.Number).isLeft)
    assertEquals(
      SettingValue.parse(SettingValue.Typed("yes"), SettingKind.Flag),
      Right(SettingValue.Flag(SettingValue.Flag.Value(true)))
    )
    assertEquals(
      SettingValue.parse(SettingValue.Typed("Off"), SettingKind.Flag),
      Right(SettingValue.Flag(SettingValue.Flag.Value(false)))
    )
    assert(SettingValue.parse(SettingValue.Typed("maybe"), SettingKind.Flag).isLeft)
    assertEquals(
      SettingValue.parse(SettingValue.Typed("[openalex, \"cross ref\", 3]"), SettingKind.Items),
      Right(
        SettingValue.Items(
          List(
            SettingValue.Text(SettingValue.Text.Value("openalex")),
            SettingValue.Text(SettingValue.Text.Value("cross ref")),
            SettingValue.Number(BigDecimal(3))
          )
        )
      )
    )
    assertEquals(
      SettingValue.parse(SettingValue.Typed("a, b ,c"), SettingKind.Items),
      Right(SettingValue.Items(List("a", "b", "c").map(t => SettingValue.Text(SettingValue.Text.Value(t)))))
    )
    assertEquals(SettingValue.parse(SettingValue.Typed(""), SettingKind.Items), Right(SettingValue.Items(Nil)))
    assert(
      SettingValue.parse(SettingValue.Typed("[a, {b = 1}]"), SettingKind.Items).isLeft,
      "an object in a list is no plain value"
    )
    assert(SettingValue.parse(SettingValue.Typed("x"), SettingKind.Block).isLeft)

  test("an open block's key guesses the kind"):
    assertEquals(
      SettingValue.parse(SettingValue.Typed("true"), SettingKind.Any),
      Right(SettingValue.Flag(SettingValue.Flag.Value(true)))
    )
    assertEquals(
      SettingValue.parse(SettingValue.Typed("7"), SettingKind.Any),
      Right(SettingValue.Number(BigDecimal(7)))
    )
    assertEquals(
      SettingValue.parse(SettingValue.Typed("env:X"), SettingKind.Any),
      Right(SettingValue.Text(SettingValue.Text.Value("env:X")))
    )

  test("rendered as HOCON reads back the same"):
    val vs = List(
      SettingValue.Text(SettingValue.Text.Value("quote \" and \\ and ${HOME}")),
      SettingValue.Number(BigDecimal(5)),
      SettingValue.Number(BigDecimal("2.5")),
      SettingValue.Flag(SettingValue.Flag.Value(false)),
      SettingValue.Items(List(SettingValue.Text(SettingValue.Text.Value("x")), SettingValue.Number(BigDecimal(1))))
    )
    vs.foreach { v =>
      val back = SettingValue.fromConfig(org.ekrich.config.ConfigFactory.parseString(s"v = ${v.hocon}").getValue("v"))
      assert(back.exists(SettingValue.same(_, v)), s"$v → ${v.hocon} → $back")
    }

  test("5 and 5.0 are the same number; \"5\" is not 5"):
    assert(SettingValue.same(SettingValue.Number(BigDecimal(5)), SettingValue.Number(BigDecimal("5.0"))))
    assert(!SettingValue.same(SettingValue.Text(SettingValue.Text.Value("5")), SettingValue.Number(BigDecimal(5))))
