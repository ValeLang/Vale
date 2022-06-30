package dev.vale.parsing

import dev.vale.lexing.{BadVPSTError, BadVPSTException, IParseError, RangeL}
import dev.vale.{Err, FileCoordinate, Interner, Ok, PackageCoordinate, Profiler, Result, StrI, vimpl, vwat}
import dev.vale.parsing.ast.{AbstractAttributeP, AbstractP, AndPE, AnonymousRunePT, ArenaRuneAttributeP, AugmentPE, BinaryCallPE, BlockPE, BoolTypePR, BorrowP, BorrowPT, BraceCallPE, BreakPE, BuiltinAttributeP, BuiltinCallPR, BumpRuneAttributeP, CallMacroP, CallPT, CitizenTemplateTypePR, ComponentsPR, ConsecutorPE, ConstantBoolPE, ConstantFloatPE, ConstantIntPE, ConstantStrPE, ConstructArrayPE, ConstructingMemberNameDeclarationP, CoordListTypePR, CoordTypePR, DestructPE, DestructureP, DontCallMacroP, DotPE, EachPE, EqualsPR, ExportAsP, ExportAttributeP, ExternAttributeP, FileP, FinalP, FunctionCallPE, FunctionHeaderP, FunctionP, FunctionReturnP, IArraySizeP, IAttributeP, IExpressionPE, IImpreciseNameP, INameDeclarationP, IRulexPR, IRuneAttributeP, IStructContent, ITemplexPT, ITypePR, IdentifyingRuneP, IdentifyingRunesP, IfPE, IgnoredLocalNameDeclarationP, ImmutableP, ImmutableRuneAttributeP, ImplP, ImportP, IndexPE, InlinePT, IntPT, IntTypePR, InterfaceP, InterpretedPT, IterableNameDeclarationP, IterableNameP, IterationOptionNameDeclarationP, IterationOptionNameP, IteratorNameDeclarationP, IteratorNameP, KindTypePR, LambdaPE, LetPE, LoadAsBorrowP, LoadAsP, LoadAsWeakP, LocalNameDeclarationP, LocationTypePR, LookupNameP, LookupPE, MacroCallP, MagicParamLookupPE, MethodCallPE, MoveP, MutabilityP, MutabilityPT, MutabilityTypePR, MutableP, MutatePE, NameOrRunePT, NameP, NormalStructMemberP, NotPE, OrPE, OrPR, OwnP, OwnershipP, OwnershipPT, OwnershipTypePR, PackPE, PackPT, ParamsP, PatternPP, PoolRuneAttributeP, PrototypeTypePR, PureAttributeP, RangePE, ReadOnlyRuneAttributeP, ReadWriteRuneAttributeP, RegionRunePT, RegionTypePR, ReturnPE, RuntimeSizedArrayPT, RuntimeSizedP, SealedAttributeP, ShareP, ShortcallPE, StaticSizedArrayPT, StaticSizedP, StrInterpolatePE, StringPT, StructMembersP, StructMethodP, StructP, SubExpressionPE, TemplateArgsP, TemplateRulesP, TemplexPR, TopLevelExportAsP, TopLevelFunctionP, TopLevelImplP, TopLevelImportP, TopLevelInterfaceP, TopLevelStructP, TuplePE, TuplePT, TypeRuneAttributeP, TypedPR, UnitP, UnletPE, UseP, VariabilityP, VariabilityPT, VariabilityTypePR, VariadicStructMemberP, VaryingP, VoidPE, WeakP, WeakableAttributeP, WhilePE}
import net.liftweb.json._
import dev.vale.parsing.ast._

