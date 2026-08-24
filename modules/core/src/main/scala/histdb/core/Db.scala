package histdb.core

import org.postgresql.ds.PGSimpleDataSource
import javax.sql.DataSource

/** Postgres DataSource from the HOCON config (see [[AppConfig]] / reference.conf). */
object Db:
  def dataSource(cfg: DbConfig): DataSource =
    val ds = PGSimpleDataSource()
    ds.setUrl(cfg.url)
    ds.setUser(cfg.user)
    ds.setPassword(cfg.password)
    ds

  def fromConfig(): DataSource = dataSource(AppConfig.load().db)
