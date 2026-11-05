package com.histmersion.server.hot

import hearth.kindlings.optics.syntax.*
import ox.{supervised, Ox}

import scala.concurrent.duration.*
import com.histmersion.config.{HotNode, HotPlan}
import com.histmersion.util.jobs.{Carried, Pinning}
import java.io.PrintWriter
import java.sql.Connection
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}
import java.util.logging.Logger
import javax.sql.DataSource
import scala.util.control.NonFatal

/** A stable handle on a component a config reload may rebuild (docs/research-plugins.md §3.6). Its holder never
  * reloads: [[use]] gives the value of the generation the calling thread is pinned to — or pins the current generation
  * for the duration of the call — so a value is never closed under the code using it.
  */
trait Hot[+A]:
  def name: com.histmersion.server.hot.Hot.NameResult

  /** The value for this call, pinned while `f` runs. */
  def use[B](f: A => B): B

  /** The value the calling thread sees now (its pin's, else the current one), NOT pinned: for reading something that is
    * not closed with its generation (a report, a registry snapshot).
    */
  def peek: A

  /** The latest published value, whatever the calling thread is pinned to; not pinned. */
  def latest: A

object Hot:

  /** A `DataSource` whose every `getConnection` comes from the pool a [[Hot]] handle holds NOW — the pool of the
    * generation the calling thread is pinned to, else the current one. So the 74 places that take a `DataSource` keep
    * taking one, and a swapped database needs none of them to reload:
    *
    *   - a connection taken before a swap stays on its pool until it is closed, and the old pool is closed only when
    *     its last connection came back (the pool node's `idle` check);
    *   - work pinned to a generation (an HTTP request, a job, an agent run) gets every connection from that
    *     generation's pool, so it finishes on the database it started on;
    *   - unpinned work (a manager's tick, the lease renewal) moves to the new pool at its next step.
    *
    * Taking the connection is itself pinned, so a pool cannot be closed between choosing it and borrowing from it.
    * Considered and not taken: `Hot[DataSource]` in every repo signature (74 files, and every call site wrapped in
    * `use` for no gain — the pin is already per request or per job), and a delegating `Connection` (more surface, and
    * the pool's own active count already says when a retired pool is free).
    */
  type NameResult = NameResult.T
  object NameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final class DataSource[A](pool: Hot[A])(of: A => javax.sql.DataSource) extends javax.sql.DataSource:
    private def now: javax.sql.DataSource = of(pool.peek)
    def getConnection(): Connection = pool.use(a => of(a).getConnection())
    def getConnection(username: com.histmersion.server.hot.Hot.DataSource.GetConnectionUsername, password: com.histmersion.server.hot.Hot.DataSource.GetConnectionPassword): Connection =
      pool.use(a => of(a).getConnection(username, password))

    def getLogWriter: PrintWriter = now.getLogWriter
    def setLogWriter(out: PrintWriter): Unit = now.setLogWriter(out)
    def setLoginTimeout(seconds: com.histmersion.server.hot.Hot.DataSource.SetLoginTimeoutSeconds): Unit = now.setLoginTimeout(seconds)
    def getLoginTimeout: com.histmersion.server.hot.Hot.DataSource.GetLoginTimeoutResult = now.getLoginTimeout
    def getParentLogger: Logger = now.getParentLogger
    def unwrap[T](iface: Class[T]): T =
      if iface.isInstance(this) then iface.cast(this) else now.unwrap(iface)
    def isWrapperFor(iface: Class[?]): com.histmersion.server.hot.Hot.DataSource.IsWrapperForResult = iface.isInstance(this) || now.isWrapperFor(iface)

    override def toString = s"HotDataSource(${pool.name})"

  object DataSource:
    type GetConnectionUsername = GetConnectionUsername.T
    object GetConnectionUsername extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetConnectionPassword = GetConnectionPassword.T
    object GetConnectionPassword extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetLoginTimeoutSeconds = SetLoginTimeoutSeconds.T
    object SetLoginTimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetLoginTimeoutResult = GetLoginTimeoutResult.T
    object GetLoginTimeoutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsWrapperForResult = IsWrapperForResult.T
    object IsWrapperForResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The hot component graph (docs/research-plugins.md §3.6): Ox has no `Hotswap`, so this is one.
    *
    *   - A NODE is built from the config and the current values of its dependencies, inside its OWN Ox scope, on a
    *     thread of its own: what the build acquires with `useInScope`/`releaseAfterScope` is released when that scope
    *     ends, which is when the node's value is retired (or at once, when the build fails).
    *   - A RELOAD computes the nodes the changed keys concern plus everything that depends on them ([[HotPlan.plan]]),
    *     builds them in that order into a new generation beside the running one, and publishes it — one
    *     `AtomicReference` swap — only when every one of them built. A failed build publishes nothing and closes what
    *     the reload had built.
    *   - A user PINS a generation ([[Hot.use]], [[pinned]], [[pin]]): its components are not closed while the pin is
    *     held. A retired component is closed once nobody pins it and its own `idle` check holds (a pool: no connection
    *     out); the reload waits for that up to a bound, then reports what is still in use, which goes on draining in
    *     the background — it is never closed under its user.
    *
    * The pin is a thread-local: the HTTP layer pins each request, and work handed to another thread carries its pin
    * over explicitly ([[carry]]). `C` is the config type (the server's is `HotConfig`).
    */
  final class Graph[C](initial: C, log: Graph.Log = Graph.Log.scribe) extends Pinning:
    import Graph.*

    private val defs = AtomicReference(Vector.empty[Def[C]])
    private val current = AtomicReference[Snap[C]](Snap(0L, initial, Map.empty))
    private val pinnedTL = ThreadLocal[Snap[C] | Null]()

    /** Node registration and reloads, one at a time. */
    private val lock = ReentrantLock()

    /** Retired components still waiting for their last user, by name. */
    private val draining = java.util.concurrent.ConcurrentHashMap[Instance, Unit]()
    private val closed = AtomicBoolean(false)

    // ── reading ────────────────────────────────────────────────────────────────────────────────────

    /** The generation the calling thread sees: its pin's, else the current one (not pinned). */
    def generation: com.histmersion.server.hot.Hot.Graph.GenerationResult = seen.generation

    /** The config the calling thread sees: its pin's, else the current one. Read it once per unit of work that needs
      * one consistent config; a pinned request sees one config throughout.
      */
    def config: C = seen.config

    private def seen: Snap[C] =
      val s = pinnedTL.get()
      if s == null then current.get() else s

    /** The node names, in the order they were added. */
    def nodes: List[com.histmersion.server.hot.Hot.Graph.NodesResult] = defs.get().map(_.name).toList

    /** Pin the generation the calling thread sees (its own pin's, nested, or the current one). Release it when the work
      * it covers is done; [[carry]] does both for work handed to another thread.
      */
    def pin(): Pin =
      val s = pinnedTL.get()
      if s != null then
        s.acquire() // the outer pin keeps every instance of `s` open, so it cannot be closing now
        Pin(s)
      else Pin(acquireCurrent())

    /** Run `f` with `p`'s generation as the calling thread's. Does not release `p`. */
    def within[A](p: Pin)(f: => A): A =
      val before = pinnedTL.get()
      pinnedTL.set(p.snap.asInstanceOf[Snap[C]]) // a Pin of this graph holds one of its own snapshots
      try f
      finally pinnedTL.set(before)

    /** Run `f` pinned: on the calling thread's generation when it is pinned already, else on the current one, released
      * when `f` returns.
      */
    /** The latest published config, whatever the calling thread is pinned to (a reload compares against it).
      */
    def latest: C = current.get().config

    /** The latest published generation's number. */
    def latestGeneration: com.histmersion.server.hot.Hot.Graph.LatestGenerationResult = current.get().generation

    def pinned[A](f: => A): A =
      if pinnedTL.get() != null then f
      else
        val p = pin()
        try within(p)(f)
        finally p.release()

    /** `f`, to be run later on another thread on the generation the calling thread sees NOW: pinned here, released when
      * it has run (or is dropped).
      */
    def carry[A](f: => A): Carried[A] =
      val p = pin()
      Carried(() => within(p)(f), () => p.release())

    /** The current snapshot, pinned: count first, then check that it is still current — a reload that published
      * meanwhile may already be waiting to close it, so the count is undone and retried.
      */
    private def acquireCurrent(): Snap[C] =
      @annotation.tailrec
      def loop(): Snap[C] =
        val s = current.get()
        s.acquire()
        if current.get() eq s then s
        else
          s.release()
          loop()
      loop()

    // ── nodes ──────────────────────────────────────────────────────────────────────────────────────

    /** Add a node and build its first value now (in its own scope); `Left` when the build fails or the node would break
      * the graph (a name used twice, a dependency cycle). `keys` are the config path prefixes the value is built from;
      * `idle` says whether a retired value may be closed (a pool: no connection out); `onPublish` runs after a reload
      * published a new value of this node.
      */
    def node[A](
        name: com.histmersion.server.hot.Hot.Graph.NodeName,
        deps: List[Hot[?]] = Nil,
        keys: List[com.histmersion.server.hot.Hot.Graph.NodeKeys] = Nil,
        idle: A => com.histmersion.server.hot.Hot.Graph.NodeIdleResult = (_: A) => true,
        onPublish: A => Unit = (_: A) => ()
    )(build: BuildCtx[C, A] => Ox ?=> Either[com.histmersion.server.hot.Hot.Graph.NodeBuildResultResultPart1, A]): Either[com.histmersion.server.hot.Hot.Graph.NodeResultPart1, Hot[A]] =
      locked {
        val d = Def[C](
          name,
          deps.map(_.name),
          keys,
          ctx => build(ctx.asInstanceOf[BuildCtx[C, A]]), // the ctx a Def is built with carries its own A
          v => idle(v.asInstanceOf[A]),
          v => onPublish(v.asInstanceOf[A])
        )
        for
          _ <- if closed.get() then Left("hot graph: closed") else Right(())
          _ <- HotPlan.order((defs.get() :+ d).map(_.spec).toList)
          s = current.get()
          i <- instantiate(d, BuildCtx[C, Any](s.config, None, None, Nil, n => s.nodes(n).value), s.generation)
        yield
          defs.updateAndGet(_ :+ d)
          // the same generation with one node more; a pin on the snapshot before it simply lacks the node
          // (Handle.use then pins the current one for that call)
          val next = s.modify(_.nodes).using(_.updated(name, i))
          current.set(next)
          Handle[A](name)
      }

    final private class Handle[A](val name: String) extends Hot[A]:
      def use[B](f: A => B): B =
        val s = pinnedTL.get()
        if s != null && s.nodes.contains(name) then f(s.nodes(name).value.asInstanceOf[A])
        else
          val p = Pin(acquireCurrent())
          try within(p)(f(p.snap.nodes(name).value.asInstanceOf[A]))
          finally p.release()
      def peek: A =
        val s = pinnedTL.get()
        (if s != null && s.nodes.contains(name) then s else current.get()).nodes(name).value.asInstanceOf[A]
      def latest: A = current.get().nodes(name).value.asInstanceOf[A]
      override def toString = s"Hot($name)"

    // ── reload ─────────────────────────────────────────────────────────────────────────────────────

    /** Switch to `next`: rebuild what `changed` (plus `forced`) concerns and everything depending on it, publish a new
      * generation when all of it built, retire the replaced values (waiting up to `drain` for their users). A change
      * that concerns no node still publishes the new config. `Left`: nothing was published; the running generation
      * stays.
      */
    def reload(
        next: C,
        changed: List[com.histmersion.server.hot.Hot.Graph.ReloadChanged],
        forced: Set[com.histmersion.server.hot.Hot.Graph.ReloadForced] = Set.empty,
        drain: FiniteDuration = 10.seconds
    ): Either[com.histmersion.server.hot.Hot.Graph.ReloadResultPart1, Outcome] =
      locked {
        if closed.get() then Left("hot graph: closed")
        else
          val s = current.get()
          val specs = defs.get()
          HotPlan.plan(specs.map(_.spec).toList, changed, forced).flatMap { plan =>
            val byName = specs.map(d => d.name -> d).toMap
            val gen = s.generation + 1
            val built = scala.collection.mutable.LinkedHashMap.empty[String, Instance]
            val failure = plan.iterator
              .map { name =>
                val d = byName(name)
                val ctx = BuildCtx[C, Any](
                  next,
                  s.nodes.get(name).map(_.value),
                  Some(s.config),
                  changed,
                  n =>
                    built
                      .get(n)
                      .orElse(s.nodes.get(n))
                      .map(_.value)
                      .getOrElse(throw IllegalStateException(s"hot graph: $name depends on $n, which has no value"))
                )
                instantiate(d, ctx, gen).map(i => built.update(name, i)).left.map(e => s"$name: $e")
              }
              .collectFirst { case Left(e) => e }
            failure match
              case Some(e) =>
                // nobody has seen these: close them now, dependents first
                built.values.toList.reverse.foreach(_.close(log))
                log.warn(s"config reload: nothing changed — $e")
                Left(e)
              case None =>
                val published = Snap(gen, next, s.nodes ++ built)
                current.set(published)
                val retired = plan.flatMap(s.nodes.get)
                // the hooks run on the NEW generation, whatever the reloading thread is pinned to
                val onNew = Pin(published)
                published.acquire()
                val notes =
                  try
                    within(onNew)(plan.flatMap { name =>
                      try { byName(name).onPublish(built(name).value); None }
                      catch case NonFatal(e) => Some(s"$name: after the swap: ${e.getMessage}")
                    })
                  finally onNew.release()
                notes.foreach(log.warn)
                Right(Outcome(gen, plan, retire(retired.reverse, drain), notes))
          }
      }

    /** Close the retired instances (dependents first) once each is free; wait for that up to `bound` and name what is
      * still in use then — it keeps draining in the background. A pin the calling thread holds itself (a reload asked
      * for over HTTP) does not count: it is released when the request returns.
      */
    private def retire(old: List[Instance], bound: FiniteDuration): List[com.histmersion.server.hot.Hot.Graph.RetireResult] =
      if old.isEmpty then Nil
      else
        old.foreach(i => draining.put(i, ()))
        val own = Option(pinnedTL.get()).map(_.nodes.values.toSet).getOrElse(Set.empty[Instance])
        val done = CountDownLatch(1)
        val _ = Thread.ofVirtual().name("histmersion-hot-drain").start { () =>
          try
            old.foreach { i =>
              while !i.free do Thread.sleep(DrainPollMs)
              i.close(log)
              draining.remove(i)
            }
          catch case _: InterruptedException => ()
          finally done.countDown()
        }
        val deadline = System.nanoTime() + bound.toNanos
        def settled = old.forall(i => i.isClosed || i.freeBut(if own(i) then 1 else 0))
        while !settled && System.nanoTime() < deadline do Thread.sleep(DrainPollMs)
        val busy = old.filterNot(i => i.isClosed || i.freeBut(if own(i) then 1 else 0))
        busy.foreach(i =>
          log.warn(
            s"config reload: ${i.describe} is still in use after ${bound.toSeconds} s — " +
              "it closes when its last user lets go"
          )
        )
        busy.map(_.describe)

    /** What is still draining from earlier generations. */
    def stillDraining: List[com.histmersion.server.hot.Hot.Graph.StillDrainingResult] =
      import scala.jdk.CollectionConverters.*
      draining.keySet.asScala.toList.map(_.describe).sorted

    /** Close every component (dependents first) — the server's shutdown, a test's end. In-use ones are waited for up to
      * `bound`, then closed anyway (the process is ending).
      */
    def close(bound: FiniteDuration = 10.seconds): Unit =
      locked {
        if closed.compareAndSet(false, true) then
          val s = current.get()
          val order = HotPlan.order(defs.get().map(_.spec).toList).getOrElse(defs.get().map(_.name).toList)
          val deadline = System.nanoTime() + bound.toNanos
          (order.reverse.flatMap(s.nodes.get) ++ {
            import scala.jdk.CollectionConverters.*
            draining.keySet.asScala.toList
          }).distinct.foreach { i =>
            while !i.free && System.nanoTime() < deadline do Thread.sleep(DrainPollMs)
            if !i.free then log.warn(s"shutdown: closing ${i.describe} while still in use")
            i.close(log)
          }
      }

    private def locked[A](f: => A): A =
      lock.lock()
      try f
      finally lock.unlock()

    /** Build one node's value in a scope of its own, on a thread of its own that holds the scope open until the value
      * is retired.
      */
    private def instantiate(d: Def[C], ctx: BuildCtx[C, Any], gen: com.histmersion.server.hot.Hot.Graph.InstantiateGen): Either[com.histmersion.server.hot.Hot.Graph.InstantiateResultPart1, Instance] =
      val built = CompletableFuture[Either[String, Any]]()
      val release = CountDownLatch(1)
      val ended = CountDownLatch(1)
      val _ = Thread.ofVirtual().name(s"histmersion-hot-${d.name}-$gen").start { () =>
        try
          supervised {
            val r =
              try d.build(ctx)
              catch case NonFatal(e) => Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
            built.complete(r)
            // a failed build ends its scope now, releasing whatever it had acquired
            if r.isRight then release.await()
          }
        catch
          case NonFatal(e) =>
            if !built.complete(Left(s"its scope failed: ${e.getMessage}")) then
              log.warn(s"${d.name} (generation $gen): closing its scope failed: ${e.getMessage}")
        finally ended.countDown()
      }
      built.get() match
        case Left(e) =>
          ended.await()
          Left(e)
        case Right(v) => Right(Instance(d.name, gen, v, () => d.idle(v), release, ended))

  object Graph:
    /** How often a drain looks at its components' users. */
    type GenerationResult = GenerationResult.T
    object GenerationResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NodesResult = NodesResult.T
    object NodesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LatestGenerationResult = LatestGenerationResult.T
    object LatestGenerationResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NodeResultPart1 = NodeResultPart1.T
    object NodeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NodeName = NodeName.T
    object NodeName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NodeKeys = NodeKeys.T
    object NodeKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NodeIdleResult = NodeIdleResult.T
    object NodeIdleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NodeBuildResultResultPart1 = NodeBuildResultResultPart1.T
    object NodeBuildResultResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReloadResultPart1 = ReloadResultPart1.T
    object ReloadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReloadChanged = ReloadChanged.T
    object ReloadChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReloadForced = ReloadForced.T
    object ReloadForced extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetireResult = RetireResult.T
    object RetireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StillDrainingResult = StillDrainingResult.T
    object StillDrainingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InstantiateResultPart1 = InstantiateResultPart1.T
    object InstantiateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InstantiateGen = InstantiateGen.T
    object InstantiateGen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    private[hot] val DrainPollMs = 20L

    trait Log:
      def info(m: com.histmersion.server.hot.Hot.Graph.Log.InfoM): Unit
      def warn(m: com.histmersion.server.hot.Hot.Graph.Log.WarnM): Unit
    object Log:
      type InfoM = InfoM.T
      object InfoM extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type WarnM = WarnM.T
      object WarnM extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

      val scribe: Log = new Log:
        def info(m: com.histmersion.server.hot.Hot.Graph.Log.InfoM) = _root_.scribe.info(m)
        def warn(m: com.histmersion.server.hot.Hot.Graph.Log.WarnM) = _root_.scribe.warn(m)

    /** What a reload did: the generation published, the nodes rebuilt (in build order), the retired components still in
      * use when it stopped waiting, and the `onPublish` failures.
      */
    final case class Outcome(generation: Outcome.Generation, rebuilt: List[Outcome.Rebuilt], draining: List[Outcome.Draining], warnings: List[Outcome.Warnings])

    object Outcome:
      type Generation = Generation.T
      object Generation extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Rebuilt = Rebuilt.T
      object Rebuilt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Draining = Draining.T
      object Draining extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Warnings = Warnings.T
      object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    final private[hot] case class Def[C](
        name: Def.Name,
        deps: List[Def.Deps],
        keys: List[Def.Keys],
        build: BuildCtx[C, Any] => Ox ?=> Either[Def.BuildResultResultPart1, Any],
        idle: Any => Def.IdleResult,
        onPublish: Any => Unit
    ):
      def spec: HotNode = HotNode(name, deps, keys)

    object Def:
      type BuildResultResultPart1 = BuildResultResultPart1.T
      object BuildResultResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type IdleResult = IdleResult.T
      object IdleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

      type Name = Name.T
      object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Deps = Deps.T
      object Deps extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Keys = Keys.T
      object Keys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    /** One built value, the users pinning it and the scope holding it open. */
    final private[hot] class Instance(
        val name: String,
        val generation: Long,
        val value: Any,
        idle: () => Boolean,
        release: CountDownLatch,
        ended: CountDownLatch
    ):
      val users = AtomicInteger(0)
      private val closedFlag = AtomicBoolean(false)
      def isClosed: com.histmersion.server.hot.Hot.Graph.Instance.IsClosedResult = closedFlag.get()
      private def idleNow: com.histmersion.server.hot.Hot.Graph.Instance.IdleNowResult =
        try idle()
        catch case NonFatal(_) => true
      def free: com.histmersion.server.hot.Hot.Graph.Instance.FreeResult = users.get() == 0 && idleNow

      /** Free but for `own` pins the asking thread holds itself. */
      def freeBut(own: com.histmersion.server.hot.Hot.Graph.Instance.FreeButOwn): com.histmersion.server.hot.Hot.Graph.Instance.FreeButResult = users.get() <= own && idleNow
      def describe: com.histmersion.server.hot.Hot.Graph.Instance.DescribeResult = s"$name (generation $generation, ${users.get()} pinned)"
      def close(log: Log): Unit =
        if closedFlag.compareAndSet(false, true) then
          release.countDown()
          if !ended.await(CloseWaitSeconds, TimeUnit.SECONDS) then
            log.warn(s"$name (generation $generation): its scope did not close within $CloseWaitSeconds s")

    object Instance:
      type IsClosedResult = IsClosedResult.T
      object IsClosedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type IdleNowResult = IdleNowResult.T
      object IdleNowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type FreeResult = FreeResult.T
      object FreeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type FreeButResult = FreeButResult.T
      object FreeButResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type FreeButOwn = FreeButOwn.T
      object FreeButOwn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type DescribeResult = DescribeResult.T
      object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    private val CloseWaitSeconds = 60L

    /** One generation: its number, its config and the instance of every node. */
    final private[hot] case class Snap[C](generation: Snap.Generation, config: C, nodes: Map[Snap.NodesKey, Instance]):
      def acquire(): Unit = nodes.values.foreach(_.users.incrementAndGet())
      def release(): Unit = nodes.values.foreach(_.users.decrementAndGet())

    object Snap:
      type Generation = Generation.T
      object Generation extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type NodesKey = NodesKey.T
      object NodesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
