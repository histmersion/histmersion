package histdb.web

class ChipInputSuite extends munit.FunSuite:

  test("a topic hint shows spaces, not underscores") {
    assertEquals(ChipInput.hintLabel("bronze_age_collapse"), "bronze age collapse")
  }

  test("a hint is not HTML-escaped (it is a text node; Laminar escapes it)") {
    assertEquals(ChipInput.hintLabel("Bell & Beaker"), "Bell & Beaker")
  }
