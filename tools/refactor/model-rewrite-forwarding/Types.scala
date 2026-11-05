//> using scala 3.10.0
package forwardingfixture
opaque type RowCount = Int
object RowCount:
  def apply(value: Int): RowCount = value