class ParsedLoader(interner: Interner) {
  def expectObject(obj: Object): JObject = {
    if (!obj.isInstanceOf[JObject]) {
      throw BadVPSTException(BadVPSTError("Expected JSON object, got: " + obj.getClass.getSimpleName))
    }
    obj.asInstanceOf[JObject]
  }
  def expectString(obj: Object): JString = {
    if (!obj.isInstanceOf[JString]) {
      throw BadVPSTException(BadVPSTError("Expected JSON string, got: " + obj.getClass.getSimpleName))
    }
    obj.asInstanceOf[JString]
  }
  def expectNumber(obj: Object): BigInt = {
    if (!obj.isInstanceOf[JInt]) {
      throw BadVPSTException(BadVPSTError("Expected JSON number, got: " + obj.getClass.getSimpleName))
    }
    obj.asInstanceOf[JInt].num
  }
  def expectObjectTyped(obj: JValue, expectedType: String): JObject = {
    val jobj = expectObject(obj)
    val actualType = getStringField(jobj, "__type")
    if (!actualType.equals(expectedType)) {
      throw BadVPSTException(BadVPSTError("Expected " + expectedType + " but got a " + actualType))
    }
    jobj
  }
  def getField(jobj: JValue, fieldName: String): JValue = {
    (jobj \ fieldName) match {
      case JNothing => throw BadVPSTException(BadVPSTError("Object had no field named " + fieldName))
      case other => other
    }
  }
  def getObjectField(containerJobj: JObject, fieldName: String): JObject = {
    expectObject(getField(containerJobj, fieldName))
  }
  def getObjectField(containerJobj: JObject, fieldName: String, expectedType: String): JObject = {
    val jobj = expectObject(getField(containerJobj, fieldName))
    expectType(jobj, expectedType)
    jobj
  }
  def getStringField(jobj: JObject, fieldName: String): String = {
    getField(jobj, fieldName) match {
      case JString(s) => {
        s
      }
      case _ => throw BadVPSTException(BadVPSTError("Field " + fieldName + " wasn't a string!"))
    }
  }
  def getIntField(jobj: JObject, fieldName: String): Int = {
    getField(jobj, fieldName) match {
      case JInt(s) => s.toInt
      case _ => throw BadVPSTException(BadVPSTError("Field " + fieldName + " wasn't a number!"))
    }
  }
  def getLongField(jobj: JObject, fieldName: String): Long = {
    getField(jobj, fieldName) match {
      case JInt(s) => s.toLong
      case JString(s) if s.toLong.toString == s => s.toLong
      case _ => throw BadVPSTException(BadVPSTError("Field " + fieldName + " wasn't a number!"))
    }
  }
  def getFloatField(jobj: JObject, fieldName: String): Double = {
    getField(jobj, fieldName) match {
      case JDouble(s) => s
      case _ => throw BadVPSTException(BadVPSTError("Field " + fieldName + " wasn't a double!"))
    }
  }
  def getBooleanField(jobj: JObject, fieldName: String): Boolean = {
    getField(jobj, fieldName) match {
      case JBool(b) => b
      case _ => throw BadVPSTException(BadVPSTError("Field " + fieldName + " wasn't a boolean!"))
    }
  }
  def getArrayField(jobj: JObject, fieldName: String): Vector[JValue] = {
    getField(jobj, fieldName) match {
      case JArray(arr) => arr.toVector
      case _ => throw BadVPSTException(BadVPSTError("Field " + fieldName + " wasn't an array!"))
    }
  }
  def expectType(jobj: JObject, expectedType: String): Unit = {
    val actualType = getType(jobj)
    if (!actualType.equals(expectedType)) {
      throw BadVPSTException(BadVPSTError("Expected " + expectedType + " but got a " + actualType))
    }
  }
  def getType(jobj: JObject): String = {
    getStringField(jobj, "__type")
  }
  def loadRange(jobj: JObject): RangeL = {
    expectType(jobj, "Range")
    RangeL(
      getIntField(jobj, "begin"),
      getIntField(jobj, "end"))
  }
  def loadName(jobj: JObject): NameP = {
    expectType(jobj, "Name")
    NameP(
      loadRange(getObjectField(jobj, "range")),
      interner.intern(StrI(getStringField(jobj, "name"))))
  }

  def load(source: String): Result[FileP, IParseError] = {
    Profiler.frame(() => {
      try {
        val jfile = expectObjectTyped(parse(source), "File")
        Ok(
          FileP(
            FileCoordinate(
              PackageCoordinate(
                interner.intern(StrI(getStringField(jfile, "module"))),
                getArrayField(jfile, "packages").map(expectString).map(s => interner.intern(StrI(s.s)))),
              getStringField(jfile, "filepath")),
            getArrayField(jfile, "commentsRanges").map(expectObject).map(x => loadRange(x)).toArray,
            getArrayField(jfile, "denizens").map(expectObject).map(denizen => {
              getType(denizen) match {
                case "Struct" => TopLevelStructP(loadStruct(denizen))
                case "Interface" => TopLevelInterfaceP(loadInterface(denizen))
                case "Function" => TopLevelFunctionP(loadFunction(denizen))
                case "Impl" => TopLevelImplP(loadImpl(denizen))
                case "Import" => TopLevelImportP(loadImport(denizen))
                case "ExportAs" => TopLevelExportAsP(loadExportAs(denizen))
                case x => vimpl(x.toString)
              }
            }).toArray))
      } catch {
        case BadVPSTException(err) => Err(err)
      }
    })
  }

