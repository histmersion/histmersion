package fixture

import com.augustnagro.magnum.Frag
import fixture.util.RangeSql
import com.augustnagro.magnum.sql

object NoSql:
  // a file without `sql` in scope gets it for the fragment the rule writes
  def cols: Frag = RangeSql.cols(sql"p.period")
