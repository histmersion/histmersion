package com.histmersion.server.hot

import com.histmersion.config.{HotNode, HotPlan}
import ox.{Ox, supervised}

import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import java.util.concurrent.locks.ReentrantLock
import scala.concurrent.duration.*
import scala.util.control.NonFatal

/** A stable handle on a component a config reload may rebuild (docs/research-plugins.md §3.6). Its
  * holder never reloads: [[use]] gives the value of the generation the calling thread is pinned to —
  * or pins the current generation for the duration of the call — so a value is never closed under
  * the code using it. */
trait Hot[+A]:
  def name: String

  /** The value for this call, pinned while `f` runs. */
  def use[B](f: A => B): B

  /** The value the calling thread sees now (its pin's, else the current one), NOT pinned: for reading
    * something that is not closed with its generation (a report, a registry snapshot). */
  def peek: A

  /** The latest published value, whatever the calling thread is pinned to; not pinned. */
  def latest: A

/** What a node's build is given: the config being switched to, the values of its dependencies in the
  * generation being built, and — on a rebuild — its own previous value and config (so a node may carry
  * over what did not change, as the plugin node does per plugin). */
final class BuildCtx[C, A] private[hot] (
    val config: C,
    val previous: Option[A],
    val previousConfig: Option[C],
    /** The config keys this reload changed (empty for the first build). */
    val changed: List[String],
    dep: String => Any):
  /** A dependency's value in the generation being built. */
  def apply[B](h: Hot[B]): B =
    // the map behind `dep` holds, under a node's name, a value of the type that node's Hot was made
    // with (HotGraph.node is the only writer), so the name fixes the type
    dep(h.name).asInstanceOf[B]

/** A pin on one generation: while it is held, none of that generation's components is closed. */
final class Pin private[hot] (private[hot] val snap: HotGraph.Snap[?]):
  private val released = AtomicBoolean(false)
  def generation: Long = snap.generation
  def release(): Unit = if released.compareAndSet(false, true) then snap.release()

/** The hot component graph (docs/research-plugins.md §3.6): Ox has no `Hotswap`, so this is one.
  *
  *  - A NODE is built from the config and the current values of its dependencies, inside its OWN Ox
  *    scope, on a thread of its own: what the build acquires with `useInScope`/`releaseAfterScope` is
  *    released when that scope ends, which is when the node's value is retired (or at once, when the
  *    build fails).
  *  - A RELOAD computes the nodes the changed keys concern plus everything that depends on them
  *    ([[HotPlan.plan]]), builds them in that order into a new generation beside the running one, and
  *    publishes it — one `AtomicReference` swap — only when every one of them built. A failed build
  *    publishes nothing and closes what the reload had built.
  *  - A user PINS a generation ([[Hot.use]], [[pinned]], [[pin]]): its components are not closed while
  *    the pin is held. A retired component is closed once nobody pins it and its own `idle` check holds
  *    (a pool: no connection out); the reload waits for that up to a bound, then reports what is still
  *    in use, which goes on draining in the background — it is never closed under its user.
  *
  * The pin is a thread-local: the HTTP layer pins each request, and work handed to another thread
  * carries its pin over explicitly ([[carry]]). `C` is the config type (the server's is `HotConfig`). */
