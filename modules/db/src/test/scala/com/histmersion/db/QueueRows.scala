package com.histmersion.db

import com.histmersion.api.{ApiError, Queue, Write}
import com.histmersion.api.Queue.{Kind, QueueAdd, QueueItem, QueuePatch}
import com.histmersion.domain.automation.worklists.QueueRepo

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Work-list rows written and read straight through the repository, each call in a transaction of its own: how the
  * suites of other features seed and inspect the lists (the work lists' own behaviour is the server's
  * `QueueServiceContractSuite`).
  */
object QueueRows:
  def add(ds: DataSource, kind: Kind, in: QueueAdd): QueueItem = transact(ds)(QueueRepo.insert(kind, in))
  def list(ds: DataSource, kind: Kind, all: Boolean): Vector[QueueItem] = connect(ds)(QueueRepo.list(kind, all))
  def get(ds: DataSource, id: Int): Option[QueueItem] = connect(ds)(QueueRepo.get(id))
  def acquireNaming(ds: DataSource, doi: String): Vector[QueueItem] = connect(ds)(QueueRepo.naming(Kind.Acquire, doi))

  def patch(ds: DataSource, id: Int, p: QueuePatch): Either[ApiError, QueueItem] =
    transact(ds):
      QueueRepo.get(id).toRight(ApiError(s"No queue item #$id", Some(Write.CodeNotFound))).map { cur =>
        QueueRepo.update(Queue.applyPatch(cur, p))
        QueueRepo.get(id).get
      }

  def delete(ds: DataSource, id: Int): Either[ApiError, QueueItem] =
    transact(ds):
      QueueRepo.get(id).toRight(ApiError(s"No queue item #$id", Some(Write.CodeNotFound))).map { item =>
        QueueRepo.delete(id)
        item
      }
