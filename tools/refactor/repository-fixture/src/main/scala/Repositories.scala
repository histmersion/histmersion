//> using scala 3.10.0
package visibility.feature {
  object SafeRepo:
    def value: Int = 1
  object ExposedRepo:
    def value: Int = 2
  object Service:
    import SafeRepo.{value as renamed}
    def value: Int = renamed
}
package visibility.feature.child {
  object Child:
    val call: () => Int = () => visibility.feature.SafeRepo.value
}
package visibility.other {
  import visibility.feature.{ExposedRepo as Store}
  object Consumer:
    def value: Int = Store.value
  object SafeRepo:
    def value: Int = 3
}
