package net.verdagon.vale.hammer

import net.verdagon.vale.hammer.ExpressionHammer.translateDeferreds
import net.verdagon.vale.{vassert, vassertSome, vcurious, vfail, vwat, metal => m}
import net.verdagon.vale.metal.{ShareH => _, _}
import net.verdagon.vale.templar.{Hinputs, _}
import net.verdagon.vale.templar.ast.{DestroyImmRuntimeSizedArrayTE, DestroyStaticSizedArrayIntoFunctionTE, ExpressionT, FunctionHeaderT, IfTE, NewImmRuntimeSizedArrayTE, NewMutRuntimeSizedArrayTE, PrototypeT, ReferenceExpressionTE, StaticArrayFromCallableTE, WhileTE}
import net.verdagon.vale.templar.types._

object CallHammer {

  def translateExternFunctionCall(
    hinputs: Hinputs,
    hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    prototype2: PrototypeT,
    argsExprs2: Vector[ReferenceExpressionTE]):
  (ExpressionH[KindH]) = {
    val (argsHE, argsDeferreds) =
      ExpressionHammer.translateExpressionsUntilNever(
        hinputs, hamuts, currentFunctionHeader, locals, argsExprs2);
    // Don't evaluate anything that can't ever be run, see BRCOBS
    if (argsHE.nonEmpty && argsHE.last.resultType.kind == NeverH()) {
      return Hammer.consecrash(locals, argsHE)
    }

    // Doublecheck the types
    val (paramTypes) =
      TypeHammer.translateReferences(hinputs, hamuts, prototype2.paramTypes);
    vassert(argsHE.map(_.resultType) == paramTypes)

    val (functionRefH) =
      FunctionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, prototype2);

    val callResultNode = ExternCallH(functionRefH.prototype, argsHE)

      ExpressionHammer.translateDeferreds(
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
      ExpressionHammer.translateExpressionsUntilNever(
        hinputs, hamuts, currentFunctionHeader, locals, args);
    // Don't evaluate anything that can't ever be run, see BRCOBS
    if (argsHE.nonEmpty && argsHE.last.resultType.kind == NeverH()) {
      return Hammer.consecrash(locals, argsHE)
    }

    val prototypeH =
      FunctionHammer.translatePrototype(hinputs, hamuts, function)

    // Doublecheck the types
    val (paramTypesH) =
      TypeHammer.translateReferences(hinputs, hamuts, paramTypes)
    vassert(argsHE.map(_.resultType) == paramTypesH)

    // Doublecheck return
    val (returnTypeH) = TypeHammer.translateReference(hinputs, hamuts, returnType2)
    val (resultTypeH) = TypeHammer.translateReference(hinputs, hamuts, resultType2);
    vassert(returnTypeH == resultTypeH)

    val callResultNode = CallH(prototypeH, argsHE)

    ExpressionHammer.translateDeferreds(
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
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, capacityExpr2);

    val (arrayRefTypeH) =
      TypeHammer.translateReference(
        hinputs, hamuts, constructArray2.result.reference)

    val (arrayTypeH) =
      TypeHammer.translateRuntimeSizedArray(hinputs, hamuts, arrayType2)
    vassert(arrayRefTypeH.expectRuntimeSizedArrayReference().kind == arrayTypeH)

    val elementType = hamuts.getRuntimeSizedArray(arrayTypeH).elementType

    val constructArrayCallNode =
      NewMutRuntimeSizedArrayH(
        capacityRegisterId.expectIntAccess(),
        elementType,
        arrayRefTypeH.expectRuntimeSizedArrayReference())

    ExpressionHammer.translateDeferreds(
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
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, sizeExpr2);

    val (generatorRegisterId, generatorDeferreds) =
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, generatorExpr2);

    val (arrayRefTypeH) =
      TypeHammer.translateReference(
        hinputs, hamuts, constructArray2.result.reference)

    val (arrayTypeH) =
      TypeHammer.translateRuntimeSizedArray(hinputs, hamuts, arrayType2)
    vassert(arrayRefTypeH.expectRuntimeSizedArrayReference().kind == arrayTypeH)

    val elementType = hamuts.getRuntimeSizedArray(arrayTypeH).elementType

    val generatorMethodH =
      FunctionHammer.translatePrototype(hinputs, hamuts, generatorMethod)

    val constructArrayCallNode =
        NewImmRuntimeSizedArrayH(
          sizeRegisterId.expectIntAccess(),
          generatorRegisterId,
          generatorMethodH,
          elementType,
          arrayRefTypeH.expectRuntimeSizedArrayReference())

    ExpressionHammer.translateDeferreds(
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
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, generatorExpr2);

    val (arrayRefTypeH) =
      TypeHammer.translateReference(
        hinputs, hamuts, exprTE.result.reference)

    val (arrayTypeH) =
      TypeHammer.translateStaticSizedArray(hinputs, hamuts, arrayType2)
    vassert(arrayRefTypeH.expectStaticSizedArrayReference().kind == arrayTypeH)

