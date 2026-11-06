package compilerfixture

object Comparisons extends munit.Assertions:
  def check(): Unit =
    assertEquals(((Values.Text("one")).value: String), "one")
    assertEquals(((List(Values.Text("one"))).map(refactorValue => (refactorValue).value): List[String]), List("one"))
    assertEquals(((Option(Values.Text("one"))).map(refactorValue2 => (refactorValue2).value): Option[String]), Some("one"))
    assertEquals(((Map("key" -> Values.Text("one"))).iterator.map { case (refactorKey, refactorItem) => (refactorKey, (refactorItem).value) }.toMap: Map[String, String]), Map("key" -> "one"))
    assertEquals(({ val refactorTuple = (Values.Text("one"), Values.Count(7)); ((refactorTuple._1).value, (refactorTuple._2).value) }: (String, Int)), ("one", 7))
  def mismatch(): Unit = assertEquals(((Values.Text("one")).value: String), "two")
  def leftError(value: Either[Values.Other, Int]): Unit = assertEquals(((value).left.map(refactorError => (refactorError).value): Either[String, Int]), Left("bad"))
  def refinedTuple(value: Values.Text): Unit = assertEquals(({ val refactorTuple2 = (value.value, Values.Count(7)); (refactorTuple2._1, (refactorTuple2._2).value) }: (String, Int)), ("one", 7))
  def texts(values: Set[Values.Text]): Unit = assertEquals(((values).map(refactorValue4 => (refactorValue4).value): Set[String]), Set("one", "two"))
  def empties(text: Values.Text): Unit = assertEquals(({ val refactorTuple3 = (text, List.empty[Int], Option.empty[Int]); ((refactorTuple3._1).value, refactorTuple3._2, refactorTuple3._3) }: (String, List[Int], Option[Int])), ("one", Nil, None))
  def equalField(record: Record): Boolean = record.text == _root_.compilerfixture.Values.Text("one")
  def unequalField(record: Record): Boolean = record.text != _root_.compilerfixture.Values.Text("one")
  def emptyMap(text: Values.Text): Unit = assertEquals(({ val refactorTuple4 = (text, Map.empty[Values.Text, Values.Text]); ((refactorTuple4._1).value, (refactorTuple4._2).iterator.map { case (refactorKey2, refactorItem2) => ((refactorKey2).value, (refactorItem2).value) }.toMap) }: (String, Map[String, String])), ("one", Map.empty))
  def nonemptyMap(text: Values.Text): Unit = assertEquals(({ val refactorTuple5 = (text, Map(Values.Text("key") -> Values.Text("one"))); ((refactorTuple5._1).value, (refactorTuple5._2).iterator.map { case (refactorKey3, refactorItem3) => ((refactorKey3).value, (refactorItem3).value) }.toMap) }: (String, Map[String, String])), ("one", Map.empty))
  def multiline(): Unit =
    assertEquals(
      ((Record(
        text = Values.Text("one"),
        count = Values.Count(7)
      ).text).value: String),
      "one"
    )
