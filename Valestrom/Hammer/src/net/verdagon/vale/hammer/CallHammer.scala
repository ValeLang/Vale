package net.verdagon.vale.hammer

import net.verdagon.vale.hammer.ExpressionHammer.translateDeferreds
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.{vassert, vassertSome, vcurious, vfail, vwat, metal => m}
import net.verdagon.vale.metal.{ShareH => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.{FunctionBannerT, FunctionHeaderT, PrototypeT}
import net.verdagon.vale.templar.types._

object CallHammer {

  def translateExternFunctionCall(
    hinputs: Hinputs,
    hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    prototype2: PrototypeT,
    argsExprs2: List[ReferenceExpressionTE]):
  (ExpressionH[KindH]) = {
    val (argsResultLines, argsDeferreds) =
      ExpressionHammer.translateExpressions(
        hinputs, hamuts, currentFunctionHeader, locals, argsExprs2);

    // Doublecheck the types
    val (paramTypes) =
      TypeHammer.translateReferences(hinputs, hamuts, prototype2.paramTypes);
    vassert(argsResultLines.map(_.resultType) == paramTypes)

    val (functionRefH) =
      FunctionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, prototype2);

    val callResultNode = ExternCallH(functionRefH.prototype, argsResultLines)

      ExpressionHammer.translateDeferreds(
        hinputs, hamuts, currentFunctionHeader, locals, callResultNode, argsDeferreds)
  }

  def translateFunctionPointerCall(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      function: PrototypeT,
      args: List[ExpressionT],
      resultType2: CoordT):
  ExpressionH[KindH] = {
    val returnType2 = function.returnType
    val paramTypes = function.paramTypes
    val (argLines, argsDeferreds) =
      ExpressionHammer.translateExpressions(
        hinputs, hamuts, currentFunctionHeader, locals, args);

    val prototypeH =
      FunctionHammer.translatePrototype(hinputs, hamuts, function)

    // Doublecheck the types
    val (paramTypesH) =
      TypeHammer.translateReferences(hinputs, hamuts, paramTypes)
    vassert(argLines.map(_.resultType) == paramTypesH)

    // Doublecheck return
    val (returnTypeH) = TypeHammer.translateReference(hinputs, hamuts, returnType2)
    val (resultTypeH) = TypeHammer.translateReference(hinputs, hamuts, resultType2);
    vassert(returnTypeH == resultTypeH)

    val callResultNode = CallH(prototypeH, argLines)

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, callResultNode, argsDeferreds)
  }

  def translateConstructArray(
      hinputs: Hinputs, hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      constructArray2: ConstructArrayTE):
  (ExpressionH[KindH]) = {
    val ConstructArrayTE(arrayType2, sizeExpr2, generatorExpr2, generatorMethod) = constructArray2;

    val (sizeRegisterId, sizeDeferreds) =
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, sizeExpr2);

    val (generatorRegisterId, generatorDeferreds) =
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, generatorExpr2);

    val (arrayRefTypeH) =
      TypeHammer.translateReference(
        hinputs, hamuts, constructArray2.resultRegister.reference)

    val (arrayTypeH) =
      TypeHammer.translateRuntimeSizedArray(hinputs, hamuts, arrayType2)
    vassert(arrayRefTypeH.expectRuntimeSizedArrayReference().kind == arrayTypeH)

    val elementType = hamuts.getRuntimeSizedArray(arrayTypeH).rawArray.elementType

    val generatorMethodH =
      FunctionHammer.translatePrototype(hinputs, hamuts, generatorMethod)

    val constructArrayCallNode =
        ConstructRuntimeSizedArrayH(
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
        hinputs, hamuts, exprTE.resultRegister.reference)

    val (arrayTypeH) =
      TypeHammer.translateStaticSizedArray(hinputs, hamuts, arrayType2)
    vassert(arrayRefTypeH.expectStaticSizedArrayReference().kind == arrayTypeH)

    val elementType = hamuts.getStaticSizedArray(arrayTypeH).rawArray.elementType

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
      TypeHammer.translateReference(hinputs, hamuts, arrayExpr2.resultRegister.reference)
    vassert(arrayRefTypeH.expectStaticSizedArrayReference().kind == arrayTypeH)

    val (arrayExprResultLine, arrayExprDeferreds) =
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

    val (consumerCallableResultLine, consumerCallableDeferreds) =
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, consumerExpr2);

    val staticSizedArrayDef = hamuts.getStaticSizedArray(arrayTypeH)

    val consumerInterfaceRef = consumerCallableResultLine.expectInterfaceAccess().resultType.kind;
    val consumerInterfaceDef = vassertSome(hamuts.interfaceDefs.values.find(_.getRef == consumerInterfaceRef))
    vassert(consumerInterfaceDef.methods.head.prototypeH.params.size == 2)
    vassert(consumerInterfaceDef.methods.head.prototypeH.params(0).kind == consumerInterfaceRef)
    vassert(consumerInterfaceDef.methods.head.prototypeH.params(1) == staticSizedArrayDef.rawArray.elementType)

    val consumerMethod =
      FunctionHammer.translatePrototype(hinputs, hamuts, consumerMethod2)

    val destroyStaticSizedArrayCallNode =
        DestroyStaticSizedArrayIntoFunctionH(
          arrayExprResultLine.expectStaticSizedArrayAccess(),
          consumerCallableResultLine.expectInterfaceAccess(),
          consumerMethod,
          staticSizedArrayDef.rawArray.elementType,
          staticSizedArrayDef.size)

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, destroyStaticSizedArrayCallNode, consumerCallableDeferreds ++ arrayExprDeferreds)
  }

  def translateDestroyRuntimeSizedArray(
    hinputs: Hinputs,
    hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    das2: DestroyRuntimeSizedArrayTE):
  ExpressionH[KindH] = {
    val DestroyRuntimeSizedArrayTE(arrayExpr2, runtimeSizedArrayType2, consumerExpr2, consumerMethod2) = das2;

//    val RuntimeSizedArrayT2(RawArrayT2(memberType2, mutability)) = runtimeSizedArrayType2

    val (arrayTypeH) =
      TypeHammer.translateRuntimeSizedArray(hinputs, hamuts, runtimeSizedArrayType2)
    val (arrayRefTypeH) =
      TypeHammer.translateReference(hinputs, hamuts, arrayExpr2.resultRegister.reference)
    vassert(arrayRefTypeH.expectRuntimeSizedArrayReference().kind == arrayTypeH)

    val (arrayExprResultLine, arrayExprDeferreds) =
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

    val (consumerCallableResultLine, consumerCallableDeferreds) =
      ExpressionHammer.translate(
        hinputs, hamuts, currentFunctionHeader, locals, consumerExpr2);

    val consumerMethod =
      FunctionHammer.translatePrototype(hinputs, hamuts, consumerMethod2)

    val elementType =
      hamuts.getRuntimeSizedArray(
          arrayExprResultLine.expectRuntimeSizedArrayAccess().resultType.kind)
        .rawArray.elementType

    val destroyStaticSizedArrayCallNode =
        DestroyRuntimeSizedArrayH(
          arrayExprResultLine.expectRuntimeSizedArrayAccess(),
          consumerCallableResultLine.expectInterfaceAccess(),
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

    val (conditionBlockH, Nil) =
      ExpressionHammer.translate(hinputs, hamuts, currentFunctionHeader, parentLocals, condition2);
    vassert(conditionBlockH.resultType == ReferenceH(m.ShareH, InlineH, ReadonlyH, BoolH()))

    val thenLocals = LocalsBox(parentLocals.snapshot)
    val (thenBlockH, Nil) =
      ExpressionHammer.translate(hinputs, hamuts, currentFunctionHeader, thenLocals, thenBlock2);
    val thenResultCoord = thenBlockH.resultType
    parentLocals.setNextLocalIdNumber(thenLocals.nextLocalIdNumber)

    val elseLocals = LocalsBox(parentLocals.snapshot)
    val (elseBlockH, Nil) =
      ExpressionHammer.translate(hinputs, hamuts, currentFunctionHeader, elseLocals, elseBlock2);
    val elseResultCoord = elseBlockH.resultType
    parentLocals.setNextLocalIdNumber(elseLocals.nextLocalIdNumber)

    val commonSupertypeH =
      TypeHammer.translateReference(hinputs, hamuts, if2.resultRegister.reference)

    val ifCallNode = IfH(conditionBlockH.expectBoolAccess(), thenBlockH, elseBlockH, commonSupertypeH)


    val thenContinues = thenResultCoord.kind != NeverH()
    val elseContinues = elseResultCoord.kind != NeverH()

    val localsFromBranchToUseForUnstackifyingParentLocals =
      if (thenContinues == elseContinues) { // Both continue, or both don't
        val parentLocalsAfterThen = thenLocals.locals.keySet -- thenLocals.unstackifiedVars
        val parentLocalsAfterElse = elseLocals.locals.keySet -- elseLocals.unstackifiedVars
        // The same outside-if variables should still exist no matter which branch we went down.
        vassert(parentLocalsAfterThen == parentLocalsAfterElse)
        // Since theyre the same, just arbitrarily use the then.
        thenLocals
      } else {
        // One of them continues and the other does not.
        if (thenContinues) {
          // Throw away any information from the else. But do consider those from the then.
          thenLocals
        } else if (elseContinues) {
          elseLocals
        } else vfail()
      }

    val parentLocalsToUnstackify =
      // All the parent locals...
      parentLocals.locals.keySet
        // ...minus the ones that were unstackified before...
        .diff(parentLocals.unstackifiedVars)
        // ...which were unstackified by the branch.
        .intersect(localsFromBranchToUseForUnstackifyingParentLocals.unstackifiedVars)
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

    val boolExpr = expr.expectBoolAccess()

    val whileCallNode = WhileH(boolExpr)
    whileCallNode
  }

  def translateInterfaceFunctionCall(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      superFunctionHeader: FunctionHeaderT,
      resultType2: CoordT,
      argsExprs2: List[ExpressionT]):
  ExpressionH[KindH] = {
    val (argLines, argsDeferreds) =
      ExpressionHammer.translateExpressions(
        hinputs, hamuts, currentFunctionHeader, locals, argsExprs2);

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
          argLines,
          virtualParamIndex,
          interfaceRefH,
          indexInEdge,
          prototypeH)

    ExpressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, callNode, argsDeferreds)

    //