final class HotGraph[C](initial: C, log: HotGraph.Log = HotGraph.Log.scribe) extends Pinning:
  import HotGraph.*

  private val defs    = AtomicReference(Vector.empty[Def[C]])
  private val current = AtomicReference[Snap[C]](Snap(0L, initial, Map.empty))
  private val pinnedTL = ThreadLocal[Snap[C] | Null]()
  /** Node registration and reloads, one at a time. */
  private val lock = ReentrantLock()
  /** Retired components still waiting for their last user, by name. */
  private val draining = java.util.concurrent.ConcurrentHashMap[Instance, Unit]()
  private val closed = AtomicBoolean(false)

  // ── reading ────────────────────────────────────────────────────────────────────────────────────

  /** The generation the calling thread sees: its pin's, else the current one (not pinned). */
  def generation: Long = seen.generation

  /** The config the calling thread sees: its pin's, else the current one. Read it once per unit of
    * work that needs one consistent config; a pinned request sees one config throughout. */
  def config: C = seen.config

  private def seen: Snap[C] =
    val s = pinnedTL.get()
    if s == null then current.get() else s

  /** The node names, in the order they were added. */
  def nodes: List[String] = defs.get().map(_.name).toList

  /** Pin the generation the calling thread sees (its own pin's, nested, or the current one). Release it
    * when the work it covers is done; [[carry]] does both for work handed to another thread. */
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

  /** Run `f` pinned: on the calling thread's generation when it is pinned already, else on the current
    * one, released when `f` returns. */
  /** The latest published config, whatever the calling thread is pinned to (a reload compares
    * against it). */
  def latest: C = current.get().config

  /** The latest published generation's number. */
  def latestGeneration: Long = current.get().generation

  def pinned[A](f: => A): A =
    if pinnedTL.get() != null then f
    else
      val p = pin()
      try within(p)(f)
      finally p.release()

  /** `f`, to be run later on another thread on the generation the calling thread sees NOW: pinned
    * here, released when it has run (or is dropped). */
  def carry[A](f: => A): Carried[A] =
    val p = pin()
    Carried(() => within(p)(f), () => p.release())

  /** The current snapshot, pinned: count first, then check that it is still current — a reload that
    * published meanwhile may already be waiting to close it, so the count is undone and retried. */
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

  /** Add a node and build its first value now (in its own scope); `Left` when the build fails or the
    * node would break the graph (a name used twice, a dependency cycle). `keys` are the config path
    * prefixes the value is built from; `idle` says whether a retired value may be closed (a pool: no
    * connection out); `onPublish` runs after a reload published a new value of this node. */
  def node[A](name: String, deps: List[Hot[?]] = Nil, keys: List[String] = Nil,
      idle: A => Boolean = (_: A) => true, onPublish: A => Unit = (_: A) => ())(
      build: BuildCtx[C, A] => Ox ?=> Either[String, A]): Either[String, Hot[A]] =
    locked {
      val d = Def[C](name, deps.map(_.name), keys,
        ctx => build(ctx.asInstanceOf[BuildCtx[C, A]]), // the ctx a Def is built with carries its own A
        v => idle(v.asInstanceOf[A]), v => onPublish(v.asInstanceOf[A]))
      for
        _ <- if closed.get() then Left("hot graph: closed") else Right(())
        _ <- HotPlan.order((defs.get() :+ d).map(_.spec).toList)
        s  = current.get()
        i <- instantiate(d, BuildCtx[C, Any](s.config, None, None, Nil, n => s.nodes(n).value), s.generation)
      yield
        defs.updateAndGet(_ :+ d)
        // the same generation with one node more; a pin on the snapshot before it simply lacks the node
        // (Handle.use then pins the current one for that call)
        val next = s.copy(nodes = s.nodes.updated(name, i))
        current.set(next)
        Handle[A](name)
    }

  private final class Handle[A](val name: String) extends Hot[A]:
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

  /** Switch to `next`: rebuild what `changed` (plus `forced`) concerns and everything depending on
    * it, publish a new generation when all of it built, retire the replaced values (waiting up to
    * `drain` for their users). A change that concerns no node still publishes the new config. `Left`:
    * nothing was published; the running generation stays. */
  def reload(next: C, changed: List[String], forced: Set[String] = Set.empty,
      drain: FiniteDuration = 10.seconds): Either[String, Outcome] =
    locked {
      if closed.get() then Left("hot graph: closed")
      else
        val s     = current.get()
        val specs = defs.get()
        HotPlan.plan(specs.map(_.spec).toList, changed, forced).flatMap { plan =>
          val byName = specs.map(d => d.name -> d).toMap
          val gen    = s.generation + 1
          val built  = scala.collection.mutable.LinkedHashMap.empty[String, Instance]
          val failure = plan.iterator.map { name =>
            val d   = byName(name)
            val ctx = BuildCtx[C, Any](next, s.nodes.get(name).map(_.value), Some(s.config), changed,
              n => built.get(n).orElse(s.nodes.get(n)).map(_.value).getOrElse(
                throw IllegalStateException(s"hot graph: $name depends on $n, which has no value")))
            instantiate(d, ctx, gen).map(i => built.update(name, i)).left.map(e => s"$name: $e")
          }.collectFirst { case Left(e) => e }
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
                try within(onNew)(plan.flatMap { name =>
                  try { byName(name).onPublish(built(name).value); None }
                  catch case NonFatal(e) => Some(s"$name: after the swap: ${e.getMessage}")
                })
                finally onNew.release()
              notes.foreach(log.warn)
              Right(Outcome(gen, plan, retire(retired.reverse, drain), notes))
        }
    }

  /** Close the retired instances (dependents first) once each is free; wait for that up to `bound`
    * and name what is still in use then — it keeps draining in the background. A pin the calling
    * thread holds itself (a reload asked for over HTTP) does not count: it is released when the
    * request returns. */
  private def retire(old: List[Instance], bound: FiniteDuration): List[String] =
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
      busy.foreach(i => log.warn(s"config reload: ${i.describe} is still in use after ${bound.toSeconds} s — " +
        "it closes when its last user lets go"))
      busy.map(_.describe)

  /** What is still draining from earlier generations. */
  def stillDraining: List[String] =
    import scala.jdk.CollectionConverters.*
    draining.keySet.asScala.toList.map(_.describe).sorted

  /** Close every component (dependents first) — the server's shutdown, a test's end. In-use ones are
    * waited for up to `bound`, then closed anyway (the process is ending). */
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

  /** Build one node's value in a scope of its own, on a thread of its own that holds the scope open
    * until the value is retired. */
  private def instantiate(d: Def[C], ctx: BuildCtx[C, Any], gen: Long): Either[String, Instance] =
    val built   = CompletableFuture[Either[String, Any]]()
    val release = CountDownLatch(1)
    val ended   = CountDownLatch(1)
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

