package com.histmersion.util.data.backup

/** A dump whose file name is fixed but which has not started — so a caller can report the name of a backup that is
  * still running ([[BackupJobs]]).
  */
import com.histmersion.api.Api
import com.histmersion.api.backup.Backup
final case class PreparedBackup(file: String, run: () => Either[Api.Error, Backup.Result])
