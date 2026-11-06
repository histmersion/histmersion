package compilerfixture

object Foreign:
  def construct(raw: String): Owned.Text = _root_.compilerfixture.Owned.Text(raw)
  def raw(value: Owned.Text): String = value
  def upper(value: Owned.Text): String = value.toUpperCase(java.util.Locale.ROOT)
  def increment(value: Owned.Count): Int = value + 1
  def sum(left: Int, right: Owned.Count): Int = left + right
  def path(value: Owned.Text): java.nio.file.Path = java.nio.file.Path.of(value)
  def same(value: Owned.Text): Boolean = "one" == value
  def output(stream: java.io.PrintStream, value: Owned.Text): Unit = stream.println(value)

object Outer:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object NestedConsumer:
    def raw(value: Outer.Text): String = value

object RootConsumer:
  object NestedModel:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  def raw(value: NestedModel.Text): String = value
