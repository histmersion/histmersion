package com.histmersion
package util.data.backup

/** A dump whose file name is fixed but which has not started — so a caller can report the name of a backup that is
  * still running ([[BackupJobs]]).
  */
import com.histmersion.api.Api
import com.histmersion.api.backup.Backup
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
final case class PreparedBackup(file: PreparedBackup.File, run: () => Either[Api.Error, Backup.Result])

object PreparedBackup:
  type File = File.T
  object File extends RefinedType[String, Pure]
