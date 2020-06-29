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
      |abstract fn forEach<F, T>(virtual opt &MyOption<MyList<T>>, func F) *Int;
      |fn forEach<F, T>(opt &MyNone<MyList<T>> impl MyOption<MyList<T>>, func F) *Int { 0 }
      |fn forEach<F, T>(opt &MySome<MyList<T>> impl MyOption<MyList<T>>, func F) *Int {
      |   forEach<F, T>(opt.value, func);
      |   = 0;
      |}
      |fn forEach<F, T>(list &MyList<T>, func F) *Int {
      |  func(list.value);
      |  forEach<F, T>(list.next, func);
      |  = 0;
      |}
      |
      |fn main() *Int {
      |  list = MyList<*Int>(10, MySome<^MyList<*Int>>(MyList<*Int>(20, MySome<^MyList<*Int>>(MyList<*Int>(30, MyNone<^MyList<*Int>>())))));
      |  forEach(&list, print);
      |  = 0;
      |}
    """.stripMargin
}
