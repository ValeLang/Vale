package net.verdagon.vale

import net.verdagon.vale.parser.VaryingP
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.{AddressMemberLookupTE, ConstructTE, FunctionCallTE, LetNormalTE, LocalLookupTE, MutateTE, ReferenceMemberLookupTE}
import net.verdagon.vale.templar.env.{AddressibleLocalVariableT, ReferenceLocalVariableT}
import net.verdagon.vale.templar.types._
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.templar.expression.LocalHelper
import net.verdagon.vale.templar.names.{ClosureParamNameT, CodeVarNameT, FullNameT, FunctionNameT, LambdaCitizenNameT, TemplarBlockResultVarNameT}

class ClosureTests extends FunSuite with Matchers {

  test("Addressibility") {
    def calc(
        selfBorrowed: IVariableUseCertainty,
        selfMoved: IVariableUseCertainty,
        selfMutated: IVariableUseCertainty,
        childBorrowed: IVariableUseCertainty,
        childMoved: IVariableUseCertainty,
        childMutated: IVariableUseCertainty) = {
      val addressibleIfMutable =
        LocalHelper.determineIfLocalIsAddressible(
          MutableT,
          LocalS(
            CodeVarNameS("x"), selfBorrowed, selfMoved, selfMutated, childBorrowed, childMoved, childMutated))
      val addressibleIfImmutable =
        LocalHelper.determineIfLocalIsAddressible(
          ImmutableT,
          LocalS(
            CodeVarNameS("x"), selfBorrowed, selfMoved, selfMutated, childBorrowed, childMoved, childMutated))
      (addressibleIfMutable, addressibleIfImmutable)
    }

    // If we don't do anything with the variable, it can be just a reference.
    calc(NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed) shouldEqual (false, false)

    // If we or our children only ever read, it can be just a reference.
    calc(Used, NotUsed, NotUsed,      NotUsed, NotUsed, NotUsed) shouldEqual (false, false)
    calc(NotUsed, NotUsed, NotUsed,   Used, NotUsed, NotUsed) shouldEqual (false, false)

    // If only we mutate it, it can be just a reference.
    calc(NotUsed, NotUsed, Used, NotUsed, NotUsed, NotUsed) shouldEqual (false, false)

    // Even if we're certain it's moved, it must be addressible.
    // Imagine:
    // fn main() int export {
    //   m = Marine();
    //   if (something) {
    //     something.consume(m);
    //   } else {
    //     otherthing.consume(m);
    //   }
    // }
    // (or, we can change it so we move it into the closure struct, but that
    // seems weird, i like thinking that closures only ever have borrows or
    // addressibles)
    // However, this doesnt apply to immutable, since move = copy.
    calc(NotUsed, NotUsed, NotUsed, NotUsed, Used, NotUsed) shouldEqual (true, false)

    // If we're certain children mutate it, it also has to be addressible.
    calc(NotUsed, NotUsed, NotUsed, NotUsed, NotUsed, Used) shouldEqual (true, true)
  }