//    val (callResultLine) =
//      superFamilyRootBanner.params.zipWithIndex.collectFirst({
//        case (Parameter2(_, Some(_), Coord(_, interfaceTT : InterfaceRef2)), paramIndex) => {
//          val (interfaceRefH) =
//            StructHammer.translateInterfaceRef(hinputs, hamuts, currentFunctionHeader, interfaceTT)
//
//          val (functionNodeLine) =
//            translateInterfaceFunctionCallWithInterface(
//              hinputs,
//              hamuts,
//              nodesByLine,
//              superFamilyRootBanner,
//              paramIndex,
//              interfaceRefH,
//              functionTypeH,
//              argLines)
//          (functionNodeLine)
//        }
//        case (Parameter2(_, Some(_), Coord(_, structTT@ structTT(_))), _) => {
//          val (functionRegister) =
//            translateInterfaceFunctionLookupWithStruct(
//              hinputs,
//              hamuts,
//              nodesByLine,
//              structTT,
//              superFamilyRootBanner)
//          val callResultNode =
//            addNode(
//              nodesByLine,
//              CallH(
//                nodesByLine.nextId(),
//                functionRegister,
//                argLines));
//
//          val returnType2 = functionRegister.expectedType.expectFunctionReference().innerType.returnType
//          val access =
//            if (returnType2 == ReferenceH(m.Share, VoidH())) {
//              None
//            } else {
//              Some(NodeH(callResultNode.registerId, returnType2))
//            }
//          (access)
//        }
//      }).get
//
//
//      ExpressionHammer.translateDeferreds(
//        hinputs, hamuts, currentFunctionHeader, locals, argsDeferreds)
//    (callResultLine)
  }
