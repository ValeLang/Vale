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
      |  value *Int;
      |  next *MyOption;
      |}
      |
      |fn printValues(list *MyList) Void {
      |	 print(list.value);
      |	 printNextValue(list.next);
      |}
      |
      |fn printNextValue(virtual opt *MyOption) Void { }
      |fn printNextValue(opt *MyNone impl MyOption) Void { }
      |fn printNextValue(opt *MySome impl MyOption) Void {
      |	 printValues(opt.value);
      |}
      |
      |
      |fn main() Int {
      | 	list = MyList(10, MySome(MyList(20, MySome(MyList(30, MyNone())))));
      | 	printValues(list);
      | 	= 0;
      |}
    """.stripMargin
}
