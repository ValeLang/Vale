package dev.vale.simplifying

import dev.vale.finalast.{ArgumentH, ArrayCapacityH, ArrayLengthH, AsSubtypeH, BoolH, BorrowToWeakH, BreakH, CallH, ConsecutorH, ConstantBoolH, ConstantF64H, ConstantIntH, ConstantStrH, ConstantVoidH, DestroyImmRuntimeSizedArrayH, DestroyMutRuntimeSizedArrayH, DestroyStaticSizedArrayIntoFunctionH, DiscardH, ExpressionH, ExternCallH, Final, IfH, InlineH, InterfaceCallH, InterfaceRefH, InterfaceToInterfaceUpcastH, IsSameInstanceH, KindH, LockWeakH, NeverH, NewArrayFromValuesH, NewImmRuntimeSizedArrayH, NewMutRuntimeSizedArrayH, NewStructH, PopRuntimeSizedArrayH, PushRuntimeSizedArrayH, ReferenceH, ReturnH, ShareH, StackifyH, StaticArrayFromCallableH, StructRefH, StructToInterfaceUpcastH, UnstackifyH, VariableIdH, VoidH, WhileH}
import dev.vale.typing.Hinputs
import dev.vale.typing.ast.{AddressMemberLookupTE, ArgLookupTE, ArrayLengthTE, AsSubtypeTE, BlockTE, BorrowToWeakTE, BreakTE, ConsecutorTE, ConstantBoolTE, ConstantFloatTE, ConstantIntTE, ConstantStrTE, ConstructTE, DeferTE, DestroyImmRuntimeSizedArrayTE, DestroyMutRuntimeSizedArrayTE, DestroyStaticSizedArrayIntoFunctionTE, DestroyStaticSizedArrayIntoLocalsTE, DestroyTE, DiscardTE, ExpressionT, ExternFunctionCallTE, FunctionCallTE, FunctionHeaderT, IfTE, InterfaceFunctionCallTE, InterfaceToInterfaceUpcastTE, IsSameInstanceTE, LetAndLendTE, LetNormalTE, LocalLookupTE, LockWeakTE, MutateTE, NewImmRuntimeSizedArrayTE, NewMutRuntimeSizedArrayTE, PopRuntimeSizedArrayTE, PrototypeT, PushRuntimeSizedArrayTE, ReferenceExpressionTE, ReturnTE, RuntimeSizedArrayCapacityTE, SoftLoadTE, StaticArrayFromCallableTE, StaticArrayFromValuesTE, StructToInterfaceUpcastTE, ReinterpretTE, TupleTE, UnletTE, VoidLiteralTE, WhileTE}
import dev.vale.typing.env.AddressibleLocalVariableT
import dev.vale.typing.types.{CoordT, InterfaceTT, NeverT, StructTT}
import dev.vale.{vassert, vcurious, vfail}
import dev.vale.{finalast => m}
import dev.vale.{finalast => m}
import dev.vale.finalast._
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.types._

