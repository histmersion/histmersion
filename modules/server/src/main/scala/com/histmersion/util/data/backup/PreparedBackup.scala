package com.histmersion.util.data.backup

import com.histmersion.api.ApiError
import com.histmersion.api.backup.BackupResult

/** A dump whose file name is fixed but which has not started — so a caller can report the name of a backup that is
  * still running ([[BackupJobs]]).
  */
final case class PreparedBackup(file: String, run: () => Either[ApiError, BackupResult])