//
//  private def translateInterfaceFunctionLookupWithStruct(
//      hinputs: Hinputs,
//      hamuts: HamutsBox,
//      nodesByLine: NodesBox,
//      structTT: structTT,
//      superFamilyRootBanner: FunctionBanner2):
//  (Vector[NodeH], NodeH[FunctionTH]) = {
//    val prototype2 =
//      getPrototypeForStructInterfaceCall(hinputs, structTT, superFamilyRootBanner)
//
//    val (functionRefH) =
//      FunctionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, prototype2);
//    val functionNode =
//      addNode(
//        nodesByLine,
//        LoadFunctionH(nodesByLine.nextId(), functionRefH));
//    val access = NodeH(functionNode.registerId, ReferenceH(m.Raw, functionRefH.functionType))
//    (access)
//  }
//
//  private def translateInterfaceFunctionCallWithInterface(
//      hinputs: Hinputs,
//      hamuts: HamutsBox,
//      nodesByLine: NodesBox,
//      superFamilyRootBanner: FunctionBanner2,
//      firstVirtualParamIndex: Int,
//      firstVirtualParamInterface: InterfaceRefH,
//      functionTypeH: FunctionTH,
//      argLines: List[NodeH[KindH]]):
//  (Vector[NodeH], Option[NodeH[KindH]]) = {
//    val interfaceId = firstVirtualParamInterface.interfaceId
//
//    val edgeBlueprint =
//      hinputs.edgeBlueprintsByInterfaceId(interfaceId)
//    val indexInEdge =
//      edgeBlueprint.superFamilyRootBanners.indexOf(superFamilyRootBanner)
//    if (indexInEdge < 0) {
//      vfail("Can't find:\n" + superFamilyRootBanner + "\nin:\n" + edgeBlueprint.interface)
//    }
//
//    val methodNode =
//      addNode(
//        nodesByLine,
//        InterfaceCallH(
//          nodesByLine.nextId(),
//          argLines,
//          firstVirtualParamIndex,
//          firstVirtualParamInterface,
//          interfaceId,
//          indexInEdge,
//          functionTypeH));
//
//    val access =
//      if (functionTypeH.returnType == ReferenceH(m.Share, VoidH())) {
//        None
//      } else {
//        Some(NodeH(methodNode.registerId, functionTypeH.returnType))
//      }
//    (access)
//  }

//  private def getPrototypeForStructInterfaceCall(
//      hinputs: Hinputs,
//      structTT: structTT,
//      superFamilyRootBanner: FunctionBanner2):
//  Prototype2 = {
//
//    val structDefT = hinputs.lookupStruct(structTT)
//    val ancestorInterfaces2 =
//      hinputs.impls.filter(impl => impl.struct == structDefT.getRef).map(_.interface)
//    val edgeBlueprints = ancestorInterfaces2.map(hinputs.edgeBlueprintsByInterface)
//    val matchingEdgeBlueprint =
//      edgeBlueprints.find(_.superFamilyRootBanners.contains(superFamilyRootBanner)).get;
//
//    val indexInEdgeBlueprint = matchingEdgeBlueprint.superFamilyRootBanners.indexOf(superFamilyRootBanner);
//    vassert(indexInEdgeBlueprint >= 0);
//
//    val edge =
//      hinputs.edges.find(
//        edge => edge.interface == matchingEdgeBlueprint.interface && edge.struct == structTT).get;
//    val methodPrototype2 = edge.methods(indexInEdgeBlueprint)
//    methodPrototype2
//  }

}
