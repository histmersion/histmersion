package com.histmersion.util.data.checks

import com.augustnagro.magnum.DbCon

/** What several checks read: one run of the checks reads it once, on its connection, when a check first asks. */
final class CheckInput[A](val read: DbCon ?=> A)
