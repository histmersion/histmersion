package helperfixture

object Excluded:
  type Input = Input.T
  object Input extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unknown = Unknown.T
  object Unknown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Custom = Custom.T
  object Custom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    def normalize(value: T): String = value.value.trim
  type Checked = Checked.T
  object Checked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.string.Match["[a-z]+"]]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  final case class Row(title: Title, slug: Slug)
  def take(value: Input): Input = value
  def custom(value: Custom): Custom = value
  def count(value: Count): Count = value
  def unknown(value: Unknown): Input = take(value)
  def properties(value: Title): Slug = value
  def foreign(value: Helpers.Input): Input = take(value)
  def behavior(value: Input): Custom = custom(value)
  def constrained(value: Input): Checked = value
  def widening(value: Input): Count = count(value)