    val elementType = hamuts.getStaticSizedArray(arrayTypeH).elementType

    val generatorMethodH =
      FunctionHammer.translatePrototype(hinputs, hamuts, generatorMethod)

    val constructArrayCallNode =
      StaticArrayFromCallableH(
        generatorRegisterId,
        generatorMethodH,
        elementType,
        arrayRefTypeH.expectStaticSizedArrayReference())

    ExpressionHammer.translateDeferreds(
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
      TypeHammer.translateStaticSizedArray(hinputs, hamuts, staticSizedArrayType)
    val (arrayRefTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, arrayExpr2.result.reference)
    vassert(arrayRefTypeH.expectStaticSizedArrayReference().kind == arrayTypeH)

    val (arrayExprResultHE, arrayExprDeferreds) =
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

    val (consumerCallableResultHE, consumerCallableDeferreds) =
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, consumerExpr2);

    val staticSizedArrayDef = hamuts.getStaticSizedArray(arrayTypeH)

    val consumerMethod =
      FunctionHammer.translatePrototype(hinputs, hamuts, consumerMethod2)

    val destroyStaticSizedArrayCallNode =
        DestroyStaticSizedArrayIntoFunctionH(
          arrayExprResultHE.expectStaticSizedArrayAccess(),
          consumerCallableResultHE,
          consumerMethod,
          staticSizedArrayDef.elementType,
          staticSizedArrayDef.size)

    ExpressionHammer.translateDeferreds(
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
      TypeHammer.translateRuntimeSizedArray(hinputs, hamuts, runtimeSizedArrayType2)
    val (arrayRefTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, arrayExpr2.result.reference)
    vassert(arrayRefTypeH.expectRuntimeSizedArrayReference().kind == arrayTypeH)

    val (arrayExprResultHE, arrayExprDeferreds) =
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

    val (consumerCallableResultHE, consumerCallableDeferreds) =
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, consumerExpr2);

    val consumerMethod =
      FunctionHammer.translatePrototype(hinputs, hamuts, consumerMethod2)

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

    ExpressionHammer.translateDeferreds(
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
      ExpressionHammer.translate(hinputs, hamuts, currentFunctionHeader, parentLocals, condition2);
    vassert(conditionBlockH.resultType == ReferenceH(m.ShareH, InlineH, ReadonlyH, BoolH()))

    val thenLocals = LocalsBox(parentLocals.snapshot)
    val (thenBlockH, Vector()) =
      ExpressionHammer.translate(hinputs, hamuts, currentFunctionHeader, thenLocals, thenBlock2);
    val thenResultCoord = thenBlockH.resultType
    parentLocals.setNextLocalIdNumber(thenLocals.nextLocalIdNumber)

    val elseLocals = LocalsBox(parentLocals.snapshot)
    val (elseBlockH, Vector()) =
      ExpressionHammer.translate(hinputs, hamuts, currentFunctionHeader, elseLocals, elseBlock2);
    val elseResultCoord = elseBlockH.resultType
    parentLocals.setNextLocalIdNumber(elseLocals.nextLocalIdNumber)

    val commonSupertypeH =
      TypeHammer.translateReference(hinputs, hamuts, if2.result.reference)

    val ifCallNode = IfH(conditionBlockH.expectBoolAccess(), thenBlockH, elseBlockH, commonSupertypeH)


    val thenContinues = thenResultCoord.kind != NeverH()
    val elseContinues = elseResultCoord.kind != NeverH()

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
      ExpressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, bodyExpr2);
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
      ExpressionHammer.translateExpressionsUntilNever(
        hinputs, hamuts, currentFunctionHeader, locals, argsExprs2);
    // Don't evaluate anything that can't ever be run, see BRCOBS
    if (argsHE.nonEmpty && argsHE.last.resultType.kind == NeverH()) {
      return Hammer.consecrash(locals, argsHE)
    }

    val virtualParamIndex = superFunctionHeader.getVirtualIndex.get
    val CoordT(_, _, interfaceTT @ InterfaceTT(_)) =
      superFunctionHeader.paramTypes(virtualParamIndex)
    val (interfaceRefH) =
      StructHammer.translateInterfaceRef(hinputs, hamuts, interfaceTT)
    val edge = hinputs.edgeBlueprintsByInterface(interfaceTT)
    vassert(edge.interface == interfaceTT)
    val indexInEdge = edge.superFamilyRootBanners.indexOf(superFunctionHeader.toBanner)
    vassert(indexInEdge >= 0)

    val (prototypeH) = FunctionHammer.translatePrototype(hinputs, hamuts, superFunctionHeader.toPrototype)

    val callNode =
        InterfaceCallH(
          argsHE,
          virtualParamIndex,
          interfaceRefH,
          indexInEdge,
          prototypeH)

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, callNode, argsDeferreds)
  }
}
