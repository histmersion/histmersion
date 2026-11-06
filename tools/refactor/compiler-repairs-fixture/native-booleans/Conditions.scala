package compilerfixture

object NativeFlags:
  type Flag = Flag.T
  object Flag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CustomFlag = CustomFlag.T
  object CustomFlag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    def apply(raw: Boolean): T = assume(!raw)
  type UnresolvedRule = _root_.io.github.iltotore.iron.constraint.any.Pure
  type AliasFlag = AliasFlag.T
  object AliasFlag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, UnresolvedRule]

object NativeConditions:
  def choose(flag: NativeFlags.Flag): Int = if flag then 1 else 0
  def negate(flag: NativeFlags.Flag): Boolean = !flag
  def and(flag: NativeFlags.Flag, rhs: () => Boolean): Boolean = flag && rhs()
  def or(flag: NativeFlags.Flag, rhs: () => Boolean): Boolean = flag || rhs()
  def produced(factory: () => NativeFlags.Flag): Boolean = if factory() then true else false
  def function(factory: () => (Int => NativeFlags.Flag)): Int => Boolean = factory()
  def partial(factory: () => PartialFunction[Int, NativeFlags.Flag]): PartialFunction[Int, Boolean] = factory()

  def check(): Unit =
    val on = NativeFlags.Flag(true)
    val off = NativeFlags.Flag(false)
    assert(choose(on) == 1 && choose(off) == 0)
    assert(!negate(on) && negate(off))
    var calls = 0
    def rhs(): Boolean = { calls += 1; true }
    assert(!and(off, () => rhs()) && calls == 0)
    assert(and(on, () => rhs()) && calls == 1)
    assert(or(on, () => rhs()) && calls == 1)
    assert(or(off, () => rhs()) && calls == 2)
    assert(produced(() => { calls += 1; on }) && calls == 3)
    var factories = 0
    val f = function(() => { factories += 1; i => NativeFlags.Flag(i > 0) })
    assert(factories == 1 && f(1) && !f(0) && factories == 1)
    var guards = 0
    var bodies = 0
    val p = partial(() => { factories += 1; { case i if { guards += 1; i > 0 } => bodies += 1; NativeFlags.Flag(i % 2 == 0) } })
    assert(factories == 2 && p.isDefinedAt(1) && !p.isDefinedAt(0) && bodies == 0)
    var defaults = 0
    val before = guards
    assert(p.applyOrElse(0, (_: Int) => { defaults += 1; true }))
    assert(defaults == 1 && bodies == 0 && guards == before + 1)
    assert(p.applyOrElse(2, (_: Int) => { defaults += 1; false }))
    assert(!p.applyOrElse(3, (_: Int) => { defaults += 1; true }))
    assert(defaults == 1 && bodies == 2 && factories == 2)
