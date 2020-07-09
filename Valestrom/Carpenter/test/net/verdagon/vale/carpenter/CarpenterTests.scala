package net.verdagon.vale.carpenter

import org.scalatest.{FunSuite, Matchers}

class CarpenterTests extends FunSuite with Matchers {
//  test("Function param has virtual") {
//    val compile = new Compilation(
//      """
//        |interface I {}
//        |fn doThing(i: virtual I) {4}
//        |fn main() {3}
//      """.stripMargin)
//    compile.getTemputs()
//    val hinputs = compile.getHinputs()
//
//    vassert(temputs.getAllUserFunctions.size == 2)
//    vassert(temputs.lookupFunction("main").header.returnType == Coord(Share, Int2()))
//
//    val doThing = temputs.lookupFunction(Signature2("doThing", List(), List(Coord(Own, InterfaceRef2("I", List()))))).get
//    vassert(doThing.header.params(0).virtuality.get == Virtual2)
//  }
//
//  // There is no doAThing which takes in a MyInterface, so there should be no
//  // virtual shenanigans going on.
//  test("No virtual/override/abstract means no function families") {
//    val compile = new Compilation(
//      """
//        |interface MyInterface { }
//        |
//        |struct SomeStruct { }
//        |SomeStruct implements MyInterface;
//        |fn doAThing(a: SomeStruct) { }
//        |
//        |struct OtherStruct { }
//        |OtherStruct implements MyInterface;
//        |fn doAThing(b: OtherStruct) { }
//      """.stripMargin)
//    compile.getTemputs()
//    val hinputs = compile.getHinputs()
//    vassert(temputs.functionFamiliesByRootBanner.isEmpty)
//  }
//
//  // Make sure we generate a covariant family for doAThing.
//  test("Function with virtual makes a function family") {
//    val compile = new Compilation(
//      """
//        |interface MyInterface {
//        |  fn doAThing(i: virtual MyInterface):();
//        |}
//        |
//        |struct SomeStruct { }
//        |SomeStruct implements MyInterface;
//        |fn doAThing(a: SomeStruct for MyInterface):() { () }
//        |
//        |struct OtherStruct { }
//        |OtherStruct implements MyInterface;
//        |fn doAThing(b: OtherStruct for MyInterface):() { () }
//      """.stripMargin)
//    compile.getTemputs()
//    val hinputs = compile.getHinputs()
//    vassert(temputs.functionFamiliesByRootBanner.size == 1)
//    val family = temputs.functionFamiliesByRootBanner.values.head
//    vassert(family.rootBanner.paramTypes(0).referend == InterfaceRef2("MyInterface", List()))
//
//    family.memberSignaturesByVirtualRoots(List(StructRef2("OtherStruct", List()))).paramTypes(0).referend == StructRef2("OtherStruct", List());
//    family.memberSignaturesByVirtualRoots(List(StructRef2("SomeStruct", List()))).paramTypes(0).referend == StructRef2("SomeStruct", List());
//  }
//
//
//  test("Unrelated structs don't share function families") {
//    val compile = new Compilation(
//      """
//        |interface MyInterface<T> { }
//        |fn doThing<T>(x: virtual MyInterface<T>) {}
//        |
//        |struct MyStruct<T> { }
//        |MyStruct<T> implements MyInterface<T>;
//        |fn doThing<T>(x: MyStruct<T> for MyInterface<T>) {}
//        |
//        |
//        |interface OtherInterface<T> { }
//        |fn doThing<T>(x: virtual OtherInterface<T>) {}
//        |
//        |struct OtherStruct<T> { }
//        |OtherStruct<T> implements OtherInterface<T>;
//        |fn doThing<T>(x: OtherStruct<T> for MyInterface<T>) {}
//        |
//        |
//        |fn main() {
//        |  x = MyStruct<int>();
//        |  doThing(x);
//        |  y = OtherStruct<int>();
//        |  doThing(y);
//        |}
//      """.stripMargin)
//    compile.getTemputs()
//    val hinputs = compile.getHinputs()
//    // No merging should happen because MyStruct and OtherStruct are completely unrelated
//    vassert(temputs.functionFamiliesByRootBanner.size == 4)
//  }
//
//
//  test("Functions with same signatures for different ancestors") {
//    val compile = new Compilation(
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
//        |fn doThing<T>(x: MyStruct<T> for J<T>) {}
//        |
//        |fn main() {
//        |  x = MyStruct<int>();
//        |  doThing(x);
//        |}
//      """.stripMargin)
//    vfail("make this test!")
//    compile.getTemputs()
//    val hinputs = compile.getHinputs()
//    vassert(temputs.functionFamiliesByRootBanner.size == 4)
//  }
//
//
//  test("Virtual function is added to overridden families and gets its own family") {
//    val compile = new Compilation(
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
//    val hinputs = compile.getHinputs()
//    // same as the previous test, but all three are virtual. should still be merged.
//    vassert(temputs.functionFamiliesByRootBanner.size == 5)
//    vassert(hinputs.superFamilyRootBannersByRootBanner.size == 5)
//    vassert(hinputs.superFamilyRootBannersByRootBanner.values.toSet.size == 4)
//  }
//
//
//  test("Struct is stamped and function families created") {
//    val compile = new Compilation(
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
//    val hinputs = compile.getHinputs()
//    vassert(temputs.functionFamiliesByRootBanner.size == 7)
//    vassert(hinputs.superFamilyRootBannersByRootBanner.size == 7)
//    // This also tests the transitive super families, see SuperFamilyCarpenter
//    vassert(hinputs.superFamilyRootBannersByRootBanner.values.toSet.size == 2)
//  }
//
//  // Make sure it keeps doAThing and doBThing separate, even though they have the same signatures.
//  // This tripped up the superfamilycarpenter once when it forgot to compare the names.
//  test("Functions with same params but different name dont share families") {
//    val compile = new Compilation(
//      """
//        |interface MyInterface<T> { }
//        |struct MyStruct<T> { }
//        |MyStruct<T> implements MyInterface<T>;
//        |
//        |fn doAThing<T>(x: virtual T) {}
//        |fn doBThing<T>(x: virtual T) {}
//        |
//        |fn main() {
//        |  doAThing(MyStruct<int>());
//        |  doBThing(MyStruct<int>());
//        |}
//      """.stripMargin)
//    val hinputs = compile.getHinputs()
//    compile.getTemputs()
//  }

}