class ExpressionHammer(
    typeHammer: TypeHammer,
    nameHammer: NameHammer,
    structHammer: StructHammer,
    functionHammer: FunctionHammer) {
  val blockHammer = new BlockHammer(this)
  val loadHammer = new LoadHammer(typeHammer, nameHammer, structHammer, this)
  val letHammer = new LetHammer(typeHammer, nameHammer, structHammer, this, loadHammer)
  val mutateHammer = new MutateHammer(typeHammer, nameHammer, structHammer, this)

  // stackHeight is the number of locals that have been declared in ancestor
  // blocks and previously in this block. It's used to figure out the index of
  // a newly declared local.
  // Returns:
  // - result register id
  // - deferred expressions, to move to after the enclosing call. head is put first after call.
  def translate(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      expr2: ExpressionT
  ): (ExpressionH[KindH], Vector[ExpressionT]) = {
    expr2 match {
      case ConstantIntTE(value, bits) => {
        (ConstantIntH(value, bits), Vector.empty)
      }
      case VoidLiteralTE() => {
        val constructH = ConstantVoidH()
        (constructH, Vector.empty)
      }
      case ConstantStrTE(value) => {
        (ConstantStrH(value), Vector.empty)
      }
      case ConstantFloatTE(value) => {
        (ConstantF64H(value), Vector.empty)
      }
      case ConstantBoolTE(value) => {
        (ConstantBoolH(value), Vector.empty)
      }
      case let2 @ LetNormalTE(_, _) => {
        val letH =
          letHammer.translateLet(hinputs, hamuts, currentFunctionHeader, locals, let2)
        (letH, Vector.empty)
      }
      case let2 @ LetAndLendTE(_, _, _) => {
        val borrowAccess =
          letHammer.translateLetAndPoint(hinputs, hamuts, currentFunctionHeader, locals, let2)
        (borrowAccess, Vector.empty)
      }
      case des2 @ DestroyTE(_, _, _) => {
        val destroyH =
            letHammer.translateDestroy(hinputs, hamuts, currentFunctionHeader, locals, des2)
        // Compiler destructures put things in local variables (even though hammer itself
        // uses registers internally to make that happen).
        // Since all the members landed in locals, we still need something to return, so we
        // return a void.
        (destroyH, Vector.empty)
      }
      case des2 @ DestroyStaticSizedArrayIntoLocalsTE(_, _, _) => {
        val destructureH =
            letHammer.translateDestructureStaticSizedArray(hinputs, hamuts, currentFunctionHeader, locals, des2)
        // Compiler destructures put things in local variables (even though hammer itself
        // uses registers internally to make that happen).
        // Since all the members landed in locals, we still need something to return, so we
        // return a void.
        (destructureH, Vector.empty)
      }
      case unlet2 @ UnletTE(_) => {
        val valueAccess =
          letHammer.translateUnlet(
            hinputs, hamuts, currentFunctionHeader, locals, unlet2)
        (valueAccess, Vector.empty)
      }
      case mutate2 @ MutateTE(_, _) => {
        val newEmptyPackStructNodeHE =
          mutateHammer.translateMutate(hinputs, hamuts, currentFunctionHeader, locals, mutate2)
        (newEmptyPackStructNodeHE, Vector.empty)
      }
      case b @ BlockTE(_) => {
        val blockH =
          blockHammer.translateBlock(hinputs, hamuts, currentFunctionHeader, locals, b)
        (blockH, Vector.empty)
      }
      case call2 @ FunctionCallTE(callableExpr, args) => {
        val access =
          translateFunctionPointerCall(
            hinputs, hamuts, currentFunctionHeader, locals, callableExpr, args, call2.result.reference)
        (access, Vector.empty)
      }

      case InterfaceFunctionCallTE(superFunctionHeader, resultType2, argsExprs2) => {
        val access =
          translateInterfaceFunctionCall(
            hinputs, hamuts, currentFunctionHeader, locals, superFunctionHeader, resultType2, argsExprs2)
        (access, Vector.empty)
      }

      case ConsecutorTE(exprsTE) => {
        // If there's an expression returning a Never, then remove all the expressions after that.
        // See BRCOBS.
        val exprsHE =
          exprsTE.foldLeft(Vector[ExpressionH[KindH]]())({
            case (previousHE, nextTE) => {
              previousHE.lastOption.map(_.resultType.kind) match {
                case Some(NeverH(_)) => previousHE
                case _ => {
                  val (nextHE, nextDeferreds) =
                    translate(hinputs, hamuts, currentFunctionHeader, locals, nextTE);
                  val nextExprWithDeferredsHE =
                    translateDeferreds(
                      hinputs, hamuts, currentFunctionHeader, locals, nextHE, nextDeferreds)
                  previousHE :+ nextExprWithDeferredsHE
                }
              }
            }
          })
        exprsHE.lastOption.map(_.resultType.kind) match {
          case Some(NeverH(_)) => {
            return (Hammer.consecrash(locals, exprsHE), Vector.empty)
          }
          case _ =>
        }

        (Hammer.consecutive(exprsHE), Vector.empty)
      }

      case ArrayLengthTE(arrayExpr2) => {
        val (resultHE, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

        val lengthResultNode = ArrayLengthH(resultHE);

        val arrayLengthAndDeferredsExprH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, lengthResultNode, deferreds)

        (arrayLengthAndDeferredsExprH, Vector.empty)
      }

      case RuntimeSizedArrayCapacityTE(arrayExpr2) => {
        val (resultHE, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

        val lengthResultNode = ArrayCapacityH(resultHE);

        val arrayLengthAndDeferredsExprH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, lengthResultNode, deferreds)

        (arrayLengthAndDeferredsExprH, Vector.empty)
      }

      case LockWeakTE(innerExpr2, resultOptBorrowType2, someConstructor, noneConstructor) => {
        val (resultHE, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr2);
        val (resultOptBorrowTypeH) =
          typeHammer.translateReference(hinputs, hamuts, resultOptBorrowType2)

        val someConstructorH =
          functionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, someConstructor);
        val noneConstructorH =
          functionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, noneConstructor);

        val resultNode =
          LockWeakH(
            resultHE,
            resultOptBorrowTypeH.expectInterfaceReference(),
            someConstructorH.prototype,
            noneConstructorH.prototype);
        (resultNode, deferreds)
      }

      case TupleTE(exprs, resultType) => {
        val (resultsHE, deferreds) =
          translateExpressionsUntilNever(
            hinputs, hamuts, currentFunctionHeader, locals, exprs);
        // Don't evaluate anything that can't ever be run, see BRCOBS
        resultsHE.lastOption.map(_.resultType.kind) match {
          case Some(NeverH(_)) => {
            return (Hammer.consecrash(locals, resultsHE), Vector())
          }
          case _ =>
        }

        val resultStructT = resultType.kind match { case s @ StructTT(_) => s }
        val (underlyingStructRefH) =
          structHammer.translateStructRef(hinputs, hamuts, resultStructT);
        val (resultReference) =
          typeHammer.translateReference(hinputs, hamuts, resultType)
        vassert(resultReference.kind == underlyingStructRefH)

        val structDefH = hamuts.structDefsByRefT(resultStructT)
        vassert(resultsHE.size == structDefH.members.size)
        val newStructNode =
          NewStructH(
            resultsHE,
            structDefH.members.map(_.name),
            resultReference.expectStructReference())
        // Export locals from inside the pack

        val newStructAndDeferredsExprH =
            translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, newStructNode, deferreds)

        (newStructAndDeferredsExprH, Vector.empty)
      }

      case StaticArrayFromValuesTE(exprs, arrayReference2, arrayType2) => {
        val (resultsHE, deferreds) =
          translateExpressionsUntilNever(hinputs, hamuts, currentFunctionHeader, locals, exprs);
        // Don't evaluate anything that can't ever be run, see BRCOBS
        resultsHE.lastOption.map(_.resultType.kind) match {
          case Some(NeverH(_)) => {
            return (Hammer.consecrash(locals, resultsHE), Vector())
          }
          case _ =>
        }
        val (underlyingArrayH) =
          typeHammer.translateStaticSizedArray(hinputs, hamuts, arrayType2);

        val (arrayReferenceH) =
          typeHammer.translateReference(hinputs, hamuts, arrayReference2)
        vassert(arrayReferenceH.kind == underlyingArrayH)

        val newStructNode =
          NewArrayFromValuesH(
            arrayReferenceH.expectStaticSizedArrayReference(),
            resultsHE)

        val newStructAndDeferredsExprH =
        translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, newStructNode, deferreds)

        (newStructAndDeferredsExprH, Vector.empty)
      }

      case ConstructTE(structTT, resultType2, memberExprs) => {
        val (membersHE, deferreds) =
          translateExpressionsUntilNever(hinputs, hamuts, currentFunctionHeader, locals, memberExprs);
        // Don't evaluate anything that can't ever be run, see BRCOBS
        membersHE.lastOption.map(_.resultType.kind) match {
          case Some(NeverH(_)) => {
            return (Hammer.consecrash(locals, membersHE), Vector())
          }
          case _ =>
        }

        val (resultTypeH) =
          typeHammer.translateReference(hinputs, hamuts, resultType2)


        val structDefH = hamuts.structDefsByRefT(structTT)
        vassert(membersHE.size == structDefH.members.size)
        membersHE.zip(structDefH.members).foreach({ case (memberHE, memberH ) =>
          vassert(memberHE.resultType == memberH.tyype)
        })
        val newStructNode =
          NewStructH(
            membersHE,
            structDefH.members.map(_.name),
            resultTypeH.expectStructReference())

        val newStructAndDeferredsExprH =
            translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, newStructNode, deferreds)

        (newStructAndDeferredsExprH, Vector.empty)
      }

      case load2 @ SoftLoadTE(_, _) => {
        val (loadedAccessH, deferreds) =
          loadHammer.translateLoad(hinputs, hamuts, currentFunctionHeader, locals, load2)
        (loadedAccessH, deferreds)
      }

      case lookup2 @ LocalLookupTE(_,AddressibleLocalVariableT(_, _, _)) => {
        val loadBoxAccess =
          loadHammer.translateLocalAddress(hinputs, hamuts, currentFunctionHeader, locals, lookup2)
        (loadBoxAccess, Vector.empty)
      }

      case lookup2 @ AddressMemberLookupTE(_,_, _, _, _) => {
        val (loadBoxAccess, deferreds) =
          loadHammer.translateMemberAddress(hinputs, hamuts, currentFunctionHeader, locals, lookup2)
        (loadBoxAccess, deferreds)
      }

      case if2 @ IfTE(_, _, _) => {
        val maybeAccess =
          translateIf(hinputs, hamuts, currentFunctionHeader, locals, if2)
        (maybeAccess, Vector.empty)
      }

      case prsaTE @ PushRuntimeSizedArrayTE(_, _) => {

        val PushRuntimeSizedArrayTE(arrayTE, newcomerTE) = prsaTE;

        val (arrayHE, arrayDeferreds) =
          translate(
            hinputs, hamuts, currentFunctionHeader, locals, arrayTE);
        val rsaHE = arrayHE.expectRuntimeSizedArrayAccess()
        val rsaDefH = hamuts.getRuntimeSizedArray(rsaHE.resultType.kind)

        val (newcomerHE, newcomerDeferreds) =
          translate(
            hinputs, hamuts, currentFunctionHeader, locals, newcomerTE);

        vassert(newcomerHE.resultType == rsaDefH.elementType)

        val constructArrayCallNode = PushRuntimeSizedArrayH(rsaHE, newcomerHE)

        val access =
          translateDeferreds(
            hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, arrayDeferreds ++ newcomerDeferreds)

        (access, Vector.empty)
      }

      case prsaTE @ PopRuntimeSizedArrayTE(_) => {
        val PopRuntimeSizedArrayTE(arrayTE) = prsaTE;

        val (arrayHE, arrayDeferreds) =
          translate(
            hinputs, hamuts, currentFunctionHeader, locals, arrayTE);
        val rsaHE = arrayHE.expectRuntimeSizedArrayAccess()
        val rsaDefH = hamuts.getRuntimeSizedArray(rsaHE.resultType.kind)

        val constructArrayCallNode = PopRuntimeSizedArrayH(rsaHE, rsaDefH.elementType)

        val access =
          translateDeferreds(
            hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, arrayDeferreds)

        (access, Vector.empty)
      }

      case nmrsaTE @ NewMutRuntimeSizedArrayTE(_, _) => {
        val access =
          translateNewMutRuntimeSizedArray(
            hinputs, hamuts, currentFunctionHeader, locals, nmrsaTE)
        (access, Vector.empty)
      }

      case nirsaTE @ NewImmRuntimeSizedArrayTE(_, _, _, _) => {
        val access =
          translateNewImmRuntimeSizedArray(
            hinputs, hamuts, currentFunctionHeader, locals, nirsaTE)
        (access, Vector.empty)
      }

      case ca2 @ StaticArrayFromCallableTE(_, _, _) => {
        val access =
          translateStaticArrayFromCallable(
            hinputs, hamuts, currentFunctionHeader, locals, ca2)
        (access, Vector.empty)
      }

      case ReinterpretTE(innerExpr, resultType2) => {
        // Check types; it's overkill because reinterprets are rather scary.
        val innerExprResultType2 = innerExpr.result.reference
        val (innerExprResultTypeH) = typeHammer.translateReference(hinputs, hamuts, innerExprResultType2);
        val (resultTypeH) = typeHammer.translateReference(hinputs, hamuts, resultType2);
        innerExprResultTypeH.kind match {
          case NeverH(_) =>
          case _ => {
            if (innerExprResultTypeH != resultTypeH) {
              vfail(innerExprResultTypeH + " doesnt match " + resultTypeH);
            }
          }
        }

        val (innerExprHE, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);

        // Not always the case:
        //   vcurious(innerExprResultTypeH.kind == NeverH() || resultTypeH.kind == NeverH())
        // for example when we're destructuring a TupleT2 or PackT2, we interpret from that
        // to its understruct.

        // These both trip:
        //   vcurious(innerExprResultTypeH == resultTypeH)
        //   vcurious(innerExprResultTypeH != resultTypeH)
        // Because sometimes theyre actually the same, because we might interpret a tuple to
        // its understruct, and sometimes theyre different, when we're making a Never into
        // an Int for example when one branch of an If panics or returns.

        innerExprResultTypeH.kind match {
          case NeverH(_) => vfail()
          case _ =>
        }
        resultTypeH.kind match {
          case NeverH(_) => vfail()
          case _ =>
        }
        (innerExprHE, deferreds)
      }

      case up @ InterfaceToInterfaceUpcastTE(innerExpr, targetInterfaceRef2) => {
        val targetPointerType2 = up.result.reference;
        val sourcePointerType2 = innerExpr.result.reference

        val (sourcePointerTypeH) =
          typeHammer.translateReference(hinputs, hamuts, sourcePointerType2);
        val (targetPointerTypeH) =
          typeHammer.translateReference(hinputs, hamuts, targetPointerType2);

        val sourceStructRefH = sourcePointerTypeH.kind.asInstanceOf[InterfaceRefH]
        val targetInterfaceRefH = targetPointerTypeH.kind.asInstanceOf[InterfaceRefH]

        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        // Upcasting an interface is technically a no-op with our language, but the sculptor
        // will still want to do it to do some checking in debug mode.
        val upcastNode =
            InterfaceToInterfaceUpcastH(
              innerExprHE.expectInterfaceAccess(),
              targetInterfaceRefH);
        (upcastNode, innerDeferreds)
      }

      case up @ StructToInterfaceUpcastTE(innerExpr, targetInterfaceRef2) => {
        val targetPointerType2 = up.result.reference;
        val sourcePointerType2 = innerExpr.result.reference

        val (sourcePointerTypeH) =
          typeHammer.translateReference(hinputs, hamuts, sourcePointerType2);
        val (targetPointerTypeH) =
          typeHammer.translateReference(hinputs, hamuts, targetPointerType2);

        val sourceStructRefH = sourcePointerTypeH.kind.asInstanceOf[StructRefH]

        val targetInterfaceRefH = targetPointerTypeH.kind.asInstanceOf[InterfaceRefH]

        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        // Upcasting an interface is technically a no-op with our language, but the sculptor
        // will still want to do it to do some checking in debug mode.
        val upcastNode =
            StructToInterfaceUpcastH(
              innerExprHE.expectStructAccess(),
              targetInterfaceRefH)
        (upcastNode, innerDeferreds)
      }

      case ExternFunctionCallTE(prototype2, argsExprs2) => {
        val access =
          translateExternFunctionCall(hinputs, hamuts, currentFunctionHeader, locals, prototype2, argsExprs2)
        (access, Vector.empty)
      }

      case while2 @ WhileTE(_) => {
        val whileH =
            translateWhile(hinputs, hamuts, currentFunctionHeader, locals, while2)
        (whileH, Vector.empty)
      }

      case DeferTE(innerExpr, deferredExpr) => {
        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (innerExprHE, Vector(deferredExpr) ++ innerDeferreds)
      }

      case DiscardTE(innerExpr) => {
        val (undiscardedInnerExprH, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        vassert(innerDeferreds.isEmpty) // BMHD, probably need to translate them here.
        val innerExprH = DiscardH(undiscardedInnerExprH)
        val innerWithDeferredsExprH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, innerExprH, innerDeferreds)
        (innerWithDeferredsExprH, Vector.empty)
      }
      case ReturnTE(innerExpr) => {
        vassert(
          innerExpr.result.reference.kind == NeverT(false) ||
          innerExpr.result.reference == currentFunctionHeader.returnType)

        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);

        // Return is a special case where we execute the *inner* expression (not the whole return expression) and
        // then the deferreds and then the return. See MEDBR.
        val innerWithDeferreds =
            translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, innerExprHE, innerDeferreds)

        // If we're returning a never, just strip this Return, because we'll
        // never get here.
        innerWithDeferreds.resultType.kind match {
          case NeverH(_) => {
            return (innerWithDeferreds, Vector.empty)
          }
          case _ =>
        }

        vassert(innerExpr.result.reference == currentFunctionHeader.returnType)
        (ReturnH(innerWithDeferreds), Vector.empty)
      }
      case ArgLookupTE(paramIndex, type2) => {
        val typeH = typeHammer.translateReference(hinputs, hamuts, type2)
        vassert(currentFunctionHeader.paramTypes(paramIndex) == type2)
        vassert(typeHammer.translateReference(hinputs, hamuts, currentFunctionHeader.paramTypes(paramIndex)) == typeH)
        val argNode = ArgumentH(typeH, paramIndex)
        (argNode, Vector.empty)
      }

      case das2 @ DestroyStaticSizedArrayIntoFunctionTE(_, _, _, _) => {
        val dasH =
            translateDestroyStaticSizedArray(
              hinputs, hamuts, currentFunctionHeader, locals, das2)
        (dasH, Vector.empty)
      }

      case das2 @ DestroyImmRuntimeSizedArrayTE(_, _, _, _) => {
        val drsaH =
            translateDestroyImmRuntimeSizedArray(
              hinputs, hamuts, currentFunctionHeader, locals, das2)
        (drsaH, Vector.empty)
      }