  test("Captured own is borrow") {
    // Here, the scout determined that the closure is only ever borrowing
    // it (during the dereference to get its member) so templar doesn't put
    // an address into the closure, it instead puts a reference. Specifically,
    // a borrow reference (because why would we want to move this into the
    // closure struct?).
    // This means the closure struct contains a borrow reference. This means
    // the environment in the closure has to match this; the environment has
    // to have a borrow reference instead of an owning reference.

    val compile = RunCompilation.test(
      """
        |struct Marine {
        |  hp int;
        |}
        |fn main() int export {
        |  m = Marine(9);
        |  ret { m.hp }!();
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }

  test("Test closure's local variables") {
    val compile = RunCompilation.test("fn main() int export { x = 4; ret {x}(); }")
    val temputs = compile.expectTemputs()

    val main = temputs.lookupLambdaIn("main")
    Collector.only(main, {
      case LetNormalTE(
        ReferenceLocalVariableT(
          FullNameT(_, Vector(FunctionNameT("main", _, _), LambdaCitizenNameT(_), FunctionNameT("__call", _, _)), ClosureParamNameT()),
          FinalT,
          CoordT(ShareT, ReadonlyT, StructTT(FullNameT(_, Vector(FunctionNameT("main", Vector(), Vector())), LambdaCitizenNameT(_))))),
        _) =>
    })
    Collector.only(main, {
      case LetNormalTE(
        ReferenceLocalVariableT(
          FullNameT(_, Vector(FunctionNameT("main",_,_), LambdaCitizenNameT(_), FunctionNameT("__call",_,_)),TemplarBlockResultVarNameT(_)),
          FinalT,
          CoordT(ShareT,ReadonlyT, IntT.i32)),
        _) =>
    })
  }

  test("Test returning a nonmutable closured variable from the closure") {
    val compile = RunCompilation.test("fn main() int export { x = 4; ret {x}(); }")
    val temputs = compile.expectTemputs()

    // The struct should have an int x in it which is a reference type.
    // It's a reference because we know for sure that it's moved from our child,
    // which means we don't need to check afterwards, which means it doesn't need
    // to be boxed/addressible.
    val closuredVarsStruct =
      vassertSome(
        temputs.structs.find(struct => struct.fullName.last match { case l @ LambdaCitizenNameT(_) => true case _ => false }));
    val expectedMembers = Vector(StructMemberT(CodeVarNameT("x"), FinalT, ReferenceMemberTypeT(CoordT(ShareT, ReadonlyT, IntT.i32))));
    vassert(closuredVarsStruct.members == expectedMembers)

    val lambda = temputs.lookupLambdaIn("main")
    // Make sure we're doing a referencememberlookup, since it's a reference member
    // in the closure struct.
    Collector.only(lambda, {
      case ReferenceMemberLookupTE(_,_, FullNameT(_, _, CodeVarNameT("x")), _, _, _) =>
    })

    // Make sure there's a function that takes in the closured vars struct, and returns an int
    val lambdaCall =
      vassertSome(
        temputs.functions.find(func => {
          func.header.fullName.last match {
            case FunctionNameT("__call", _, _) => true
            case _ => false
          }
        }))
    lambdaCall.header.paramTypes.head match {
      case CoordT(ShareT, ReadonlyT, StructTT(FullNameT(_, Vector(FunctionNameT("main",Vector(),Vector())),LambdaCitizenNameT(_)))) =>
    }
    lambdaCall.header.returnType shouldEqual CoordT(ShareT, ReadonlyT, IntT.i32)

    // Make sure we make it with a function pointer and a constructed vars struct
    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case ConstructTE(StructTT(FullNameT(_, Vector(FunctionNameT("main",Vector(),Vector())),LambdaCitizenNameT(_))), _, _) =>
    })

    // Make sure we call the function somewhere
    Collector.onlyOf(main, classOf[FunctionCallTE])

    Collector.only(lambda, {
      case LocalLookupTE(_,ReferenceLocalVariableT(FullNameT(_, _,ClosureParamNameT()),FinalT,_),_, _) =>
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(4)
  }

  test("Mutates from inside a closure") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  x! = 4;
        |  { set x = x + 1; }!();
        |  ret x;
        |}
      """.stripMargin)
    val scoutput = compile.getScoutput().getOrDie()
    val temputs = compile.expectTemputs()
    // The struct should have an int x in it.
    val closuredVarsStruct = vassertSome(temputs.structs.find(struct => struct.fullName.last match { case l @ LambdaCitizenNameT(_) => true case _ => false }));
    val expectedMembers = Vector(StructMemberT(CodeVarNameT("x"), VaryingT, AddressMemberTypeT(CoordT(ShareT, ReadonlyT, IntT.i32))));
    vassert(closuredVarsStruct.members == expectedMembers)

    val lambda = temputs.lookupLambdaIn("main")
    Collector.only(lambda, {
      case MutateTE(
        AddressMemberLookupTE(_,_,FullNameT(_, Vector(FunctionNameT("main",Vector(),Vector()), LambdaCitizenNameT(_)),CodeVarNameT("x")),CoordT(ShareT,ReadonlyT, IntT.i32), _),
        _) =>
    })

    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case LetNormalTE(AddressibleLocalVariableT(_, VaryingT, _), _) =>
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

  test("Mutates from inside a closure inside a closure") {
    val compile = RunCompilation.test("fn main() int export { x! = 4; { { set x = x + 1; }!(); }!(); ret x; }")

    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

  test("Read from inside a closure inside a closure") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  x = 42;
        |  ret { { x }() }();
        |}
        |""".stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Mutable lambda") {
    val compile =
      RunCompilation.test(
        Tests.loadExpected("programs/lambdas/lambdamut.vale"))

    val temputs = compile.expectTemputs()
    val closureStruct =
      temputs.structs.find(struct => {
        struct.fullName.last match {
          case LambdaCitizenNameT(_) => true
          case _ => false
        }
      }).get
    vassert(closureStruct.mutability == MutableT)
    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }
}
