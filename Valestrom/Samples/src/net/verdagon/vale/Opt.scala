package net.verdagon.vale

object Opt {
  val code =
    """interface Opt<T> rules(T Ref) { }
      |struct Some<T> rules(T Ref) { value T; }
      |impl<T> Some<T> for Opt<T>;
      |struct None<T> rules(T Ref) { }
      |impl<T> None<T> for Opt<T>;
      |
      |abstract fn empty?<T>(virtual opt &Opt<T>) Bool;
      |fn empty?<T>(opt &None<T> impl Opt<T>) Bool { true }
      |fn empty?<T>(opt &Some<T> impl Opt<T>) Bool { false }
      |
      |abstract fn get<T>(virtual opt Opt<T>) T;
      |fn get<T>(opt None<T> impl Opt<T>) T { panic() }
      |fn get<T>(opt Some<T> impl Opt<T>) T {
      |  Some<T>(value) = opt;
      |  = value;
      |}
      |
      |abstract fn get<T>(virtual opt &Opt<T>) &T;
      |fn get<T>(opt &None<T> impl Opt<T>) &T { panic() }
      |fn get<T>(opt &Some<T> impl Opt<T>) &T { opt.value }
      |
      |abstract fn getOr<T>(virtual opt &Opt<T>, default T) T;
      |fn getOr<T>(opt &None<T> impl Opt<T>, default T) T {
      |  default
      |}
      |fn getOr<T>(opt &Some<T> impl Opt<T>, default T) T {
      |  opt.value
      |}
      |
      |abstract fn map<T, R>(virtual opt &Opt<T>, func &IFunction1<mut, &T, R>) Opt<R>;
      |fn map<T, R>(opt &None<T> impl Opt<T>, func &IFunction1<mut, &T, R>) Opt<R> {
      |  None<R>()
      |}
      |fn map<T, R>(opt &Some<T> impl Opt<T>, func &IFunction1<mut, &T, R>) Opt<R> {
      |  Some<R>(func(opt.value))
      |}
      |
      |abstract fn getSize<T>(virtual opt &Opt<T>) *Int;
      |fn getSize<T>(opt &None<T> impl Opt<T>) *Int { 0 }
      |fn getSize<T>(opt &Some<T> impl Opt<T>) *Int { 1 }
      |
    """.stripMargin
}
