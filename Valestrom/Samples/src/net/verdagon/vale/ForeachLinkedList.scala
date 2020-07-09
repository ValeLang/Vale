package net.verdagon.vale

object ForeachLinkedList {
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
      |
      |struct MyList<T> rules(T Ref) {
      |  value T;
      |  next ^MyOption<^MyList<T>>;
      |}
      |
      |abstract fn forEach<F, T>(virtual opt &MyOption<MyList<T>>, func F) int;
      |fn forEach<F, T>(opt &MyNone<MyList<T>> impl MyOption<MyList<T>>, func F) int { 0 }
      |fn forEach<F, T>(opt &MySome<MyList<T>> impl MyOption<MyList<T>>, func F) int {
      |   forEach<F, T>(opt.value, func);
      |   = 0;
      |}
      |fn forEach<F, T>(list &MyList<T>, func F) int {
      |  func(list.value);
      |  forEach<F, T>(list.next, func);
      |  = 0;
      |}
      |
      |fn main() int {
      |  list = MyList<int>(10, MySome<^MyList<int>>(MyList<int>(20, MySome<^MyList<int>>(MyList<int>(30, MyNone<^MyList<int>>())))));
      |  forEach(&list, print);
      |  = 0;
      |}
    """.stripMargin
}
