package com.histmersion
package domain.schedulerpolicy

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.schedulerpolicy.Block
import com.histmersion.domain.schedulerpolicy.JobType
import com.histmersion.domain.schedulerpolicy.Candidate
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Capacity(
    maxRuns: Capacity.MaxRuns,
    occupied: Capacity.Occupied,
    poolCap: Map[Candidate.Pools, Capacity.PoolCapValue] = Map.empty,
    poolUsed: Map[Candidate.Pools, Capacity.PoolUsedValue] = Map.empty,
    managerCap: Map[Candidate.ManagerId, Capacity.ManagerCapValue] = Map.empty,
    managerUsed: Map[Candidate.ManagerId, Capacity.ManagerUsedValue] = Map.empty,
    gatheringUsed: Capacity.GatheringUsed = Capacity.GatheringUsed(0),
    stop: Option[Block] = None,
    recent: List[JobType] = Nil
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def room: Capacity.Room = Capacity.Room(math.max(0, maxRuns.value - occupied.value))

object Capacity:

  type MaxRuns = MaxRuns.T
  object MaxRuns extends Newtype[Int, Pure]
  type Occupied = Occupied.T
  object Occupied extends Newtype[Int, Pure]
  type Room = Room.T
  object Room extends Newtype[Int, Pure]
  type PoolCapValue = PoolCapValue.T
  object PoolCapValue extends Newtype[Int, Pure]
  type PoolUsedValue = PoolUsedValue.T
  object PoolUsedValue extends Newtype[Int, Pure]
  type ManagerCapValue = ManagerCapValue.T
  object ManagerCapValue extends Newtype[Int, Pure]
  type ManagerUsedValue = ManagerUsedValue.T
  object ManagerUsedValue extends Newtype[Int, Pure]
  type GatheringUsed = GatheringUsed.T
  object GatheringUsed extends Newtype[Int, Pure]
