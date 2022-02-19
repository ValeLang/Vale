package net.verdagon.vale.hammer

import net.verdagon.vale.{vassert, vassertSome, vcurious, vfail, vimpl, metal => m}
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.metal.{ShareH, Immutable => _, Mutable => _, OwnH => _, PointerH => _, _}
import net.verdagon.vale.templar.{Hinputs, _}
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.env.AddressibleLocalVariableT
import net.verdagon.vale.templar.types._

object ExpressionHammer {

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
          LetHammer.translateLet(hinputs, hamuts, currentFunctionHeader, locals, let2)
        (letH, Vector.empty)
      }
      case let2 @ LetAndLendTE(_, _, _) => {
        val borrowAccess =
          LetHammer.translateLetAndPoint(hinputs, hamuts, currentFunctionHeader, locals, let2)
        (borrowAccess, Vector.empty)
      }
      case des2 @ DestroyTE(_, _, _) => {
        val destroyH =
            LetHammer.translateDestroy(hinputs, hamuts, currentFunctionHeader, locals, des2)
        // Templar destructures put things in local variables (even though hammer itself
        // uses registers internally to make that happen).
        // Since all the members landed in locals, we still need something to return, so we
        // return a void.
        (destroyH, Vector.empty)
      }
      case des2 @ DestroyStaticSizedArrayIntoLocalsTE(_, _, _) => {
        val destructureH =
            LetHammer.translateDestructureStaticSizedArray(hinputs, hamuts, currentFunctionHeader, locals, des2)
        // Templar destructures put things in local variables (even though hammer itself
        // uses registers internally to make that happen).
        // Since all the members landed in locals, we still need something to return, so we
        // return a void.
        (destructureH, Vector.empty)
      }
      case unlet2 @ UnletTE(_) => {
        val valueAccess =
          LetHammer.translateUnlet(
            hinputs, hamuts, currentFunctionHeader, locals, unlet2)
        (valueAccess, Vector.empty)
      }
      case mutate2 @ MutateTE(_, _) => {
        val newEmptyPackStructNodeHE =
          MutateHammer.translateMutate(hinputs, hamuts, currentFunctionHeader, locals, mutate2)
        (newEmptyPackStructNodeHE, Vector.empty)
      }
      case b @ BlockTE(_) => {
        val blockH =
          BlockHammer.translateBlock(hinputs, hamuts, currentFunctionHeader, locals, b)
        (blockH, Vector.empty)
      }
      case call2 @ FunctionCallTE(callableExpr, args) => {
        val access =
          CallHammer.translateFunctionPointerCall(
            hinputs, hamuts, currentFunctionHeader, locals, callableExpr, args, call2.result.reference)
        (access, Vector.empty)
      }

      case InterfaceFunctionCallTE(superFunctionHeader, resultType2, argsExprs2) => {
        val access =
          CallHammer.translateInterfaceFunctionCall(
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
          TypeHammer.translateReference(hinputs, hamuts, resultOptBorrowType2)

        val someConstructorH =
          FunctionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, someConstructor);
        val noneConstructorH =
          FunctionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, noneConstructor);

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
          StructHammer.translateStructRef(hinputs, hamuts, resultStructT);
        val (resultReference) =
          TypeHammer.translateReference(hinputs, hamuts, resultType)
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
          TypeHammer.translateStaticSizedArray(hinputs, hamuts, arrayType2);

        val (arrayReferenceH) =
          TypeHammer.translateReference(hinputs, hamuts, arrayReference2)
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
          TypeHammer.translateReference(hinputs, hamuts, resultType2)


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

      case load2 @ SoftLoadTE(_, _, _) => {
        val (loadedAccessH, deferreds) =
          LoadHammer.translateLoad(hinputs, hamuts, currentFunctionHeader, locals, load2)
        (loadedAccessH, deferreds)
      }

      case lookup2 @ LocalLookupTE(_,AddressibleLocalVariableT(_, _, _)) => {
        val loadBoxAccess =
          LoadHammer.translateLocalAddress(hinputs, hamuts, currentFunctionHeader, locals, lookup2)
        (loadBoxAccess, Vector.empty)
      }

      case lookup2 @ AddressMemberLookupTE(_,_, _, _, _) => {
        val (loadBoxAccess, deferreds) =
          LoadHammer.translateMemberAddress(hinputs, hamuts, currentFunctionHeader, locals, lookup2)
        (loadBoxAccess, deferreds)
      }

      case if2 @ IfTE(_, _, _) => {
        val maybeAccess =
          CallHammer.translateIf(hinputs, hamuts, currentFunctionHeader, locals, if2)
        (maybeAccess, Vector.empty)
      }

      case prsaTE @ PushRuntimeSizedArrayTE(_, _) => {

        val PushRuntimeSizedArrayTE(arrayTE, newcomerTE) = prsaTE;

        val (arrayHE, arrayDeferreds) =
          ExpressionHammer.translate(
            hinputs, hamuts, currentFunctionHeader, locals, arrayTE);
        val rsaHE = arrayHE.expectRuntimeSizedArrayAccess()
        val rsaDefH = hamuts.getRuntimeSizedArray(rsaHE.resultType.kind)

        val (newcomerHE, newcomerDeferreds) =
          ExpressionHammer.translate(
            hinputs, hamuts, currentFunctionHeader, locals, newcomerTE);

        vassert(newcomerHE.resultType == rsaDefH.elementType)

        val constructArrayCallNode = PushRuntimeSizedArrayH(rsaHE, newcomerHE)

        val access =
          ExpressionHammer.translateDeferreds(
            hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, arrayDeferreds ++ newcomerDeferreds)

        (access, Vector.empty)
      }

      case prsaTE @ PopRuntimeSizedArrayTE(_) => {
        val PopRuntimeSizedArrayTE(arrayTE) = prsaTE;

        val (arrayHE, arrayDeferreds) =
          ExpressionHammer.translate(
            hinputs, hamuts, currentFunctionHeader, locals, arrayTE);
        val rsaHE = arrayHE.expectRuntimeSizedArrayAccess()
        val rsaDefH = hamuts.getRuntimeSizedArray(rsaHE.resultType.kind)

        val constructArrayCallNode = PopRuntimeSizedArrayH(rsaHE, rsaDefH.elementType)

        val access =
          ExpressionHammer.translateDeferreds(
            hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, arrayDeferreds)

        (access, Vector.empty)
      }

      case nmrsaTE @ NewMutRuntimeSizedArrayTE(_, _) => {
        val access =
          CallHammer.translateNewMutRuntimeSizedArray(
            hinputs, hamuts, currentFunctionHeader, locals, nmrsaTE)
        (access, Vector.empty)
      }

      case nirsaTE @ NewImmRuntimeSizedArrayTE(_, _, _, _) => {
        val access =
          CallHammer.translateNewImmRuntimeSizedArray(
            hinputs, hamuts, currentFunctionHeader, locals, nirsaTE)
        (access, Vector.empty)
      }

      case ca2 @ StaticArrayFromCallableTE(_, _, _) => {
        val access =
          CallHammer.translateStaticArrayFromCallable(
            hinputs, hamuts, currentFunctionHeader, locals, ca2)
        (access, Vector.empty)
      }

      case TemplarReinterpretTE(innerExpr, resultType2) => {
        // Check types; it's overkill because reinterprets are rather scary.
        val innerExprResultType2 = innerExpr.result.reference
        val (innerExprResultTypeH) = TypeHammer.translateReference(hinputs, hamuts, innerExprResultType2);
        val (resultTypeH) = TypeHammer.translateReference(hinputs, hamuts, resultType2);
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
          TypeHammer.translateReference(hinputs, hamuts, sourcePointerType2);
        val (targetPointerTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, targetPointerType2);

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
          TypeHammer.translateReference(hinputs, hamuts, sourcePointerType2);
        val (targetPointerTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, targetPointerType2);

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
          CallHammer.translateExternFunctionCall(hinputs, hamuts, currentFunctionHeader, locals, prototype2, argsExprs2)
        (access, Vector.empty)
      }

      case while2 @ WhileTE(_) => {
        val whileH =
            CallHammer.translateWhile(hinputs, hamuts, currentFunctionHeader, locals, while2)
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
        val typeH = TypeHammer.translateReference(hinputs, hamuts, type2)
        vassert(currentFunctionHeader.paramTypes(paramIndex) == type2)
        vassert(TypeHammer.translateReference(hinputs, hamuts, currentFunctionHeader.paramTypes(paramIndex)) == typeH)
        val argNode = ArgumentH(typeH, paramIndex)
        (argNode, Vector.empty)
      }

      case das2 @ DestroyStaticSizedArrayIntoFunctionTE(_, _, _, _) => {
        val dasH =
            CallHammer.translateDestroyStaticSizedArray(
              hinputs, hamuts, currentFunctionHeader, locals, das2)
        (dasH, Vector.empty)
      }

      case das2 @ DestroyImmRuntimeSizedArrayTE(_, _, _, _) => {
        val drsaH =
            CallHammer.translateDestroyImmRuntimeSizedArray(
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

      case PointerToWeakTE(innerExpr) => {
        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (PointerToWeakH(innerExprHE), innerDeferreds)
      }

      case NarrowPermissionTE(innerExpr, targetPermission) => {
        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (NarrowPermissionH(innerExprHE, Conversions.evaluatePermission(targetPermission)), innerDeferreds)
      }

      case BorrowToPointerTE(innerExpr) => {
        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (BorrowToPointerH(innerExprHE), innerDeferreds)
      }

      case PointerToBorrowTE(innerExpr) => {
        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (PointerToBorrowH(innerExprHE), innerDeferreds)
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
          TypeHammer.translateKind(hinputs, hamuts, targetSubtype)
        val (resultOptTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, resultOptType).expectInterfaceReference()

        val someConstructorH =
          FunctionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, someConstructor);
        val noneConstructorH =
          FunctionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, noneConstructor);

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
        val temporaryResultLocal = locals.addHammerLocal(originalExpr.resultType, m.Final)
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
}
