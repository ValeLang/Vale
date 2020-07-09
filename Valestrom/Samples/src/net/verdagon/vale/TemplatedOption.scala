package net.verdagon.vale

object TemplatedOption {
  val code =
    """
      |interface MyOption<T> rules(T Ref) { }
      |
      |struct MySome<T> rules(T Ref) {
      |  value T;
      |}
      |impl<T> MySome<T> for MyOption<T>;
      |
      |struct MyNone<T> rules(T Ref) { }
      |impl<T> MyNone<T> for MyOption<T>;
      |
      |abstract fn getSize<T>(virtual opt &MyOption<T>) int;
      |fn getSize<T>(opt &MyNone<T> impl MyOption<T>) int { 0 }
      |fn getSize<T>(opt &MySome<T> impl MyOption<T>) int { 1 }
      |
      |fn main() int {
      |  myOpt MyOption<int> = MySome<int>(4);
      |  = getSize(&myOpt);
      |}
    """.stripMargin
}
