package demo

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Conflict

object Demo:
  def taken: Problem = Problem.Conflict.Taken("Place.Id", "p1", Problem.Conflict.Clash.Exists)
  def short: Problem = Conflict.Taken("Place.Id", "p2", Conflict.Clash.Exists)
  def busy: Problem = Problem.Conflict.Busy(Problem.Conflict.Activity.Restore)
  def gone: Problem = Problem.Run.Stopped(Problem.Run.Stop.Vanished)
  def said(p: Problem): String = p match
    case Problem.Conflict.Taken(t, k, Problem.Conflict.Clash.Exists)     => s"$t $k"
    case Conflict.Busy(Conflict.Activity.Restore) => "backup"
    case Problem.Run.Stopped(Problem.Run.Stop.Vanished)                    => "gone"
    case _                                       => "other"
