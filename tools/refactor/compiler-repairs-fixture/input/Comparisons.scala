package compilerfixture

object Comparisons extends munit.Assertions:
  def check(): Unit =
    assertEquals(Values.Text("one"), "one")
    assertEquals(List(Values.Text("one")), List("one"))
    assertEquals(Option(Values.Text("one")), Some("one"))
    assertEquals(Map("key" -> Values.Text("one")), Map("key" -> "one"))
    assertEquals((Values.Text("one"), Values.Count(7)), ("one", 7))
  def mismatch(): Unit = assertEquals(Values.Text("one"), "two")
  def leftError(value: Either[Values.Other, Int]): Unit = assertEquals(value, Left("bad"))
  def refinedTuple(value: Values.Text): Unit = assertEquals((value.value, Values.Count(7)), ("one", 7))
  def texts(values: Set[Values.Text]): Unit = assertEquals(values, Set("one", "two"))
  def empties(text: Values.Text): Unit = assertEquals((text, List.empty[Int], Option.empty[Int]), ("one", Nil, None))
  def equalField(record: Record): Boolean = record.text == "one"
  def unequalField(record: Record): Boolean = record.text != "one"
  def emptyMap(text: Values.Text): Unit = assertEquals((text, Map.empty[Values.Text, Values.Text]), ("one", Map.empty))
  def nonemptyMap(text: Values.Text): Unit = assertEquals((text, Map(Values.Text("key") -> Values.Text("one"))), ("one", Map.empty))
  def multiline(): Unit =
    assertEquals(
      Record(
        text = Values.Text("one"),
        count = Values.Count(7)
      ).text,
      "one"
    )
