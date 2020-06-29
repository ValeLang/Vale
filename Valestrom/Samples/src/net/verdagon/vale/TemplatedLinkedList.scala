package net.verdagon.vale

object TemplatedLinkedList {
  val code =
    """
      |interface MyOption<T> imm rules(T Ref) { }
      |
      |struct MySome<T> imm rules(T Ref) {
      |  value T;
      |}
      |impl<T> MySome<T> for MyOption<T>;
      |
      |struct MyNone<T> imm rules(T Ref) { }
      |impl<T> MyNone<T> for MyOption<T>;
      |
      |
      |struct MyList<T> imm rules(T Ref) {
      |  value T;
      |  next *MyOption<*MyList<T>>;
      |}
      |
      |fn printValues(list *MyList<*Int>) Void {
      |	 print(list.value);
      |	 printNextValue(list.next);
      |}
      |
      |fn printNextValue(virtual opt *MyOption<*MyList<*Int>>) Void { }
      |fn printNextValue(opt *MyNone<*MyList<*Int>> impl MyOption<*MyList<*Int>>) Void { }
      |fn printNextValue(opt *MySome<*MyList<*Int>> impl MyOption<*MyList<*Int>>) Void {
      |	 printValues(opt.value);
      |}
      |
      |
      |fn main() *Int {
      | 	list = MyList<*Int>(10, MySome<*MyList<*Int>>(MyList<*Int>(20, MySome<*MyList<*Int>>(MyList<*Int>(30, MyNone<*MyList<*Int>>())))));
      | 	printValues(list);
      | 	= 0;
      |}
    """.stripMargin
}
