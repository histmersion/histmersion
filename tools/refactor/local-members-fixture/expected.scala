package localfixture

object Service:
  type FirstLabel = FirstLabel.T
  object FirstLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FirstLoopResult = FirstLoopResult.T
  object FirstLoopResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FirstLoopN = FirstLoopN.T
  object FirstLoopN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SecondLabel = SecondLabel.T
  object SecondLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThirdLabel = ThirdLabel.T
  object ThirdLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def first(): Unit =
    val label: localfixture.Service.FirstLabel = ???
    def loop(n: localfixture.Service.FirstLoopN): localfixture.Service.FirstLoopResult = ???
    ()
  def second(): Unit =
    val label: localfixture.Service.SecondLabel = ???
    ()
  def third(): Unit =
    if ??? then
      val label: localfixture.Service.ThirdLabel = ???
      ()
    else
      val label: String = ???
      ()
