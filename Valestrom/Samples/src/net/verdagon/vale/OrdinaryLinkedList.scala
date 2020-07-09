package net.verdagon.vale

object OrdinaryLinkedList {
  val code =
    """
      |interface MyOption imm { }
      |
      |struct MySome imm {
      |  value *MyList;
      |}
      |impl MySome for MyOption;
      |
      |struct MyNone imm { }
      |impl MyNone for MyOption;
      |
      |
      |struct MyList imm {
      |  value int;
      |  next *MyOption;
      |}
      |
      |fn printValues(list *MyList) void {
      |	 print(list.value);
      |	 printNextValue(list.next);
      |}
      |
      |fn printNextValue(virtual opt *MyOption) void { }
      |fn printNextValue(opt *MyNone impl MyOption) void { }
      |fn printNextValue(opt *MySome impl MyOption) void {
      |	 printValues(opt.value);
      |}
      |
      |
      |fn main() int {
      | 	list = MyList(10, MySome(MyList(20, MySome(MyList(30, MyNone())))));
      | 	printValues(list);
      | 	= 0;
      |}
    """.stripMargin
}