  def loadFunction(denizen: JObject) = {
    FunctionP(
      loadRange(getObjectField(denizen, "range")),
      loadFunctionHeader(getObjectField(denizen, "header")),
      loadOptionalObject(getObjectField(denizen, "body"), loadBlock))
  }

  private def loadImpl(jobj: JObject) = {
    ImplP(
      loadRange(getObjectField(jobj, "range")),
      loadOptionalObject(getObjectField(jobj, "identifyingRunes"), loadIdentifyingRunes),
      loadOptionalObject(getObjectField(jobj, "templateRules"), loadTemplateRules),
      loadOptionalObject(getObjectField(jobj, "struct"), loadTemplex),
      loadTemplex(getObjectField(jobj, "interface")),
      getArrayField(jobj, "attributes").map(expectObject).map(loadAttribute))
  }

  private def loadExportAs(jobj: JObject) = {
    ExportAsP(
      loadRange(getObjectField(jobj, "range")),
      loadTemplex(getObjectField(jobj, "struct")),
      loadName(getObjectField(jobj, "exportedName")))
  }

  private def loadImport(jobj: JObject) = {
    ImportP(
      loadRange(getObjectField(jobj, "range")),
      loadName(getObjectField(jobj, "moduleName")),
      getArrayField(jobj, "packageSteps").map(expectObject).map(loadName),
      loadName(getObjectField(jobj, "importeeName")))
  }

  private def loadStruct(jobj: JObject) = {
    StructP(
      loadRange(getObjectField(jobj, "range")),
      loadName(getObjectField(jobj, "name")),
      getArrayField(jobj, "attributes").map(expectObject).map(loadAttribute),
      loadOptionalObject(getObjectField(jobj, "mutability"), loadTemplex),
      loadOptionalObject(getObjectField(jobj, "identifyingRunes"), loadIdentifyingRunes),
      loadOptionalObject(getObjectField(jobj, "templateRules"), loadTemplateRules),
      loadRange(getObjectField(jobj, "bodyRange")),
      loadStructMembers(getObjectField(jobj, "members")))
  }

  private def loadInterface(denizen: JObject) = {
    InterfaceP(
      loadRange(getObjectField(denizen, "range")),
      loadName(getObjectField(denizen, "name")),
      getArrayField(denizen, "attributes").map(expectObject).map(loadAttribute),
//      getArrayField(denizen, "attributes").map(expectObject).map(loadCitizenAttribute),
      loadOptionalObject(getObjectField(denizen, "mutability"), loadTemplex),
      loadOptionalObject(getObjectField(denizen, "maybeIdentifyingRunes"), loadIdentifyingRunes),
      loadOptionalObject(getObjectField(denizen, "templateRules"), loadTemplateRules),
      loadRange(getObjectField(denizen, "bodyRange")),
      getArrayField(denizen, "members").map(expectObject).map(loadFunction))
  }

  def loadFunctionHeader(jobj: JObject): FunctionHeaderP = {
    FunctionHeaderP(
      loadRange(getObjectField(jobj, "range")),
      loadOptionalObject(getObjectField(jobj, "name"), loadName),
      getArrayField(jobj, "attributes").map(expectObject).map(loadAttribute),
      loadOptionalObject(getObjectField(jobj, "maybeUserSpecifiedIdentifyingRunes"), loadIdentifyingRunes),
      loadOptionalObject(getObjectField(jobj, "templateRules"), loadTemplateRules),
      loadOptionalObject(getObjectField(jobj, "params"), loadParams),
      loadFunctionReturn(getObjectField(jobj, "return")))
  }

  def loadParams(jobj: JObject): ParamsP = {
    ast.ParamsP(
      loadRange(getObjectField(jobj, "range")),
      getArrayField(jobj, "patterns").map(expectObject).map(loadPattern))
  }

  def loadPattern(jobj: JObject): PatternPP = {
    ast.PatternPP(
      loadRange(getObjectField(jobj, "range")),
      loadOptionalObject(getObjectField(jobj, "preBorrow"), loadRange),
      loadOptionalObject(getObjectField(jobj, "capture"), loadNameDeclaration),
      loadOptionalObject(getObjectField(jobj, "templex"), loadTemplex),
      loadOptionalObject(getObjectField(jobj, "destructure"), loadDestructure),
      loadOptionalObject(getObjectField(jobj, "virtuality"), loadVirtuality))
  }

