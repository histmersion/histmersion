package lensfixture

object Vocabulary:
  enum Kind:
    case First, Second
object Exported:
  export Vocabulary.Kind
  final case class Record(kind: Kind, count: Int)
final case class Anchor(count: Int)
object AliasedExamples:
  def deferred(r: Exported.Record): Exported.Record = r.copy(count = 2)
  def ordinary(r: Anchor): Anchor = r.copy(count = 2)