//      case UnreachableMootTE(innerExpr) => {
//        val (innerExprHE, innerDeferreds) =
//          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
//        val innerWithDeferredsH =
//          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, innerExprHE, innerDeferreds)
//
//        // Throw away the inner expression because we dont want it to be generated, because
//        // theyll never get run anyway.
//        // We only translated them above to mark unstackified things unstackified.
//
//        val void = ConstantVoidH()
//
//        (void, Vector.empty)
//      }

      case BorrowToWeakTE(innerExpr) => {
        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (BorrowToWeakH(innerExprHE), innerDeferreds)
      }

      case IsSameInstanceTE(leftExprT, rightExprT) => {
        val (leftExprHE, leftDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, leftExprT);
        val (rightExprHE, rightDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, rightExprT);
        val resultHE = IsSameInstanceH(leftExprHE, rightExprHE)

        val expr = translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, resultHE, leftDeferreds ++ rightDeferreds)
        (expr, Vector.empty)
      }

      case AsSubtypeTE(leftExprT, targetSubtype, resultOptType, someConstructor, noneConstructor) => {
        val (resultHE, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, leftExprT);
        val (targetSubtypeH) =
          typeHammer.translateKind(hinputs, hamuts, targetSubtype)
        val (resultOptTypeH) =
          typeHammer.translateReference(hinputs, hamuts, resultOptType).expectInterfaceReference()

        val someConstructorH =
          functionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, someConstructor);
        val noneConstructorH =
          functionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, noneConstructor);

        val resultNode =
          AsSubtypeH(
            resultHE,
            targetSubtypeH,
            resultOptTypeH,
            someConstructorH.prototype,
            noneConstructorH.prototype);
        (resultNode, deferreds)
      }

      case DestroyMutRuntimeSizedArrayTE(rsaTE) => {
        val (rsaHE, rsaDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, rsaTE);

        val destroyHE =
          DestroyMutRuntimeSizedArrayH(rsaHE.expectRuntimeSizedArrayAccess())

        val expr = translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, destroyHE, rsaDeferreds)
        (expr, Vector.empty)
      }

      case BreakTE() => {
        (BreakH(), Vector.empty)
      }

      case _ => {
        vfail("wat " + expr2)
      }
    }
  }

  def translateDeferreds(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    originalExpr: ExpressionH[KindH],
    deferreds: Vector[ExpressionT]):
  ExpressionH[KindH] = {
    if (deferreds.isEmpty) {
      return originalExpr
    }

    val (deferredExprs, deferredDeferreds) =
      translateExpressionsUntilNever(
        hinputs, hamuts, currentFunctionHeader, locals, deferreds)
    // Don't evaluate anything that can't ever be run, see BRCOBS
    deferredExprs.lastOption.map(_.resultType.kind) match {
      case Some(NeverH(_)) => {
        return Hammer.consecrash(locals, deferredExprs)
      }
      case _ =>
    }
    if (deferredExprs.map(_.resultType.kind).toSet != Set(VoidH())) {
      // curiosity, why would a deferred ever have a result
      vcurious()
    }
    if (locals.locals.size != locals.locals.size) {
      // There shouldnt have been any locals introduced
      vfail("wat")
    }
    vassert(deferredDeferreds.isEmpty)
    // Don't need these, they should all be voids anyway

    vcurious(deferredExprs.nonEmpty)

    val newExprs =
      if (originalExpr.resultType.kind == VoidH()) {
        val void = ConstantVoidH()
        Vector(originalExpr) ++ (deferredExprs :+ void)
      } else {
        val temporaryResultLocal = locals.addHammerLocal(originalExpr.resultType, Final)
        val stackify = StackifyH(originalExpr, temporaryResultLocal, None)
        val unstackify = UnstackifyH(temporaryResultLocal)
        locals.markUnstackified(temporaryResultLocal.id)
        Vector(stackify) ++ deferredExprs ++ Vector(unstackify)
      }

    val result = ConsecutorH(newExprs)
    vassert(originalExpr.resultType == result.resultType)
    result
  }

  def translateExpressionsUntilNever(
    hinputs: Hinputs, hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    exprsTE: Vector[ExpressionT]):
  (Vector[ExpressionH[KindH]], Vector[ExpressionT]) = {
    val (exprsHE, deferreds) =
      exprsTE.foldLeft((Vector[ExpressionH[KindH]](), Vector[ExpressionT]()))({
        // If we previously saw a Never, stop there, don't proceed, don't even waste
        // time compiling the rest.
        case ((prevExprsHE, _), _)
            if (prevExprsHE.lastOption.map(_.resultType.kind) match {
              case Some(NeverH(_)) => true case _ => false
            }) => {
          (prevExprsHE, Vector())
        }
        case ((prevExprsHE, prevDeferreds), nextTE) => {
          val (nextHE, nextDeferreds) =
            translate(hinputs, hamuts, currentFunctionHeader, locals, nextTE);
          (prevExprsHE :+ nextHE, prevDeferreds ++ nextDeferreds)
        }
      })

    // We'll never get to the deferreds, so forget them.
    exprsHE.lastOption.map(_.resultType.kind) match {
      case Some(NeverH(_)) => (exprsHE, Vector())
      case _ => (exprsHE, deferreds)
    }
  }

  def translateExpressionsAndDeferreds(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    exprs2: Vector[ExpressionT]):
  ExpressionH[KindH] = {
    val exprs =
      exprs2.map({ case expr2 =>
        val (firstHE, firstDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, expr2);
        val firstExprWithDeferredsH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, firstHE, firstDeferreds)
        firstExprWithDeferredsH
      })
    Hammer.consecutive(exprs)
  }

  def translateExternFunctionCall(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    prototype2: PrototypeT,
    argsExprs2: Vector[ReferenceExpressionTE]):
  (ExpressionH[KindH]) = {
    val (argsHE, argsDeferreds) =
      translateExpressionsUntilNever(
        hinputs, hamuts, currentFunctionHeader, locals, argsExprs2);
    // Don't evaluate anything that can't ever be run, see BRCOBS
    if (argsHE.nonEmpty && argsHE.last.resultType.kind == NeverH(true)) {
      return Hammer.consecrash(locals, argsHE)
    }

    // Doublecheck the types
    val (paramTypes) =
      typeHammer.translateReferences(hinputs, hamuts, prototype2.paramTypes);
    vassert(argsHE.map(_.resultType) == paramTypes)

    val (functionRefH) =
      functionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, prototype2);

    val callResultNode = ExternCallH(functionRefH.prototype, argsHE)

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, callResultNode, argsDeferreds)
  }

  def translateFunctionPointerCall(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    function: PrototypeT,
    args: Vector[ExpressionT],
    resultType2: CoordT):
  ExpressionH[KindH] = {
    val returnType2 = function.returnType
    val paramTypes = function.paramTypes
    val (argsHE, argsDeferreds) =
      translateExpressionsUntilNever(
        hinputs, hamuts, currentFunctionHeader, locals, args);
    // Don't evaluate anything that can't ever be run, see BRCOBS
    argsHE.lastOption.map(_.resultType.kind) match {
      case Some(NeverH(_)) => {
        return Hammer.consecrash(locals, argsHE)
      }
      case _ =>
    }

    val prototypeH =
      typeHammer.translatePrototype(hinputs, hamuts, function)

    // Doublecheck the types
    val (paramTypesH) =
      typeHammer.translateReferences(hinputs, hamuts, paramTypes)
    vassert(argsHE.map(_.resultType) == paramTypesH)

    // Doublecheck return
    val (returnTypeH) = typeHammer.translateReference(hinputs, hamuts, returnType2)
    val (resultTypeH) = typeHammer.translateReference(hinputs, hamuts, resultType2);
    vassert(returnTypeH == resultTypeH)

    val callResultNode = CallH(prototypeH, argsHE)

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, callResultNode, argsDeferreds)
  }

  def translateNewMutRuntimeSizedArray(
    hinputs: Hinputs, hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    constructArray2: NewMutRuntimeSizedArrayTE):
  (ExpressionH[KindH]) = {
    val NewMutRuntimeSizedArrayTE(arrayType2, capacityExpr2) = constructArray2;

    val (capacityRegisterId, capacityDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, capacityExpr2);

    val (arrayRefTypeH) =
      typeHammer.translateReference(
        hinputs, hamuts, constructArray2.result.reference)

    val (arrayTypeH) =
      typeHammer.translateRuntimeSizedArray(hinputs, hamuts, arrayType2)
    vassert(arrayRefTypeH.expectRuntimeSizedArrayReference().kind == arrayTypeH)

    val elementType = hamuts.getRuntimeSizedArray(arrayTypeH).elementType

    val constructArrayCallNode =
      NewMutRuntimeSizedArrayH(
        capacityRegisterId.expectIntAccess(),
        elementType,
        arrayRefTypeH.expectRuntimeSizedArrayReference())

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, capacityDeferreds)
  }

  def translateNewImmRuntimeSizedArray(
    hinputs: Hinputs, hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    constructArray2: NewImmRuntimeSizedArrayTE):
  (ExpressionH[KindH]) = {
    val NewImmRuntimeSizedArrayTE(arrayType2, sizeExpr2, generatorExpr2, generatorMethod) = constructArray2;

    val (sizeRegisterId, sizeDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, sizeExpr2);

    val (generatorRegisterId, generatorDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, generatorExpr2);

    val (arrayRefTypeH) =
      typeHammer.translateReference(
        hinputs, hamuts, constructArray2.result.reference)

    val (arrayTypeH) =
      typeHammer.translateRuntimeSizedArray(hinputs, hamuts, arrayType2)
    vassert(arrayRefTypeH.expectRuntimeSizedArrayReference().kind == arrayTypeH)

    val elementType = hamuts.getRuntimeSizedArray(arrayTypeH).elementType

    val generatorMethodH =
      typeHammer.translatePrototype(hinputs, hamuts, generatorMethod)

    val constructArrayCallNode =
      NewImmRuntimeSizedArrayH(
        sizeRegisterId.expectIntAccess(),
        generatorRegisterId,
        generatorMethodH,
        elementType,
        arrayRefTypeH.expectRuntimeSizedArrayReference())

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, generatorDeferreds ++ sizeDeferreds)
  }

  def translateStaticArrayFromCallable(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    exprTE: StaticArrayFromCallableTE):
  (ExpressionH[KindH]) = {
    val StaticArrayFromCallableTE(arrayType2, generatorExpr2, generatorMethod) = exprTE;

    val (generatorRegisterId, generatorDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, generatorExpr2);

    val (arrayRefTypeH) =
      typeHammer.translateReference(
        hinputs, hamuts, exprTE.result.reference)

    val (arrayTypeH) =
      typeHammer.translateStaticSizedArray(hinputs, hamuts, arrayType2)
    vassert(arrayRefTypeH.expectStaticSizedArrayReference().kind == arrayTypeH)

    val elementType = hamuts.getStaticSizedArray(arrayTypeH).elementType

    val generatorMethodH =
      typeHammer.translatePrototype(hinputs, hamuts, generatorMethod)

    val constructArrayCallNode =
      StaticArrayFromCallableH(
        generatorRegisterId,
        generatorMethodH,
        elementType,
        arrayRefTypeH.expectStaticSizedArrayReference())

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, generatorDeferreds)
  }

  def translateDestroyStaticSizedArray(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    das2: DestroyStaticSizedArrayIntoFunctionTE):
  ExpressionH[KindH] = {
    val DestroyStaticSizedArrayIntoFunctionTE(arrayExpr2, staticSizedArrayType, consumerExpr2, consumerMethod2) = das2;

    val (arrayTypeH) =
      typeHammer.translateStaticSizedArray(hinputs, hamuts, staticSizedArrayType)
    val (arrayRefTypeH) =
      typeHammer.translateReference(hinputs, hamuts, arrayExpr2.result.reference)
    vassert(arrayRefTypeH.expectStaticSizedArrayReference().kind == arrayTypeH)

    val (arrayExprResultHE, arrayExprDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

    val (consumerCallableResultHE, consumerCallableDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, consumerExpr2);

    val staticSizedArrayDef = hamuts.getStaticSizedArray(arrayTypeH)

    val consumerMethod =
      typeHammer.translatePrototype(hinputs, hamuts, consumerMethod2)

    val destroyStaticSizedArrayCallNode =
      DestroyStaticSizedArrayIntoFunctionH(
        arrayExprResultHE.expectStaticSizedArrayAccess(),
        consumerCallableResultHE,
        consumerMethod,
        staticSizedArrayDef.elementType,
        staticSizedArrayDef.size)

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, destroyStaticSizedArrayCallNode, consumerCallableDeferreds ++ arrayExprDeferreds)
  }

  def translateDestroyImmRuntimeSizedArray(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    das2: DestroyImmRuntimeSizedArrayTE):
  ExpressionH[KindH] = {
    val DestroyImmRuntimeSizedArrayTE(arrayExpr2, runtimeSizedArrayType2, consumerExpr2, consumerMethod2) = das2;

    //    val RuntimeSizedArrayT2(RawArrayT2(memberType2, mutability)) = runtimeSizedArrayType2

    val (arrayTypeH) =
      typeHammer.translateRuntimeSizedArray(hinputs, hamuts, runtimeSizedArrayType2)
    val (arrayRefTypeH) =
      typeHammer.translateReference(hinputs, hamuts, arrayExpr2.result.reference)
    vassert(arrayRefTypeH.expectRuntimeSizedArrayReference().kind == arrayTypeH)

    val (arrayExprResultHE, arrayExprDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

    val (consumerCallableResultHE, consumerCallableDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, consumerExpr2);

    val consumerMethod =
      typeHammer.translatePrototype(hinputs, hamuts, consumerMethod2)

    val elementType =
      hamuts.getRuntimeSizedArray(
        arrayExprResultHE.expectRuntimeSizedArrayAccess().resultType.kind)
        .elementType

    val destroyStaticSizedArrayCallNode =
      DestroyImmRuntimeSizedArrayH(
        arrayExprResultHE.expectRuntimeSizedArrayAccess(),
        consumerCallableResultHE,
        consumerMethod,
        elementType)

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, destroyStaticSizedArrayCallNode, consumerCallableDeferreds ++ arrayExprDeferreds)
  }

  def translateIf(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    parentLocals: LocalsBox,
    if2: IfTE):
  ExpressionH[KindH] = {
    val IfTE(condition2, thenBlock2, elseBlock2) = if2

    val (conditionBlockH, Vector()) =
      translate(hinputs, hamuts, currentFunctionHeader, parentLocals, condition2);
    vassert(conditionBlockH.resultType == ReferenceH(ShareH, InlineH, BoolH()))

    val thenLocals = LocalsBox(parentLocals.snapshot)
    val (thenBlockH, Vector()) =
      translate(hinputs, hamuts, currentFunctionHeader, thenLocals, thenBlock2);
    val thenResultCoord = thenBlockH.resultType
    parentLocals.setNextLocalIdNumber(thenLocals.nextLocalIdNumber)

    val elseLocals = LocalsBox(parentLocals.snapshot)
    val (elseBlockH, Vector()) =
      translate(hinputs, hamuts, currentFunctionHeader, elseLocals, elseBlock2);
    val elseResultCoord = elseBlockH.resultType
    parentLocals.setNextLocalIdNumber(elseLocals.nextLocalIdNumber)

    val commonSupertypeH =
      typeHammer.translateReference(hinputs, hamuts, if2.result.reference)

    val ifCallNode = IfH(conditionBlockH.expectBoolAccess(), thenBlockH, elseBlockH, commonSupertypeH)


    val thenContinues = thenResultCoord.kind match { case NeverH(_) => false case _ => true }
    val elseContinues = elseResultCoord.kind match { case NeverH(_) => false case _ => true }

    val unstackifiesOfParentLocals =
      if (thenContinues && elseContinues) { // Both continue
        val parentLocalsAfterThen = thenLocals.locals.keySet -- thenLocals.unstackifiedVars
        val parentLocalsAfterElse = elseLocals.locals.keySet -- elseLocals.unstackifiedVars
        // The same outside-if variables should still exist no matter which branch we went down.
        if (parentLocalsAfterThen != parentLocalsAfterElse) {
          vfail("Internal error:\nIn function " + currentFunctionHeader + "\nMismatch in if branches' parent-unstackifies:\nThen branch: " + parentLocalsAfterThen + "\nElse branch: " + parentLocalsAfterElse)
        }
        // Since theyre the same, just arbitrarily use the then.
        thenLocals.unstackifiedVars
      } else if (thenContinues) {
        // Then continues, else does not
        // Throw away any information from the else. But do consider those from the then.
        thenLocals.unstackifiedVars
      } else if (elseContinues) {
        // Else continues, then does not
        elseLocals.unstackifiedVars
      } else {
        // Neither continues, so neither unstackifies things.
        // It also kind of doesnt matter, no code after this will run.
        Set[VariableIdH]()
      }

    val parentLocalsToUnstackify =
    // All the parent locals...
      parentLocals.locals.keySet
        // ...minus the ones that were unstackified before...
        .diff(parentLocals.unstackifiedVars)
        // ...which were unstackified by the branch.
        .intersect(unstackifiesOfParentLocals)
    parentLocalsToUnstackify.foreach(parentLocals.markUnstackified)

    ifCallNode
  }

  def translateWhile(
    hinputs: Hinputs, hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    while2: WhileTE):
  WhileH = {

    val WhileTE(bodyExpr2) = while2

    val (exprWithoutDeferreds, deferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, bodyExpr2);
    val expr =
      translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, exprWithoutDeferreds, deferreds)

    val whileCallNode = WhileH(expr)
    whileCallNode
  }

  def translateInterfaceFunctionCall(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    superFunctionHeader: FunctionHeaderT,
    resultType2: CoordT,
    argsExprs2: Vector[ExpressionT]):
  ExpressionH[KindH] = {
    val (argsHE, argsDeferreds) =
      translateExpressionsUntilNever(
        hinputs, hamuts, currentFunctionHeader, locals, argsExprs2);
    // Don't evaluate anything that can't ever be run, see BRCOBS
    if (argsHE.nonEmpty && argsHE.last.resultType.kind == NeverH(false)) {
      return Hammer.consecrash(locals, argsHE)
    }

    val virtualParamIndex = superFunctionHeader.getVirtualIndex.get
    val CoordT(_, interfaceTT @ InterfaceTT(_)) =
      superFunctionHeader.paramTypes(virtualParamIndex)
    val (interfaceRefH) =
      structHammer.translateInterfaceRef(hinputs, hamuts, interfaceTT)
    val edge = hinputs.edgeBlueprintsByInterface(interfaceTT)
    vassert(edge.interface == interfaceTT)
    val indexInEdge = edge.superFamilyRootBanners.indexWhere(x => superFunctionHeader.toBanner.same(x))
    vassert(indexInEdge >= 0)

    val (prototypeH) = typeHammer.translatePrototype(hinputs, hamuts, superFunctionHeader.toPrototype)

    val callNode =
      InterfaceCallH(
        argsHE,
        virtualParamIndex,
        interfaceRefH,
        indexInEdge,
        prototypeH)

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, callNode, argsDeferreds)
  }
}
