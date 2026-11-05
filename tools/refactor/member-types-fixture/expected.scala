package memberfixture

class Token
final case class Policy(resolve: (Policy.ResolveArg1, Token) => Policy.ResolveResult):
  def label: memberfixture.Policy.LabelResult = ???

object Policy:
  type ResolveArg1 = ResolveArg1.T
  object ResolveArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResult = ResolveResult.T
  object ResolveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelResult = LabelResult.T
  object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

object Service:
  type Keys = Keys.T
  object Keys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SecondsResult = SecondsResult.T
  object SecondsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChooseResult = ChooseResult.T
  object ChooseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChooseQuery = ChooseQuery.T
  object ChooseQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val keys: Set[memberfixture.Service.Keys] = ???
  def seconds(s: Token): memberfixture.Service.SecondsResult = ???
  def choose(query: memberfixture.Service.ChooseQuery): Option[memberfixture.Service.ChooseResult] = ???

class Store:
  def load(key: memberfixture.Store.LoadKey): Option[memberfixture.Store.LoadResult] = ???
  override def toString: String = "store"
  def buffer(bytes: Array[Byte]): Array[Byte] = ???

object Store:
  type LoadResult = LoadResult.T
  object LoadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadKey = LoadKey.T
  object LoadKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
