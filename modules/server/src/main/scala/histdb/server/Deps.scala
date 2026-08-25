package histdb.server

import histdb.core.AppConfig
import javax.sql.DataSource

/** Everything the handlers need. Built once in `Main` (the composition root) and passed down — no
  * object-initialisation side effects anywhere below this. */
final case class Deps(cfg: AppConfig, ds: DataSource)
