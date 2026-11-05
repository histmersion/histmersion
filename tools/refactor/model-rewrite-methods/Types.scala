//> using scala 3.10.0
package methodfixture
opaque type RowCount = Int
object RowCount:
  def apply(n: Int): RowCount = n
  extension (n: RowCount) def value: Int = n
opaque type WireCount = Int
object WireCount:
  def apply(n: Int): WireCount = n
  extension (n: WireCount) def value: Int = n
