package migrationfixture

opaque type Offset = Int
object Offset:
  def apply(value: Int): Offset = value
opaque type RowCount = Int
object RowCount:
  def apply(value: Int): RowCount = value
