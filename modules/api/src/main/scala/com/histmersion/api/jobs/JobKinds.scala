package com.histmersion.api.jobs

/** Every kind of background job the server runs, as their owners registered them, in the order the jobs window lists
  * them, and the fields their jobs' detail may hold, in the order the detail shows them. A kind nobody registered is
  * shown by its identifier.
  */
final class JobKinds(val entries: List[JobKindEntry], val details: List[JobDetail.Field[?]] = Nil):
  private val byWire: Map[com.histmersion.api.jobs.JobKinds.ByWirePart1, JobKindEntry] = entries.map(e => e.kind.value -> e).toMap

  def kinds: List[JobInfo.Kind] = entries.map(_.kind)

  def get(kind: JobInfo.Kind): Option[JobKindEntry] = byWire.get(kind.value)

  /** The kind a stored or typed identifier names (case and surrounding space ignored). */
  def parse(wire: com.histmersion.api.jobs.JobKinds.ParseWire): Option[JobKindEntry] = byWire.get(_root_.com.histmersion.api.jobs.JobKinds.ByWirePart1((wire).value.trim.toLowerCase))

  /** May a person pause and cancel a job of the kind `wire` names? */
  def stoppable(wire: com.histmersion.api.jobs.JobKinds.StoppableWire): com.histmersion.api.jobs.JobKinds.StoppableResult = _root_.com.histmersion.api.jobs.JobKinds.StoppableResult(byWire.get(_root_.com.histmersion.api.jobs.JobKinds.ByWirePart1((wire).value)).exists((refactorCallback2 => ((refactorCallback2.stoppable) == _root_.com.histmersion.api.jobs.JobKindEntry.Stoppable(true)))))

  /** Do the pause, resume and cancel of a job of the kind `wire` names go to the kind's own runner? */
  def ownControl(wire: com.histmersion.api.jobs.JobKinds.OwnControlWire): com.histmersion.api.jobs.JobKinds.OwnControlResult = _root_.com.histmersion.api.jobs.JobKinds.OwnControlResult(byWire.get(_root_.com.histmersion.api.jobs.JobKinds.ByWirePart1((wire).value)).exists((refactorCallback2 => ((refactorCallback2.ownControl) == _root_.com.histmersion.api.jobs.JobKindEntry.OwnControl(true)))))

  /** The identifiers of the kinds a person may pause and cancel. */
  def stoppableKinds: Set[com.histmersion.api.jobs.JobKinds.StoppableKindsResult] = entries.filter((refactorCallback => ((refactorCallback.stoppable) == _root_.com.histmersion.api.jobs.JobKindEntry.Stoppable(true)))).map(_.kind.value).toSet

  /** What a job is about: its source, else what its detail says. */
  def subject(job: JobInfo): Option[com.histmersion.api.jobs.JobKinds.SubjectResult] =
    job.citekey.map(_.value.toString).orElse(job.detail.flatMap(d => entries.iterator.flatMap(_.subject(d)).nextOption()))

object JobKinds:
  type ByWirePart1 = ByWirePart1.T
  object ByWirePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseWire = ParseWire.T
  object ParseWire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoppableResult = StoppableResult.T
  object StoppableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoppableWire = StoppableWire.T
  object StoppableWire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OwnControlResult = OwnControlResult.T
  object OwnControlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OwnControlWire = OwnControlWire.T
  object OwnControlWire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoppableKindsResult = StoppableKindsResult.T
  object StoppableKindsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SubjectResult = SubjectResult.T
  object SubjectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
