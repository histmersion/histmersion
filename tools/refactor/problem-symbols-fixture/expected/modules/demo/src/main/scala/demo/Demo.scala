package demo

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Config

object Demo:
  def missing: Problem = Problem.Invalid.Missing.here("x")
  def empty: Problem = Config.Setting("k", Config.Fault.Empty)
  val fault: Config.Fault = Config.Fault.Rejected("no")
  val bounds = Problem.Invalid.Bounds(None, None)
  def relayed: Problem = Problem.Relayed("outside")
  def stopped(p: Problem): Boolean = p match
    case Problem.Run.Interrupted               => true
    case Config.Setting(_, Config.Fault.Empty) => true
    case _                                     => false
  def same(f: Config.Fault): Boolean = f == Config.Fault.Empty
  def actor(word: String): Option[Problem.Run.Actor] = Problem.Run.Actor.values.find(_.toString == word)
