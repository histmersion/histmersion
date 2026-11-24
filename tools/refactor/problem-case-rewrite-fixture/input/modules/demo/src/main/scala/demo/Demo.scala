package demo

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Conflict

object Demo:
  def taken: Problem = Problem.Conflict.AlreadyExists("Place.Id", "p1")
  def short: Problem = Conflict.AlreadyExists("Place.Id", "p2")
  def busy: Problem = Problem.Conflict.Busy(Problem.Conflict.Activity.Backup)
  def gone: Problem = Problem.Run.Vanished
  def said(p: Problem): String = p match
    case Problem.Conflict.AlreadyExists(t, k)     => s"$t $k"
    case Conflict.Busy(Conflict.Activity.Backup) => "backup"
    case Problem.Run.Vanished                    => "gone"
    case _                                       => "other"
