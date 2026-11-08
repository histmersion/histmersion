package com.histmersion
package domain.automation.worklists

import java.time.Clock
import java.util.concurrent.atomic.AtomicReference

class QueueServiceInMemorySuite extends QueueServiceContract("memory"):
  protected def fresh(test: String): Option[QueueService] =
    Some(QueueService.InMemory(AtomicReference(WorkListState.empty), Clock.systemUTC()))
