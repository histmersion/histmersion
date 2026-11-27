package com.histmersion
package web

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.util.problem.Problem.toVector
import com.histmersion.api.Api

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success}
import com.histmersion.lint.{tolerated, LintRule}

/** The views' one request type (G107, review finding W4): answers delivered in any order leave the newest one's state,
  * a server's problems stay typed with their status, no answer at all is "unreachable" keeping what was shown, and a
  * page's own exception is a typed internal problem. No browser, no server: promises complete by hand.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.SilentInterp, "pending G35 (a vocabulary renders through its wire string)")
class RequestSuite extends munit.FunSuite:

  // answers are applied the moment a promise completes
  private given ExecutionContext = ExecutionContext.parasitic

  private val conflict = ApiFailure(
    ApiFailure.Status(412),
    Api.Error(Problem.Conflict.AlreadyExists(Problem.TypeName("Goal.Slug"), Problem.Raw("tin")))
  )
  private val down = ServerUnreachable(External.Reported("TypeError: Failed to fetch"))

  test("answers delivered in every order leave the newest request's answer"):
    List(0, 1, 2).permutations.foreach { order =>
      val r = Request[Int]()
      val ps = List.fill(3)(Promise[Int]())
      ps.foreach(p => r.ask(p.future))
      order.foreach(i => ps(i).success(i))
      assertEquals(r.now, Loaded.Ready(2), s"order $order")
    }

  test("a failure or an unreachable server of a superseded request never replaces the newest answer"):
    val r = Request[Int]()
    val (old1, old2, newest) = (Promise[Int](), Promise[Int](), Promise[Int]())
    r.ask(old1.future); r.ask(old2.future); r.ask(newest.future)
    newest.success(7)
    old1.failure(conflict)
    old2.failure(down)
    assertEquals(r.now, Loaded.Ready(7))

  test("while loading, and when the server cannot be reached, what was shown stays"):
    val r = Request[Int]()
    r.ask(scala.concurrent.Future.successful(1))
    val p = Promise[Int]()
    r.ask(p.future)
    assertEquals(r.now, Loaded.Loading(Some(1)))
    assertEquals(r.now.shown, Some(1))
    p.failure(down)
    assertEquals(r.now, Loaded.Unreachable(Some(1)))
    assertEquals(r.now.shown, Some(1))

  test("the server's problems stay typed, with the status: a 412 conflict is not a 500"):
    val r = Request[Int]()
    val p = Promise[Int]()
    r.ask(p.future)
    p.failure(conflict)
    r.now match
      case Loaded.Failed(status, error) =>
        assertEquals(status.value, 412)
        assert(
          error.problems.toVector.exists {
            case _: Problem.Conflict.AlreadyExists => true
            case _                                 => false
          },
          s"$error"
        )
      case other => fail(s"not a typed failure: $other")

  test("a page's own exception is an internal problem with its class, never a bare message"):
    Loaded.of(Failure(IllegalStateException("decoder bug")), None) match
      case Loaded.Failed(_, error) =>
        assert(
          error.problems.toVector.exists {
            case u: Problem.Internal.Unexpected => s"${u.exception}" == "java.lang.IllegalStateException"
            case _                              => false
          },
          s"$error"
        )
      case other => fail(s"$other")
    assertEquals(Loaded.of(Success(3), Some(1)), Loaded.Ready(3))
    assertEquals(Loaded.of(Failure(down), Some(1)), Loaded.Unreachable(Some(1)))

  test("cancel drops every answer still out; set shows a value as the answer"):
    val r = Request[Int]()
    val p = Promise[Int]()
    r.ask(p.future)
    r.cancel()
    p.success(1)
    assertEquals(r.now, Loaded.Loading(None))
    r.set(5)
    assertEquals(r.now, Loaded.Ready(5))

  test("askThen hands the newest outcome on, once"):
    val r = Request[Int]()
    val seen = scala.collection.mutable.ListBuffer.empty[Loaded[Int]]
    val (a, b) = (Promise[Int](), Promise[Int]())
    r.askThen(a.future)(seen += _)
    r.askThen(b.future)(seen += _)
    a.success(1)
    b.failure(down)
    assertEquals(seen.toList, List(Loaded.Unreachable(None)))

  test("the error lines: each problem's sentence, or the unreachable line saying whether the old answer stays"):
    assertEquals(ErrorLine.said(Loaded.Ready(1)), Nil)
    assertEquals(ErrorLine.said(Loaded.Loading(None)), Nil)
    assertEquals(ErrorLine.said(Loaded.Unreachable(None)), List("The server could not be reached."))
    assertEquals(
      ErrorLine.said(Loaded.Unreachable(Some(1))),
      List("The server could not be reached: showing what was loaded before.")
    )
    assertEquals(ErrorLine.said(Loaded.Failed(conflict.status, conflict.error)).size, 1)
