package compilerfixture

object ComparisonConstruction:
  def text(raw: String, value: Values.Text): Boolean = raw == value
  def reversed(value: Values.Text, raw: String): Boolean = value != raw
  def field(record: Record, raw: String): Boolean = record.text == raw
  def alias(value: Owned.Alias, raw: String): Boolean = value == raw
  def count(value: Values.Count, raw: Int): Boolean = value == raw
  def flag(value: Values.Flag): Boolean = value == true
  def generated(value: Values.Count, raw: () => Int): Boolean = value == raw()
  def composite(value: Values.Text, raw: String): Boolean = value == (raw.trim + "!")
  def nested(value: Values.Text, raw: String): Boolean = (value == raw) == (value != "other")

  def check(): Unit =
    assert(text("one", Values.Text("one")))
    assert(!text("other", Values.Text("one")))
    assert(!reversed(Values.Text("one"), "one"))
    assert(reversed(Values.Text("one"), "other"))
    assert(field(new Record(Values.Text("one"), Values.Count(1)), "one"))
    assert(alias(Owned.Text("one"), "one"))
    assert(count(Values.Count(Int.MinValue), Int.MinValue))
    assert(flag(Values.Flag(true)) && !flag(Values.Flag(false)))
    var calls = 0
    assert(generated(Values.Count(7), () => { calls += 1; 7 }))
    assert(calls == 1)
    assert(composite(Values.Text("one!"), " one "))
    assert(nested(Values.Text("one"), "one"))