  def loadDestructure(jobj: JObject): DestructureP = {
    DestructureP(
      loadRange(getObjectField(jobj, "range")),
      getArrayField(jobj, "patterns").map(expectObject).map(loadPattern))
  }

  def loadNameDeclaration(jobj: JObject): INameDeclarationP = {
    getType(jobj) match {
      case "IgnoredLocalNameDeclaration" => IgnoredLocalNameDeclarationP(loadRange(getObjectField(jobj, "range")))
      case "LocalNameDeclaration" => LocalNameDeclarationP(loadName(getObjectField(jobj, "name")))
      case "IterableNameDeclaration" => IterableNameDeclarationP(loadRange(getObjectField(jobj, "range")))
      case "IteratorNameDeclaration" => IteratorNameDeclarationP(loadRange(getObjectField(jobj, "range")))
      case "IterationOptionNameDeclaration" => IterationOptionNameDeclarationP(loadRange(getObjectField(jobj, "range")))
      case "ConstructingMemberNameDeclaration" => ConstructingMemberNameDeclarationP(loadName(getObjectField(jobj, "name")))
    }
  }

  def loadImpreciseName(jobj: JObject): IImpreciseNameP = {
    getType(jobj) match {
      case "LookupName" => LookupNameP(loadName(getObjectField(jobj, "name")))
      case "IterableName" => IterableNameP(loadRange(getObjectField(jobj, "range")))
      case "IteratorName" => IteratorNameP(loadRange(getObjectField(jobj, "range")))
      case "IterationOptionName" => IterationOptionNameP(loadRange(getObjectField(jobj, "range")))
    }
  }

  def loadCaptureName(jobj: JObject): INameDeclarationP = {
    getType(jobj) match {
      case "LocalName" => {
        LocalNameDeclarationP(
          loadName(getObjectField(jobj, "name")))
      }
      case "ConstructingMemberName" => {
        ConstructingMemberNameDeclarationP(
          loadName(getObjectField(jobj, "name")))
      }
    }
  }

  def loadBlock(jobj: JObject): BlockPE = {
    BlockPE(
      loadRange(getObjectField(jobj, "range")),
      loadExpression(getObjectField(jobj, "inner")))
  }

  def loadConsecutor(jobj: JObject): ConsecutorPE = {
    ConsecutorPE(
      getArrayField(jobj, "inners").map(expectObject).map(loadExpression))
  }

  def loadFunctionReturn(jobj: JObject): FunctionReturnP = {
    FunctionReturnP(
      loadRange(getObjectField(jobj, "range")),
      loadOptionalObject(getObjectField(jobj, "inferRet"), loadRange),
      loadOptionalObject(getObjectField(jobj, "retType"), loadTemplex))
  }

  def loadUnit(jobj: JObject): UnitP = {
    UnitP(
      loadRange(getObjectField(jobj, "range")))
  }

  def loadStructMembers(jobj: JObject): StructMembersP = {
    StructMembersP(
      loadRange(getObjectField(jobj, "range")),
      getArrayField(jobj, "members").map(expectObject).map(loadStructContent))
  }

