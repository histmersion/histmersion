package transactionfixture

import com.histmersion.util.database.Transactor
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.{Connection, SQLException, Statement}
import javax.sql.DataSource
import scala.collection.mutable.ListBuffer

object Lifecycle:
  final class Recording:
    val events = ListBuffer.empty[String]
    var auto = true
    var readOnly = false
    var fail = Set.empty[String]
    def record(event: String): Unit =
      events += event
      if fail(event) then throw new SQLException(event)
    def proxy[A](kind: Class[A])(body: (String, Array[Object]) => Object): A =
      kind.cast(Proxy.newProxyInstance(kind.getClassLoader, Array(kind), new InvocationHandler:
        def invoke(instance: Object, method: Method, arguments: Array[Object]): Object =
          body(method.getName, if arguments == null then Array.empty[Object] else arguments)
      ))
    val statement: Statement = proxy(classOf[Statement]) { (name, args) =>
      name match
        case "execute" => record(args(0).toString); java.lang.Boolean.TRUE
        case "close" => record("statement.close"); null
        case _ => throw new UnsupportedOperationException(name)
    }
    val connection: Connection = proxy(classOf[Connection]) { (name, args) =>
      name match
        case "getAutoCommit" => java.lang.Boolean.valueOf(auto)
        case "isReadOnly" => java.lang.Boolean.valueOf(readOnly)
        case "setAutoCommit" => record(s"auto=${args(0)}"); auto = args(0).asInstanceOf[Boolean]; null
        case "setReadOnly" => record(s"readOnly=${args(0)}"); readOnly = args(0).asInstanceOf[Boolean]; null
        case "createStatement" => statement
        case "commit" | "rollback" | "close" => record(name); null
        case _ => throw new UnsupportedOperationException(name)
    }
    val dataSource: DataSource = proxy(classOf[DataSource]) { (name, _) =>
      if name == "getConnection" then connection else throw new UnsupportedOperationException(name)
    }
    def scoped[A](f: Transactor.Session => A): A = Transactor(dataSource).read(f(Transactor.onConnection))

  def main(args: Array[String]): Unit =
    val committed = Recording()
    assert(committed.scoped(_.transact(7)) == 7)
    assert(committed.events.count(_ == "commit") == 1 && !committed.events.contains("rollback"))
    assert(committed.auto && !committed.readOnly)
    val dry = Recording()
    assert(dry.scoped(_.rollback(9)) == 9)
    assert(dry.events.count(_ == "rollback") == 1 && !dry.events.contains("commit"))
    val read = Recording()
    assert(Transactor(read.dataSource).readOnlyRollback(0)(11) == 11)
    assert(read.events.contains("SET TRANSACTION READ ONLY"))
    assert(read.events.contains("SET LOCAL statement_timeout = 1"))
    assert(read.auto && !read.readOnly && read.events.count(_ == "rollback") == 1)
    val failed = Recording()
    val original = new IllegalArgumentException("body")
    val caught = scala.util.Try(failed.scoped(_.transact(throw original))).failed.get
    assert(caught eq original)
    assert(failed.events.count(_ == "rollback") == 1 && failed.auto)
    val commitFailed = Recording()
    commitFailed.fail = Set("commit")
    assert(scala.util.Try(commitFailed.scoped(_.transact(1))).failed.get.getMessage == "commit")
    assert(commitFailed.events.contains("rollback") && commitFailed.auto)
    val rollbackFailed = Recording()
    rollbackFailed.fail = Set("rollback")
    assert(scala.util.Try(rollbackFailed.scoped(_.rollback(1))).failed.get.getMessage == "rollback")
    assert(!rollbackFailed.events.contains("auto=true") && rollbackFailed.events.contains("close"))
    val suppressed = Recording()
    suppressed.fail = Set("rollback")
    val primary = new IllegalStateException("primary")
    assert(scala.util.Try(suppressed.scoped(_.transact(throw primary))).failed.get eq primary)
    assert(primary.getSuppressed.exists(_.getMessage == "rollback"))
    val nested = Recording()
    nested.auto = false
    assert(scala.util.Try(nested.scoped(_.transact(1))).isFailure)
    assert(!nested.events.contains("commit") && !nested.events.contains("rollback"))
    val vacuum = Recording()
    assert(Transactor(vacuum.dataSource).autocommit(13) == 13)
    assert(!vacuum.events.contains("commit") && !vacuum.events.contains("rollback"))
    List("readOnly=true", "auto=false", "SET TRANSACTION READ ONLY", "SET LOCAL statement_timeout = 5").foreach { failure =>
      val setup = Recording()
      setup.fail = Set(failure)
      var bodyRan = false
      assert(scala.util.Try(Transactor(setup.dataSource).readOnlyRollback(5) { bodyRan = true; 1 }).failed.get.getMessage == failure)
      assert(!bodyRan && setup.auto && !setup.readOnly)
      assert(!setup.events.contains("commit"))
      assert(setup.events.contains("rollback") == (failure != "readOnly=true"))
    }
    List("readOnly=false", "auto=true").foreach { failure =>
      val restore = Recording()
      restore.fail = Set(failure)
      assert(scala.util.Try(restore.scoped(_.rollback(1))).failed.get.getMessage == failure)
      assert(restore.events.contains("close") && restore.events.count(_ == "rollback") == 1)
    }
