package com.histmersion
package domain.automation.worklists

import com.histmersion.api.automation.queue.Kind
import com.histmersion.api.automation.queue.QueueAdd
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.queue.QueuePatch
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.db.*

import com.histmersion.api.Write
import com.histmersion.api.automation.Queue
import com.histmersion.domain.automation.worklists.QueueRepo

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.Api
import com.histmersion.util.database.given
import com.histmersion.domain.library.Doi
import com.histmersion.domain.Creator
import com.histmersion.util.database.TestCreators
import com.histmersion.lint.{tolerated, LintRule}

/** Work-list rows written and read straight through the repository, each call in a transaction of its own: how the
  * suites of other features seed and inspect the lists (the work lists' own behaviour is the server's
  * `QueueServiceContractSuite`).
  */
object QueueRows:
  def add(ds: DataSource, kind: Kind, in: QueueAdd): QueueItem =
    transact(ds)(QueueRepo.insert(kind, in, Creator.New.Manual(Creator.Surface.Rest), TestCreators.at))

  def addAs(ds: DataSource, kind: Kind, in: QueueAdd, creator: Creator.New): QueueItem =
    transact(ds)(QueueRepo.insert(kind, in, creator, TestCreators.at))
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def list(ds: DataSource, kind: Kind, all: Boolean): Vector[QueueItem] = connect(ds)(QueueRepo.list(kind, all))
  def get(ds: DataSource, id: QueueItem.Id): Option[QueueItem] = connect(ds)(QueueRepo.get(repoId(id)))
  def acquireNaming(ds: DataSource, doi: Doi): Vector[QueueItem] =
    connect(ds)(QueueRepo.naming(Kind.Acquire, doi))

  /** The row removed straight, with no read before it: false when it was gone (G94). */
  def removeRow(ds: DataSource, id: QueueItem.Id): Boolean = transact(ds)(QueueRepo.delete(repoId(id)))

  /** `item` written over its row straight, with no read before it: false when the row was gone (G94). */
  def writeOver(ds: DataSource, item: QueueItem): Boolean = transact(ds)(QueueRepo.update(item))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def repoId(id: QueueItem.Id): QueueRepo.QueueItemId = QueueRepo.QueueItemId(id.value)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def notFound(id: QueueItem.Id): Api.Error =
    Api.Error(
      Problem.Unclassified(Legacy.Text(s"No queue item #${id.value}")),
      Some(Api.Error.Code(Write.CodeNotFound))
    )

  def patch(ds: DataSource, id: QueueItem.Id, p: QueuePatch): Either[Api.Error, QueueItem] =
    transact(ds):
      QueueRepo.get(repoId(id)).toRight(notFound(id)).map { cur =>
        QueueRepo.update(cur.applyPatch(p))
        QueueRepo.get(repoId(id)).get
      }

  def delete(ds: DataSource, id: QueueItem.Id): Either[Api.Error, QueueItem] =
    transact(ds):
      QueueRepo.get(repoId(id)).toRight(notFound(id)).map { item =>
        QueueRepo.delete(repoId(id))
        item
      }
