package collisionfixture

object MissingOrigin:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Label: Label = _root_.collisionfixture.MissingOrigin.Label("label")

object Custom:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    def empty: T = apply("")
  val Label: Label = _root_.collisionfixture.Custom.Label("label")

object Checked:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.string.Match["[a-z]+"]]
  val Label: Label = _root_.collisionfixture.Checked.Label("label")

object Unrooted:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Label: Label = Label("label")

object SelfReference:
  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  lazy val Names: List[Names] = List(_root_.collisionfixture.SelfReference.Names("one")) ++ Names

object Untyped:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Label = _root_.collisionfixture.Untyped.Label("label")

final class InstanceOwner:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Label: Label = _root_.collisionfixture.InstanceOwner.Label("label")