  def loadExpression(jobj: JObject): IExpressionPE = {
    getType(jobj) match {
      case "Pack" => {
        PackPE(
          loadRange(getObjectField(jobj, "range")),
          getArrayField(jobj, "innerExprs").map(expectObject).map(loadExpression))
      }
      case "FunctionCall" => {
        FunctionCallPE(
          loadRange(getObjectField(jobj, "range")),
          loadRange(getObjectField(jobj, "operatorRange")),
          loadExpression(getObjectField(jobj, "callableExpr")),
          getArrayField(jobj, "argExprs").map(expectObject).map(loadExpression))
      }
      case "BraceCall" => {
        BraceCallPE(
          loadRange(getObjectField(jobj, "range")),
          loadRange(getObjectField(jobj, "operatorRange")),
          loadExpression(getObjectField(jobj, "callableExpr")),
          getArrayField(jobj, "argExprs").map(expectObject).map(loadExpression),
          getBooleanField(jobj, "callableReadwrite"))
      }
      case "BinaryCall" => {
        BinaryCallPE(
          loadRange(getObjectField(jobj, "range")),
          loadName(getObjectField(jobj, "functionName")),
          loadExpression(getObjectField(jobj, "leftExpr")),
          loadExpression(getObjectField(jobj, "rightExpr")))
      }
      case "MethodCall" => {
        MethodCallPE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "subjectExpr")),
          loadRange(getObjectField(jobj, "operatorRange")),
          loadLookup(getObjectField(jobj, "method")),
          getArrayField(jobj, "argExprs").map(expectObject).map(loadExpression))
      }
      case "Shortcall" => {
        ShortcallPE(
          loadRange(getObjectField(jobj, "range")),
          getArrayField(jobj, "argExprs").map(expectObject).map(loadExpression))
      }
      case "Lookup" => {
        loadLookup(jobj)
      }
      case "MagicParamLookup" => {
        MagicParamLookupPE(
          loadRange(getObjectField(jobj, "range")))
      }
      case "ConstantInt" => {
        ConstantIntPE(
          loadRange(getObjectField(jobj, "range")),
          getLongField(jobj, "value"),
          loadOptionalObject(getObjectField(jobj, "bits"), expectNumber).map(_.toInt))
      }
      case "ConstantFloat" => {
        ConstantFloatPE(
          loadRange(getObjectField(jobj, "range")),
          getFloatField(jobj, "value"))
      }
      case "ConstantStr" => {
        ConstantStrPE(
          loadRange(getObjectField(jobj, "range")),
          getStringField(jobj, "value"))
      }
      case "ConstantBool" => {
        ConstantBoolPE(
          loadRange(getObjectField(jobj, "range")),
          getBooleanField(jobj, "value"))
      }
      case "Void" => {
        VoidPE(
          loadRange(getObjectField(jobj, "range")))
      }
      case "Dot" => {
        DotPE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "left")),
          loadRange(getObjectField(jobj, "operatorRange")),
          loadName(getObjectField(jobj, "member")))
      }
      case "Lambda" => {
        LambdaPE(
          loadOptionalObject(getObjectField(jobj, "captures"), loadUnit),
          loadFunction(getObjectField(jobj, "function")))
      }
      case "Let" => {
        LetPE(
          loadRange(getObjectField(jobj, "range")),
//          getArrayField(jobj, "attributes").map(expectObject).map(loadAttribute),
//          loadOptionalObject(getObjectField(jobj, "templateRules"), loadTemplateRules),
          loadPattern(getObjectField(jobj, "pattern")),
          loadExpression(getObjectField(jobj, "source")))
      }
      case "Augment" => {
        AugmentPE(
          loadRange(getObjectField(jobj, "range")),
          loadOwnership(getObjectField(jobj, "targetOwnership")),
          loadExpression(getObjectField(jobj, "inner")))
      }
      case "Mutate" => {
        MutatePE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "mutatee")),
          loadExpression(getObjectField(jobj, "source")))
      }
      case "Return" => {
        ReturnPE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "expr")))
      }
      case "Break" => {
        BreakPE(
          loadRange(getObjectField(jobj, "range")))
      }
      case "Consecutor" => {
        loadConsecutor(jobj)
      }
      case "Block" => {
        loadBlock(jobj)
//        BlockPE(
//          loadRange(getObjectField(jobj, "range")),
//          getArrayField(jobj, "elements").map(expectObject).map(loadExpression))
      }
      case "If" => {
        IfPE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "condition")),
          loadBlock(getObjectField(jobj, "thenBody")),
          loadBlock(getObjectField(jobj, "elseBody")))
      }
      case "While" => {
        WhilePE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "condition")),
          loadBlock(getObjectField(jobj, "body")))
      }
      case "Index" => {
        IndexPE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "left")),
          getArrayField(jobj, "args").map(expectObject).map(loadExpression))
      }
      case "Tuple" => {
        TuplePE(
          loadRange(getObjectField(jobj, "range")),
          getArrayField(jobj, "elements").map(expectObject).map(loadExpression))
      }
      case "ConstructArray" => {
        loadConstructArray(jobj)
      }
      case "Destruct" => {
        DestructPE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "inner")))
      }
      case "Unlet" => {
        UnletPE(
          loadRange(getObjectField(jobj, "range")),
          loadImpreciseName(getObjectField(jobj, "localName")))
      }
      case "Each" => {
        EachPE(
          loadRange(getObjectField(jobj, "range")),
          loadPattern(getObjectField(jobj, "entryPattern")),
          loadRange(getObjectField(jobj, "inRange")),
          loadExpression(getObjectField(jobj, "iterableExpr")),
          loadBlock(getObjectField(jobj, "body")))
      }
      case "Or" => {
        OrPE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "left")),
          loadBlock(getObjectField(jobj, "right")))
      }
