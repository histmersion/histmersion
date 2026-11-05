package canonicalfixture

object Original:
  type Argument = _root_.canonicalfixture.Canonical.Argument
  val Argument: _root_.canonicalfixture.Canonical.Argument.type = _root_.canonicalfixture.Canonical.Argument
  type Error = _root_.canonicalfixture.Canonical.Error
  val Error: _root_.canonicalfixture.Canonical.Error.type = _root_.canonicalfixture.Canonical.Error

def result(f: () => Either[_root_.canonicalfixture.Canonical.Error, Int]): Either[_root_.canonicalfixture.Canonical.Error, Int] = f()
