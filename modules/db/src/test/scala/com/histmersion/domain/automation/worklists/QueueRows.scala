package com.histmersion.domain.automation.worklists

import com.histmersion.db.*

import com.histmersion.api.Write
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Queue.{Kind, QueueAdd, QueueItem, QueuePatch}
import com.histmersion.domain.automation.worklists.QueueRepo

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.Api
import com.histmersion.util.database.given

/** Work-list rows written and read straight through the repository, each call in a transaction of its own: how the
  * suites of other features seed and inspect the lists (the work lists' own behaviour is the server's
  * `QueueServiceContractSuite`).
  */
object QueueRows:
  def add(ds: DataSource, kind: Kind, in: QueueAdd): QueueItem = transact(ds)(QueueRepo.insert(kind, in))
  def list(ds: DataSource, kind: Kind, all: Boolean): Vector[QueueItem] = connect(ds)(QueueRepo.list(kind, all))
  def get(ds: DataSource, id: QueueItem.Id): Option[QueueItem] = connect(ds)(QueueRepo.get(repoId(id)))
  def acquireNaming(ds: DataSource, doi: com.histmersion.domain.library.Doi): Vector[QueueItem] =
    connect(ds)(QueueRepo.naming(Kind.Acquire, doi))

  private def repoId(id: QueueItem.Id): QueueRepo.QueueItemId = QueueRepo.QueueItemId(id.value)
  private def notFound(id: QueueItem.Id): Api.Error =
    Api.Error(Api.Error.Message(s"No queue item #${id.value}"), Some(Api.Error.Code(Write.CodeNotFound)))

  def patch(ds: DataSource, id: QueueItem.Id, p: QueuePatch): Either[Api.Error, QueueItem] =
    transact(ds):
      QueueRepo.get(repoId(id)).toRight(notFound(id)).map { cur =>
        QueueRepo.update(Queue.applyPatch(cur, p))
        QueueRepo.get(repoId(id)).get
      }

  def delete(ds: DataSource, id: QueueItem.Id): Either[Api.Error, QueueItem] =
    transact(ds):
      QueueRepo.get(repoId(id)).toRight(notFound(id)).map { item =>
        QueueRepo.delete(repoId(id))
        item
      }