//      case "Result" => {
//        ResultPE(
//          loadRange(getObjectField(jobj, "range")),
//          loadExpression(getObjectField(jobj, "source")))
//      }
      case "And" => {
        AndPE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "left")),
          loadBlock(getObjectField(jobj, "right")))
      }
      case "SubExpression" => {
        SubExpressionPE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "innerExpr")))
      }
      case "Not" => {
        NotPE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "innerExpr")))
      }
      case "Range" => {
        RangePE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "begin")),
          loadExpression(getObjectField(jobj, "end")))
      }
      case "StrInterpolate" => {
        StrInterpolatePE(
          loadRange(getObjectField(jobj, "range")),
          getArrayField(jobj, "parts").map(expectObject).map(loadExpression))
      }
      case x => vimpl(x.toString)
    }
  }

  private def loadArraySize(jobj: JObject): IArraySizeP = {
    getType(jobj) match {
      case "RuntimeSized" => RuntimeSizedP
      case "StaticSized" => {
        StaticSizedP(loadOptionalObject(getObjectField(jobj, "size"), loadTemplex))
      }
    }
  }
  private def loadConstructArray(jobj: JObject) = {
    ConstructArrayPE(
      loadRange(getObjectField(jobj, "range")),
      loadOptionalObject(getObjectField(jobj, "type"), loadTemplex),
      loadOptionalObject(getObjectField(jobj, "mutability"), loadTemplex),
      loadOptionalObject(getObjectField(jobj, "variability"), loadTemplex),
      loadArraySize(getObjectField(jobj, "size")),
      getBooleanField(jobj, "initializingIndividualElements"),
      getArrayField(jobj, "args").map(expectObject).map(loadExpression))
  }

  private def loadLookup(jobj: JObject) = {
    LookupPE(
      loadImpreciseName(getObjectField(jobj, "name")),
      loadOptionalObject(getObjectField(jobj, "templateArgs"), loadTemplateArgs))
  }

  def loadTemplateArgs(jobj: JObject): TemplateArgsP = {
    ast.TemplateArgsP(
      loadRange(getObjectField(jobj, "range")),
      getArrayField(jobj, "args").map(expectObject).map(loadTemplex))
  }

  def loadLoadAs(jobj: JObject): LoadAsP = {
    getType(jobj) match {
      case "Move" => MoveP
      case "Use" => UseP
      case "LoadAsBorrow" => LoadAsBorrowP
      case "LoadAsWeak" => LoadAsWeakP
      case other => vwat(other)
    }
  }

  def loadVirtuality(jobj: JObject): AbstractP = {
//    getType(jobj) match {
//      case "Override" => {
//        OverrideP(
//          loadRange(getObjectField(jobj, "range")),
//          loadTemplex(getObjectField(jobj, "type")))
//      }
//      case "Abstract" => {
        AbstractP(
          loadRange(getObjectField(jobj, "range")))
//      }
//    }
  }

  def loadStructContent(jobj: JObject): IStructContent = {
    getType(jobj) match {
      case "NormalStructMember" => {
        NormalStructMemberP(
          loadRange(getObjectField(jobj, "range")),
          loadName(getObjectField(jobj, "name")),
          loadVariability(getObjectField(jobj, "variability")),
          loadTemplex(getObjectField(jobj, "type")))
      }
      case "VariadicStructMember" => {
        VariadicStructMemberP(
          loadRange(getObjectField(jobj, "range")),
          loadVariability(getObjectField(jobj, "variability")),
          loadTemplex(getObjectField(jobj, "type")))
      }
      case "StructMethod" => {
        StructMethodP(
          loadFunction(getObjectField(jobj, "function")))
      }
    }
  }

  def loadOptionalObject[T](jobj: JObject, loadContents: JObject => T): Option[T] = {
    getType(jobj) match {
      case "None" => None
      case "Some" => Some(loadContents(getObjectField(jobj, "value")))
    }
  }

  def loadTemplateRules(jobj: JObject): TemplateRulesP = {
    ast.TemplateRulesP(
      loadRange(getObjectField(jobj, "range")),
      getArrayField(jobj, "rules").map(expectObject).map(loadRulex))
  }

  def loadRulex(jobj: JObject): IRulexPR = {
    getType(jobj) match {
      case "TypedPR" => {
        loadTypedPR(jobj)
      }
      case "ComponentsPR" => {
        ComponentsPR(
          loadRange(getObjectField(jobj, "range")),
          loadRulexType(getObjectField(jobj, "container")),
          getArrayField(jobj, "components").map(expectObject).map(loadRulex))
      }
      case "OrPR" => {
        OrPR(
          loadRange(getObjectField(jobj, "range")),
          getArrayField(jobj, "possibilities").map(expectObject).map(loadRulex))
      }
      case "TemplexPR" => {
        TemplexPR(
          loadTemplex(getObjectField(jobj, "templex")))
      }
      case "EqualsPR" => {
        EqualsPR(
          loadRange(getObjectField(jobj, "range")),
          loadRulex(getObjectField(jobj, "left")),
          loadRulex(getObjectField(jobj, "right")))
      }
      case "BuiltinCallPR" => {
        BuiltinCallPR(
          loadRange(getObjectField(jobj, "range")),
          loadName(getObjectField(jobj, "name")),
          getArrayField(jobj, "args").map(expectObject).map(loadRulex))
      }
      case x => vimpl(x.toString)
    }
  }

  private def loadTypedPR(jobj: JObject) = {
    TypedPR(
      loadRange(getObjectField(jobj, "range")),
      loadOptionalObject(getObjectField(jobj, "rune"), loadName),
      loadRulexType(getObjectField(jobj, "type")))
  }

  def loadRulexType(jobj: JObject): ITypePR = {
    getType(jobj) match {
      case "IntTypePR" => IntTypePR
      case "BoolTypePR" => BoolTypePR
      case "OwnershipTypePR" => OwnershipTypePR
      case "MutabilityTypePR" => MutabilityTypePR
      case "VariabilityTypePR" => VariabilityTypePR
      case "LocationTypePR" => LocationTypePR
      case "CoordTypePR" => CoordTypePR
      case "CoordListTypePR" => CoordListTypePR
      case "PrototypeTypePR" => PrototypeTypePR
      case "KindTypePR" => KindTypePR
      case "RegionTypePR" => RegionTypePR
      case "CitizenTemplateTypePR" => CitizenTemplateTypePR
      case x => vimpl(x.toString)
    }
  }


  def loadRuneAttribute(jobj: JObject): IRuneAttributeP = {
    getType(jobj) match {
      case "TypeRuneAttribute" => {
        TypeRuneAttributeP(
          loadRange(getObjectField(jobj, "range")),
          loadRulexType(getObjectField(jobj, "type")))
      }
      case "ReadOnlyRuneAttribute" => ReadOnlyRuneAttributeP(loadRange(getObjectField(jobj, "range")))
      case "ReadWriteRuneAttribute" => ReadWriteRuneAttributeP(loadRange(getObjectField(jobj, "range")))
      case "ImmutableRuneAttribute" => ImmutableRuneAttributeP(loadRange(getObjectField(jobj, "range")))
      case "PoolRuneAttribute" => PoolRuneAttributeP(loadRange(getObjectField(jobj, "range")))
      case "ArenaRuneAttribute" => ArenaRuneAttributeP(loadRange(getObjectField(jobj, "range")))
      case "BumpRuneAttribute" => BumpRuneAttributeP(loadRange(getObjectField(jobj, "range")))
      case x => vimpl(x.toString)
    }
  }

  def loadAttribute(jobj: JObject): IAttributeP = {
    getType(jobj) match {
      case "AbstractAttribute" => AbstractAttributeP(loadRange(getObjectField(jobj, "range")))
      case "PureAttribute" => PureAttributeP(loadRange(getObjectField(jobj, "range")))
      case "ExportAttribute" => ExportAttributeP(loadRange(getObjectField(jobj, "range")))
      case "ExternAttribute" => ExternAttributeP(loadRange(getObjectField(jobj, "range")))
      case "BuiltinAttribute" => {
        BuiltinAttributeP(
          loadRange(getObjectField(jobj, "range")),
          loadName(getObjectField(jobj, "generatorName")))
      }
      case "ExportAttribute" => ExportAttributeP(loadRange(getObjectField(jobj, "range")))
      case "SealedAttribute" => SealedAttributeP(loadRange(getObjectField(jobj, "range")))
      case "WeakableAttribute" => WeakableAttributeP(loadRange(getObjectField(jobj, "range")))
      case "MacroCall" => {
        MacroCallP(
          loadRange(getObjectField(jobj, "range")),
          if (getBooleanField(jobj, "dontCall")) DontCallMacroP else CallMacroP,
          loadName(getObjectField(jobj, "name")))
      }
      case x => vimpl(x.toString)
    }
  }

  def loadMutability(jobj: JObject): MutabilityP = {
    getType(jobj) match {
      case "Mutable" => MutableP
      case "Immutable" => ImmutableP
    }
  }

  def loadVariability(jobj: JObject): VariabilityP = {
    getType(jobj) match {
      case "Varying" => VaryingP
      case "Final" => FinalP
    }
  }

  def loadOwnership(jobj: JObject): OwnershipP = {
    getType(jobj) match {
      case "Own" => OwnP
      case "Borrow" => BorrowP
      case "Weak" => WeakP
      case "Share" => ShareP
    }
  }

  def loadTemplex(jobj: JObject): ITemplexPT = {
    getType(jobj) match {
      case "NameOrRuneT" => {
        NameOrRunePT(
          loadName(getObjectField(jobj, "rune")))
      }
      case "MutabilityT" => {
        MutabilityPT(
          loadRange(getObjectField(jobj, "range")),
          loadMutability(getObjectField(jobj, "mutability")))
      }
      case "VariabilityT" => {
        VariabilityPT(
          loadRange(getObjectField(jobj, "range")),
          loadVariability(getObjectField(jobj, "variability")))
      }
      case "StringT" => {
        StringPT(
          loadRange(getObjectField(jobj, "range")),
          getStringField(jobj, "str"))
      }
      case "IntT" => {
        IntPT(
          loadRange(getObjectField(jobj, "range")),
          getLongField(jobj, "inner"))
      }
      case "AnonymousRuneT" => {
        AnonymousRunePT(
          loadRange(getObjectField(jobj, "range")))
      }
      case "RegionRuneT" => {
        RegionRunePT(
          loadRange(getObjectField(jobj, "range")),
          loadName(getObjectField(jobj, "name")))
      }
      case "OwnershipT" => {
        OwnershipPT(
          loadRange(getObjectField(jobj, "range")),
          loadOwnership(getObjectField(jobj, "ownership")))
      }
      case "InterpretedT" => {
        InterpretedPT(
          loadRange(getObjectField(jobj, "range")),
          loadOwnership(getObjectField(jobj, "ownership")),
          loadTemplex(getObjectField(jobj, "inner")))
      }
      case "CallT" => {
        CallPT(
          loadRange(getObjectField(jobj, "range")),
          loadTemplex(getObjectField(jobj, "template")),
          getArrayField(jobj, "args").map(expectObject).map(loadTemplex))
      }
      case "PackT" => {
        PackPT(
          loadRange(getObjectField(jobj, "range")),
          getArrayField(jobj, "members").map(expectObject).map(loadTemplex))
      }
      case "ManualSequenceT" => {
        TuplePT(
          loadRange(getObjectField(jobj, "range")),
          getArrayField(jobj, "members").map(expectObject).map(loadTemplex))
      }
      case "StaticSizedArrayT" => {
        StaticSizedArrayPT(
          loadRange(getObjectField(jobj, "range")),
          loadTemplex(getObjectField(jobj, "mutability")),
          loadTemplex(getObjectField(jobj, "variability")),
          loadTemplex(getObjectField(jobj, "size")),
          loadTemplex(getObjectField(jobj, "element")))
      }
      case "RuntimeSizedArrayT" => {
        RuntimeSizedArrayPT(
          loadRange(getObjectField(jobj, "range")),
          loadTemplex(getObjectField(jobj, "mutability")),
          loadTemplex(getObjectField(jobj, "element")))
      }
      case "BorrowT" => {
        BorrowPT(
          loadRange(getObjectField(jobj, "range")),
          loadTemplex(getObjectField(jobj, "inner")))
      }
      case "InlineT" => {
        InlinePT(
          loadRange(getObjectField(jobj, "range")),
          loadTemplex(getObjectField(jobj, "inner")))
      }
      case "PrototypeT" => {
        PrototypePT(
          loadRange(getObjectField(jobj, "range")),
          loadName(getObjectField(jobj, "name")),
          getArrayField(jobj, "parameters").map(expectObject).map(loadTemplex),
          loadTemplex(getObjectField(jobj, "returnType")))
      }
      case x => vimpl(x.toString)
    }
  }

  def loadIdentifyingRunes(jobj: JObject): IdentifyingRunesP = {
    IdentifyingRunesP(
      loadRange(getObjectField(jobj, "range")),
      getArrayField(jobj, "identifyingRunes").map(expectObject).map(loadIdentifyingRune))
  }
  def loadIdentifyingRune(jobj: JObject): IdentifyingRuneP = {
    IdentifyingRuneP(
      loadRange(getObjectField(jobj, "range")),
      loadName(getObjectField(jobj, "name")),
      getArrayField(jobj, "attributes").map(expectObject).map(loadRuneAttribute))
  }
}
