package net.verdagon.vale.hammer

import net.verdagon.vale.{vassert, vassertSome, vcurious, vfail, vimpl, metal => m}
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.metal.{ShareH, PointerH => _, Immutable => _, Mutable => _, OwnH => _, _}
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
        val newEmptyPackStructNodeLine =
          MutateHammer.translateMutate(hinputs, hamuts, currentFunctionHeader, locals, mutate2)
        (newEmptyPackStructNodeLine, Vector.empty)
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

      case ConsecutorTE(exprs) => {
        val expression =
          translateExpressionsAndDeferreds(hinputs, hamuts, currentFunctionHeader, locals, exprs);
        (expression, Vector.empty)
      }

      case ArrayLengthTE(arrayExpr2) => {
        val (resultLine, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

        val lengthResultNode = ArrayLengthH(resultLine);

        val arrayLengthAndDeferredsExprH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, lengthResultNode, deferreds)

        (arrayLengthAndDeferredsExprH, Vector.empty)
      }

      case RuntimeSizedArrayCapacityTE(arrayExpr2) => {
        val (resultLine, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

        val lengthResultNode = ArrayCapacityH(resultLine);

        val arrayLengthAndDeferredsExprH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, lengthResultNode, deferreds)

        (arrayLengthAndDeferredsExprH, Vector.empty)
      }

      case LockWeakTE(innerExpr2, resultOptBorrowType2, someConstructor, noneConstructor) => {
        val (resultLine, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr2);
        val (resultOptBorrowTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, resultOptBorrowType2)

        val someConstructorH =
          FunctionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, someConstructor);
        val noneConstructorH =
          FunctionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, noneConstructor);

        val resultNode =
          LockWeakH(
            resultLine,
            resultOptBorrowTypeH.expectInterfaceReference(),
            someConstructorH.prototype,
            noneConstructorH.prototype);
        (resultNode, deferreds)
      }

      case TupleTE(exprs, resultType) => {
        val (resultLines, deferreds) =
          translateExpressions(hinputs, hamuts, currentFunctionHeader, locals, exprs);
        val resultStructT = resultType.kind match { case s @ StructTT(_) => s }
        val (underlyingStructRefH) =
          StructHammer.translateStructRef(hinputs, hamuts, resultStructT);
        val (resultReference) =
          TypeHammer.translateReference(hinputs, hamuts, resultType)
        vassert(resultReference.kind == underlyingStructRefH)

        val structDefH = hamuts.structDefsByRefT(resultStructT)
        vassert(resultLines.size == structDefH.members.size)
        val newStructNode =
          NewStructH(
            resultLines,
            structDefH.members.map(_.name),
            resultReference.expectStructReference())
        // Export locals from inside the pack

        val newStructAndDeferredsExprH =
            translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, newStructNode, deferreds)

        (newStructAndDeferredsExprH, Vector.empty)
      }

      case StaticArrayFromValuesTE(exprs, arrayReference2, arrayType2) => {
        val (resultLines, deferreds) =
          translateExpressions(hinputs, hamuts, currentFunctionHeader, locals, exprs);
        val (underlyingArrayH) =
          TypeHammer.translateStaticSizedArray(hinputs, hamuts, arrayType2);

        val (arrayReferenceH) =
          TypeHammer.translateReference(hinputs, hamuts, arrayReference2)
        vassert(arrayReferenceH.kind == underlyingArrayH)

        val newStructNode =
          NewArrayFromValuesH(
            arrayReferenceH.expectStaticSizedArrayReference(),
            resultLines)

        val newStructAndDeferredsExprH =
        translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, newStructNode, deferreds)

        (newStructAndDeferredsExprH, Vector.empty)
      }

      case ConstructTE(structTT, resultType2, memberExprs) => {
        val (memberResultLines, deferreds) =
          translateExpressions(hinputs, hamuts, currentFunctionHeader, locals, memberExprs);

        val (resultTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, resultType2)


        val structDefH = hamuts.structDefsByRefT(structTT)
        vassert(memberResultLines.size == structDefH.members.size)
        memberResultLines.zip(structDefH.members).foreach({ case (memberResultLine, memberH ) =>
          vassert(memberResultLine.resultType == memberH.tyype)
        })
        val newStructNode =
          NewStructH(
            memberResultLines,
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
        if (innerExprResultTypeH.kind != NeverH()) {
          if (innerExprResultTypeH != resultTypeH) {
            vfail(innerExprResultTypeH  + " doesnt match " + resultTypeH);
          }
        }

        val (innerExprResultLine, deferreds) =
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

        if (innerExprResultTypeH.kind == NeverH() || resultTypeH.kind == NeverH()) {
          vfail()
//          (ReinterpretH(innerExprResultLine, resultTypeH), deferreds)
        } else {
          (innerExprResultLine, deferreds)
        }
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

        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        // Upcasting an interface is technically a no-op with our language, but the sculptor
        // will still want to do it to do some checking in debug mode.
        val upcastNode =
            InterfaceToInterfaceUpcastH(
              innerExprResultLine.expectInterfaceAccess(),
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

        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        // Upcasting an interface is technically a no-op with our language, but the sculptor
        // will still want to do it to do some checking in debug mode.
        val upcastNode =
            StructToInterfaceUpcastH(
              innerExprResultLine.expectStructAccess(),
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
        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (innerExprResultLine, Vector(deferredExpr) ++ innerDeferreds)
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
        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);

        // Return is a special case where we execute the *inner* expression (not the whole return expression) and
        // then the deferreds and then the return. See MEDBR.
        val innerWithDeferreds =
            translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, innerExprResultLine, innerDeferreds)

        vassert(
          innerExpr.result.kind == NeverT() ||
          innerExpr.result.reference == currentFunctionHeader.returnType)
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

      case UnreachableMootTE(innerExpr) => {
        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        val innerWithDeferredsH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, innerExprResultLine, innerDeferreds)

        // Throw away the inner expression because we dont want it to be generated, because
        // theyll never get run anyway.
        // We only translated them above to mark unstackified things unstackified.

        val void = ConstantVoidH()

        (void, Vector.empty)
      }

      case BorrowToWeakTE(innerExpr) => {
        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (BorrowToWeakH(innerExprResultLine), innerDeferreds)
      }

      case PointerToWeakTE(innerExpr) => {
        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (PointerToWeakH(innerExprResultLine), innerDeferreds)
      }

      case NarrowPermissionTE(innerExpr, targetPermission) => {
        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (NarrowPermissionH(innerExprResultLine, Conversions.evaluatePermission(targetPermission)), innerDeferreds)
      }

      case BorrowToPointerTE(innerExpr) => {
        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (BorrowToPointerH(innerExprResultLine), innerDeferreds)
      }

      case PointerToBorrowTE(innerExpr) => {
        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (PointerToBorrowH(innerExprResultLine), innerDeferreds)
      }

      case IsSameInstanceTE(leftExprT, rightExprT) => {
        val (leftExprResultLine, leftDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, leftExprT);
        val (rightExprResultLine, rightDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, rightExprT);
        val resultLine = IsSameInstanceH(leftExprResultLine, rightExprResultLine)

        val expr = translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, resultLine, leftDeferreds ++ rightDeferreds)
        (expr, Vector.empty)
      }

      case AsSubtypeTE(leftExprT, targetSubtype, resultOptType, someConstructor, noneConstructor) => {
        val (resultLine, deferreds) =
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
            resultLine,
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
      translateExpressions(hinputs, hamuts, currentFunctionHeader, locals, deferreds)
    if (deferredExprs.map(_.resultType.kind).toSet != Set(VoidH())) {
      // curiosity, why would a deferred ever have a result
      vfail("ehwot?")
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

  def translateExpressions(
      hinputs: Hinputs, hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      exprs2: Vector[ExpressionT]):
  (Vector[ExpressionH[KindH]], Vector[ExpressionT]) = {
    val (a, b) = translateExpressionsInner(hinputs, hamuts, currentFunctionHeader, locals, exprs2.toList)
    (a.toVector, b.toVector)
  }

  def translateExpressionsInner(
    hinputs: Hinputs, hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    exprs2: List[ExpressionT]):
  (List[ExpressionH[KindH]], List[ExpressionT]) = {
    exprs2 match {
      case Nil => (Nil, Nil)
      case firstExpr :: restExprs => {
        val (firstResultLine, firstDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, firstExpr);
        val (restResultLines, restDeferreds) =
          translateExpressionsInner(hinputs, hamuts, currentFunctionHeader, locals, restExprs);

        val resultLines = firstResultLine :: restResultLines
        (resultLines, restDeferreds ++ firstDeferreds)
      }
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
        val (firstResultLine, firstDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, expr2);
        val firstExprWithDeferredsH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, firstResultLine, firstDeferreds)
        firstExprWithDeferredsH
      })
    Hammer.consecutive(exprs)
  }
}
