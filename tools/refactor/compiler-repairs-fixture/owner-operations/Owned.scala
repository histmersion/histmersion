package compilerfixture

object Owned:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Flag = Flag.T
  object Flag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Alias = Text
  def raw(value: Text): String = value
  def alias(value: Alias): String = value
  def upper(value: Text): String = value.toUpperCase(java.util.Locale.ROOT)
  def increment(value: Count): Int = value + 1
  def sum(left: Count, right: Count): Int = left.value + right
  def generatedLeft(factory: () => Int, right: Count): Int = factory() + right
  def negative(value: Count): Int = -value
  def small(value: Count): Boolean = value < 4
  def wide(value: Count): Long = value.toLong
  def flag(value: Flag): Boolean = value
  def not(value: Flag): Boolean = !value
  def length(value: Bytes): Int = value.length
  def path(value: Text): java.nio.file.Path = java.nio.file.Path.of(value)
  final case class FlagRecord(flag: Flag)
  def flags(rows: List[FlagRecord]): List[FlagRecord] = rows.filterNot(_.flag)
  def brokenFlags(rows: List[FlagRecord]): List[FlagRecord] = rows.filterNot((_.flag).value)

final case class OwnedRecord(text: OwnedRecord.Text):
  def trimmed: String = text.trim
object OwnedRecord:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
