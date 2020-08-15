package net.verdagon.vale.templar

import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.Set

class VirtualTests extends FunSuite with Matchers {
  // TODO: pull all of the templar specific stuff out, the unit test-y stuff

//  test("Simple program containing a virtual function") {
//    val compile = Compilation(
//      """
//        |interface I {}
//        |fn doThing(i: virtual I) {4}
//        |fn main() {3}
//      """.stripMargin)
//    compile.getTemputs()
//
//    vassert(temputs.getAllUserFunctions.size == 2)
//    vassert(temputs.lookupFunction("main").header.returnType == Coord(Share, Int2()))
//
//    val doThing = temputs.lookupFunction(Signature2("doThing", List(), List(Coord(Own, InterfaceRef2("I", List()))))).get
//    vassert(doThing.header.params(0).virtuality.get == Virtual2)
//  }
//
//  test("Simple program containing a virtual function taking an interface") {
//    val compile = Compilation(
//      """
//        |interface I {}
//        |struct S {}
//        |S implements I;
//        |fn doThing(i: virtual I) {4}
//        |fn main() {3}
//      """.stripMargin)
//    compile.getTemputs()
//
//    vassert(temputs.getAllUserFunctions.size == 3) // including constructor
//    vassert(temputs.lookupFunction("main").header.returnType == Coord(Share, Int2()))
//
//    val doThing = temputs.lookupFunction(Signature2("doThing", List(), List(Coord(Own, InterfaceRef2("I", List()))))).get
//    vassert(doThing.header.params(0).virtuality.get == Virtual2)
//  }
//
//  test("Simple override") {
//    val compile = Compilation(
//      """
//        |interface I {}
//        |struct S {}
//        |S implements I;
//        |fn doThing(i: virtual &I) {4}
//        |fn doThing(s: &S for I) {4}
//        |fn main() {3}
//      """.stripMargin)
//    compile.getTemputs()
//
//    vassert(temputs.getAllUserFunctions.size == 3) // including constructor
//    vassert(temputs.lookupFunction("main").header.returnType == Coord(Share, Int2()))
//
//    val doThingI = temputs.lookupFunction(Signature2("doThing", List(), List(Coord(Own, InterfaceRef2("I", List()))))).get
//    vassert(doThingI.header.params(0).virtuality.get == Virtual2)
//
//    val doThingS = temputs.lookupFunction(Signature2("doThing", List(), List(Coord(Own, StructRef2("S", List()))))).get
//    vassert(doThingS.header.params(0).virtuality.get == Override2(null))
//
//    vassert(temputs.functionFamiliesByRootBanner.size == 1)
//    vassert(temputs.functionFamiliesByRootBanner.head._2.memberSignaturesByVirtualRoots.size == 2)
//  }
//
//  test("Two functions overriding another function") {
//    val compile = Compilation(
//      """
//        |interface I {}
//        |struct S1 {}
//        |S1 implements I;
//        |struct S2 {}
//        |S2 implements I;
//        |fn doThing(i: virtual I) {4}
//        |fn doThing(s: S1 for I) {5}
//        |fn doThing(s: S2 for I) {6}
//        |fn main() {3}
//      """.stripMargin)
//    compile.getTemputs()
//
//    vassert(temputs.functionFamiliesByRootBanner.size == 1)
//    vassert(temputs.functionFamiliesByRootBanner.head._2.memberSignaturesByVirtualRoots.size == 3)
//  }
//
//  test("Function taking an interface overriding another function") {
//    val compile = Compilation(
//      """
//        |interface I1 {}
//        |interface I2 {}
//        |I2 implements I1;
//        |struct S {}
//        |S implements I2;
//        |fn doThing(i: virtual I1) {4}
//        |fn doThing(override i: I2) {5}
//        |fn doThing(override s: S) {6}
//        |fn main() {3}
//      """.stripMargin)
//    vfail("what would this test become in the new world order")
//    compile.getTemputs()
//
//    vassert(temputs.lookupInterface(InterfaceRef2("I2", List())).superInterfaces.nonEmpty)
//
//    vassert(temputs.functionFamiliesByRootBanner.size == 1)
//
//    val doThingI1Root =
//      temputs.functionFamiliesByRootBanner.keys.find({
//        case FunctionBanner2(_, "doThing", List(), List(Parameter2("i", Some(Virtual2), Coord(Own, InterfaceRef2("I1", List()))))) => true
//      }).get;
//    val doThingI1Family = temputs.functionFamiliesByRootBanner(doThingI1Root);
//    vassert(doThingI1Family.memberSignaturesByVirtualRoots.size == 3)
//  }
//
//  test("Function taking an interface overriding another function with virtual") {
//    val compile = Compilation(
//      """
//        |interface I1 {}
//        |interface I2 {}
//        |I2 implements I1;
//        |struct S {}
//        |S implements I2;
//        |fn doThing(i: virtual I1) {4}
//        |fn doThing(i: virtual I2) {5}
//        |fn doThing(override s: S) {6}
//        |fn main() {3}
//      """.stripMargin)
//    vfail("what would this test become in the new world order")
//    compile.getTemputs()
//
//    vassert(temputs.lookupInterface(InterfaceRef2("I2", List())).superInterfaces.nonEmpty)
//
//    vassert(temputs.functionFamiliesByRootBanner.size == 2)
//
//    val doThingI1Root =
//      temputs.functionFamiliesByRootBanner.keys.collect({
//        case b@FunctionBanner2(_, "doThing", List(), List(Parameter2("i", Some(Virtual2), Coord(Own, InterfaceRef2("I1", List()))))) => b
//      }).head;
//    val doThingI1Family = temputs.functionFamiliesByRootBanner(doThingI1Root);
//    vassert(doThingI1Family.memberSignaturesByVirtualRoots.size == 3)
//
//    val doThingI2Root =
//      temputs.functionFamiliesByRootBanner.keys.find({
//        case FunctionBanner2(_, "doThing", List(), List(Parameter2("i", Some(Virtual2), Coord(Own, InterfaceRef2("I2", List()))))) => true
//      }).get;
//    val doThingI2Family = temputs.functionFamiliesByRootBanner(doThingI2Root);
//    vassert(doThingI2Family.memberSignaturesByVirtualRoots.size == 2)
//  }
//
//  test("Stamps ancestor structs when we declare a child struct") {
//    val compile = Compilation(
//      """
//        |interface I<T> { }
//        |fn dance<T>(i: virtual I<T>) {
//        |   print(1);
//        |}
//        |
//        |struct SA<T> { }
//        |SA<T> implements I<T>;
//        |fn dance<T>(a: SA<T> for I<T>) {
//        |   print(2);
//        |}
//        |
//        |fn main() {
//        |  dance(SA<int>());
//        |}
//        |
//        |struct SB<T> { }
//        |SB<T> implements I<T>;
//        |fn dance<T>(b: SB<T> for I<T>) {
//        |   print(3);
//        |}
//        |
//        |fn thing() {
//        |  x = SB<int>();
//        |}
//      """.stripMargin)
//    vfail("what would this test be testing")
//    compile.getTemputs()
//
//    vassert(temputs.functionFamiliesByRootBanner.size == 1)
//    val family = temputs.functionFamiliesByRootBanner.head._2;
//    vassert(family.memberSignaturesByVirtualRoots.size == 3);
//    vassert(family.memberSignaturesByVirtualRoots.values.toList.contains(
//      Signature2("dance", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))), List(Coord(Own, InterfaceRef2("I", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))));
//    vassert(family.memberSignaturesByVirtualRoots.values.toList.contains(
//      Signature2("dance", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))), List(Coord(Own, StructRef2("SA", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))));
//    vassert(family.memberSignaturesByVirtualRoots.values.toList.contains(
//      Signature2("dance", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))), List(Coord(Own, StructRef2("SB", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))));
//  }
//
//  test("Calls an overriding function") {
//    val compile = Compilation(
//      """
//        |interface I<T> { }
//        |fn dance<T>(i: virtual I<T>) {
//        |   print(1);
//        |}
//        |
//        |struct SA<T> { }
//        |SA<T> implements I<T>;
//        |fn dance<T>(a: SA<T> for I<T>) {
//        |   print(2);
//        |}
//        |
//        |struct SB<T> { }
//        |SB<T> implements I<T>;
//        |fn dance<T>(b: SB<T> for I<T>) {
//        |   print(3);
//        |}
//        |
//        |fn main() {
//        |  x = SB<int>();
//        |  dance(SA<int>());
//        |}
//      """.stripMargin)
//    compile.getTemputs()
//
//    vassert(temputs.functionFamiliesByRootBanner.size == 1)
//    val family = temputs.functionFamiliesByRootBanner.head._2;
//    vassert(family.memberSignaturesByVirtualRoots.size == 3);
//    vassert(family.memberSignaturesByVirtualRoots.values.toList.contains(
//      Signature2("dance", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))), List(Coord(Own, InterfaceRef2("I", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))));
//    vassert(family.memberSignaturesByVirtualRoots.values.toList.contains(
//      Signature2("dance", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))), List(Coord(Own, StructRef2("SA", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))));
//    vassert(family.memberSignaturesByVirtualRoots.values.toList.contains(
//      Signature2("dance", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))), List(Coord(Own, StructRef2("SB", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))));
//  }
//
//  // When we call a function with a virtual parameter, try stamping for all ancestors in its
//  // place.
//  // We're stamping all ancestors, and all ancestors have virtual.
//  // Virtual starts a function family.
//  // So, this checks that it and its three ancestors are all stamped and all get their own
//  // function families.
//  test("Stamp multiple function families") {
//    val compile = Compilation(
//      """
//        |interface I<T> { }
//        |
//        |interface J<T> { }
//        |J<T> implements I<T>;
//        |
//        |interface K<T> { }
//        |K<T> implements J<T>;
//        |
//        |struct MyStruct<T> { }
//        |MyStruct<T> implements K<T>;
//        |
//        |fn doThing<T>(x: virtual T) {}
//        |
//        |fn main() {
//        |  x = MyStruct<int>();
//        |  doThing(x);
//        |}
//      """.stripMargin)
//    compile.getTemputs()
//
//    println(temputs.functionFamiliesByRootBanner.size)
//    temputs.functionFamiliesByRootBanner.foreach(println)
//
//    vassert(temputs.functionFamiliesByRootBanner.size == 4)
//
//    val getParamArgTypeByVirtualRoot =
//      (family: FunctionFamily) => {
//        family.memberSignaturesByVirtualRoots.collect({
//          case (List(virtualRoot: CitizenRef2), Signature2(_, _, List(Coord(_, param: CitizenRef2)))) => (virtualRoot.humanName -> param.humanName)
//        })
//      };
//
//    val doThingFamilyRootForI =
//      temputs.functionFamiliesByRootBanner.keys.collect({
//        case b@FunctionBanner2(_, "doThing", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Own, InterfaceRef2("I", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))), List(Parameter2("x", Some(Virtual2), Coord(Own, InterfaceRef2("I", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))) => b
//      }).head;
//    val doThingFamilyForI = temputs.functionFamiliesByRootBanner(doThingFamilyRootForI);
//    vassert(getParamArgTypeByVirtualRoot(doThingFamilyForI) == Map("I" -> "I", "J" -> "J", "K" -> "K", "MyStruct" -> "MyStruct"))
//
//    val doThingFamilyRootForJ =
//      temputs.functionFamiliesByRootBanner.keys.collect({
//        case b@FunctionBanner2(_, "doThing", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Own, InterfaceRef2("J", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))), List(Parameter2("x", Some(Virtual2), Coord(Own, InterfaceRef2("J", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))) => b
//      }).head;
//    val doThingFamilyForJ = temputs.functionFamiliesByRootBanner(doThingFamilyRootForJ);
//    vassert(getParamArgTypeByVirtualRoot(doThingFamilyForJ) == Map("J" -> "J", "K" -> "K", "MyStruct" -> "MyStruct"))
//
//    val doThingFamilyRootForK =
//      temputs.functionFamiliesByRootBanner.keys.collect({
//        case b@FunctionBanner2(_, "doThing", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Own, InterfaceRef2("K", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))), List(Parameter2("x", Some(Virtual2), Coord(Own, InterfaceRef2("K", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))) => b
//      }).head;
//    val doThingFamilyForK = temputs.functionFamiliesByRootBanner(doThingFamilyRootForK);
//    vassert(getParamArgTypeByVirtualRoot(doThingFamilyForK) == Map("K" -> "K", "MyStruct" -> "MyStruct"))
//
//    val doThingFamilyRootForMyStruct =
//      temputs.functionFamiliesByRootBanner.keys.collect({
//        case b@FunctionBanner2(_, "doThing", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Own, StructRef2("MyStruct", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))), List(Parameter2("x", Some(Virtual2), Coord(Own, StructRef2("MyStruct", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))) => b
//      }).head;
//    val doThingFamilyForMyStruct =
//      temputs.functionFamiliesByRootBanner(doThingFamilyRootForMyStruct);
//    vassert(getParamArgTypeByVirtualRoot(doThingFamilyForMyStruct) == Map("MyStruct" -> "MyStruct"))
//  }
//
//  // We manifest a doThing<int>, which takes in an I<int>.
//  // If you thought it should spawn a doThing(:MyStruct<int>), youre wrong; the function
//  // specifically says its parameter is an I<T>, not a MyStruct<T>.
//  // There should never be a doThing(:MyStruct<int>), doThing(:K<int>), or doThing(:J<int>).
//  // That said, we can still pass a MyStruct<int> arg to an I<int> parameter.
//  // This should only spawn that one function family.
//  // However, each struct is required to have a vtable entry for that function... they'll all
//  // point to the original doThing<int>(:I<int>).
//  test("Stamps virtual functions on a templated interface") {
//    val compile = Compilation(
//      """
//        |interface I<T> { }
//        |
//        |interface J<T> { }
//        |J<T> implements I<T>;
//        |
//        |interface K<T> { }
//        |K<T> implements J<T>;
//        |
//        |struct MyStruct<T> { }
//        |MyStruct<T> implements K<T>;
//        |
//        |fn doThing<T>(x: virtual I<T>) {}
//        |
//        |fn main() {
//        |  x = MyStruct<int>();
//        |  doThing(x);
//        |}
//      """.stripMargin)
//    compile.getTemputs()
//
//    println(temputs.functionFamiliesByRootBanner.size)
//    temputs.functionFamiliesByRootBanner.foreach(println)
//
//    vassert(temputs.functionFamiliesByRootBanner.size == 1)
//
//    val doThingFamilyRoot =
//      temputs.functionFamiliesByRootBanner.keys.find({
//        case FunctionBanner2(_, "doThing", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))), List(Parameter2("x", Some(Virtual2), Coord(Own, InterfaceRef2("I", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))) => true
//      }).get;
//    val doThingFamily =
//      temputs.functionFamiliesByRootBanner(doThingFamilyRoot);
//    vassert(doThingFamily.memberSignaturesByVirtualRoots.size == 4)
//
//    vassert(
//      doThingFamily.memberSignaturesByVirtualRoots.values.toSet ==
//          Set(Signature2("doThing", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))), List(Coord(Own, InterfaceRef2("I", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))))
//  }
//
//
//  test("Virtual creates function family roots") {
//    val compile = Compilation(
//      """
//        |interface I<T> { }
//        |interface J<T> { }
//        |
//        |struct MyStruct<T> { }
//        |MyStruct<T> implements I<T>;
//        |MyStruct<T> implements J<T>;
//        |
//        |fn doThing<T>(x: virtual I<T>) {}
//        |fn doThing<T>(x: virtual J<T>) {}
//        |fn doThing<T>(x: virtual MyStruct<T>) {}
//        |
//        |fn main() {
//        |  x = MyStruct<int>();
//        |  doThing(x);
//        |}
//      """.stripMargin)
//    compile.getTemputs()
//
//    vassert(temputs.functionFamiliesByRootBanner.size == 3)
//    // See the next test, which does the same thing but with override.
//    // Also, even though theres three function families, the SuperFamilyCarpenter
//    // will merge the MyStruct<T> families into the others.
//  }
//
//  // Should only be two function families.
//  test("Override doesnt make a function family root") {
//    val compile = Compilation(
//      """
//        |interface I<T> { }
//        |interface J<T> { }
//        |
//        |struct MyStruct<T> { }
//        |MyStruct<T> implements I<T>;
//        |MyStruct<T> implements J<T>;
//        |
//        |fn doThing<T>(x: virtual I<T>) {}
//        |fn doThing<T>(x: virtual J<T>) {}
//        |fn doThing<T>(x: MyStruct<T> for I<T>) {}
//        |
//        |fn main() {
//        |  x = MyStruct<int>();
//        |  doThing(x);
//        |}
//      """.stripMargin)
//    vfail("what would this test become in the new world order")
//    compile.getTemputs()
//
//    vassert(temputs.functionFamiliesByRootBanner.size == 2)
//
//    val getParamArgTypeByVirtualRoot =
//      (family: FunctionFamily) => {
//        family.memberSignaturesByVirtualRoots.collect({
//          case (List(virtualRoot: CitizenRef2), Signature2(_, _, List(Coord(_, param: CitizenRef2)))) => (virtualRoot.humanName -> param.humanName)
//        })
//      };
//
//    val doThingFamilyRootForI =
//      temputs.functionFamiliesByRootBanner.keys.collect({
//        case b@FunctionBanner2(_, "doThing", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))), List(Parameter2("x", Some(Virtual2), Coord(Own, InterfaceRef2("I", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))) => b
//      }).head;
//    val doThingFamilyForI = temputs.functionFamiliesByRootBanner(doThingFamilyRootForI);
//    vassert(getParamArgTypeByVirtualRoot(doThingFamilyForI) ==
//        Map("I" -> "I", "MyStruct" -> "MyStruct"));
//
//    val doThingFamilyRootForJ =
//      temputs.functionFamiliesByRootBanner.keys.collect({
//        case b@FunctionBanner2(_, "doThing", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))), List(Parameter2("x", Some(Virtual2), Coord(Own, InterfaceRef2("J", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))) => b
//      }).head;
//    val doThingFamilyForJ = temputs.functionFamiliesByRootBanner(doThingFamilyRootForJ);
//    vassert(getParamArgTypeByVirtualRoot(doThingFamilyForJ) ==
//        Map("J" -> "J", "MyStruct" -> "MyStruct"));
//  }
//
//
//  // These are two completely different types, so shouldnt share a family.
//  // There should be two families.
//  // This is a case where two functions with the same name+params return
//  // something different; theyre only distinguished by their template args.
//
//  test("Functions with same signatures but different template params make different families") {
//    val compile = Compilation(
//      """
//        |interface MyInterface<T> { }
//        |fn doThing<T>(x: virtual MyInterface<T>) {}
//        |
//        |struct MyStruct<T> { }
//        |MyStruct<T> implements MyInterface<T>;
//        |fn doThing<T>(x: MyStruct<T> for MyInterface<T>) {}
//        |
//        |fn main() {
//        |  x = MyStruct<int>();
//        |  y = MyStruct<Str>();
//        |  doThing(x);
//        |  doThing(y);
//        |}
//      """.stripMargin)
//    compile.getTemputs()
//
//    println(temputs.functionFamiliesByRootBanner);
//    vassert(temputs.functionFamiliesByRootBanner.size == 2)
//  }
//
//  test("Stamps different families for different template args") {
//    val compile = Compilation(
//      """
//        |interface MyInterface<T> { }
//        |abstract fn doThing<T>(x: virtual MyInterface<T>)Void;
//        |
//        |struct MyStruct<T> { }
//        |MyStruct<T> implements MyInterface<T>;
//        |fn doThing<T>(x: MyStruct<T> for MyInterface<T>) {}
//        |
//        |fn main() {
//        |  x = MyStruct<int>();
//        |  y = MyStruct<Str>();
//        |  doThing(x);
//        |  doThing(y);
//        |}
//      """.stripMargin)
//    compile.getTemputs()
//
//    val doThingFamilyRootForI =
//      temputs.functionFamiliesByRootBanner.keys.collect({
//        case b@FunctionBanner2(_, "doThing", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))), List(Parameter2("x", Some(Virtual2), Coord(Own, InterfaceRef2("MyInterface", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2()))))))))) => b
//      }).head;
//    val doThingFamilyForI = temputs.functionFamiliesByRootBanner(doThingFamilyRootForI);
//    vassert(doThingFamilyForI.memberSignaturesByVirtualRoots.size == 2)
//
//    temputs.functions.collectFirst({
//      case Function2(
//        FunctionHeader2(
//          "doThing", 0, true, false, true,
//          List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))),
//          List(
//            Parameter2("x", Some(Virtual2), Coord(Own, InterfaceRef2("MyInterface", List(CoercedFinalTemplateArg2(ReferenceTemplata(Coord(Share, Int2())))))))),
//          Coord(Share, Void2()),
//          _),
//        _,
//        _) => {}
//    }).get
//
//    vassert(temputs.all({
//      case f@FunctionHeader2("doThing", _, _, _, true, _, _, _, _) => f
//    }).size == 4)
//  }
}