object HotGraph:
  /** How often a drain looks at its components' users. */
  private[hot] val DrainPollMs = 20L

  trait Log:
    def info(m: String): Unit
    def warn(m: String): Unit
  object Log:
    val scribe: Log = new Log:
      def info(m: String) = _root_.scribe.info(m)
      def warn(m: String) = _root_.scribe.warn(m)

  /** What a reload did: the generation published, the nodes rebuilt (in build order), the retired
    * components still in use when it stopped waiting, and the `onPublish` failures. */
  final case class Outcome(generation: Long, rebuilt: List[String], draining: List[String], warnings: List[String])

  private[hot] final case class Def[C](name: String, deps: List[String], keys: List[String],
      build: BuildCtx[C, Any] => Ox ?=> Either[String, Any], idle: Any => Boolean, onPublish: Any => Unit):
    def spec: HotNode = HotNode(name, deps, keys)

  /** One built value, the users pinning it and the scope holding it open. */
  private[hot] final class Instance(val name: String, val generation: Long, val value: Any, idle: () => Boolean,
      release: CountDownLatch, ended: CountDownLatch):
    val users = AtomicInteger(0)
    private val closedFlag = AtomicBoolean(false)
    def isClosed: Boolean = closedFlag.get()
    private def idleNow: Boolean =
      try idle() catch case NonFatal(_) => true
    def free: Boolean = users.get() == 0 && idleNow
    /** Free but for `own` pins the asking thread holds itself. */
    def freeBut(own: Int): Boolean = users.get() <= own && idleNow
    def describe: String = s"$name (generation $generation, ${users.get()} pinned)"
    def close(log: Log): Unit =
      if closedFlag.compareAndSet(false, true) then
        release.countDown()
        if !ended.await(CloseWaitSeconds, TimeUnit.SECONDS) then
          log.warn(s"$name (generation $generation): its scope did not close within $CloseWaitSeconds s")

  private val CloseWaitSeconds = 60L

  /** One generation: its number, its config and the instance of every node. */
  private[hot] final case class Snap[C](generation: Long, config: C, nodes: Map[String, Instance]):
    def acquire(): Unit = nodes.values.foreach(_.users.incrementAndGet())
    def release(): Unit = nodes.values.foreach(_.users.decrementAndGet())
