package net.verdagon.vale.parser

import net.liftweb.json._
import net.verdagon.vale.{vimpl, vwat}

object ParsedLoader {
  def expectObject(obj: Object): JObject = {
    if (!obj.isInstanceOf[JObject]) {
      throw BadVPSTException(BadVPSTError("Expected JSON object, got: " + obj.getClass.getSimpleName))
    }
    obj.asInstanceOf[JObject]
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
  def loadRange(jobj: JObject): Range = {
    expectType(jobj, "Range")
    Range(
      getIntField(jobj, "begin"),
      getIntField(jobj, "end"))
  }
  def loadName(jobj: JObject): NameP = {
    expectType(jobj, "Name")
    NameP(
      loadRange(getObjectField(jobj, "range")),
      getStringField(jobj, "name"))
  }

  def load(source: String): IParseResult[FileP] = {
    try {
      val jfile = expectObjectTyped(parse(source), "File")
      ParseSuccess(
        FileP(
          getArrayField(jfile, "topLevelThings").map(expectObject).map(topLevelThing => {
            getType(topLevelThing) match {
              case "Struct" => TopLevelStructP(loadStruct(topLevelThing))
              case "Interface" => TopLevelInterfaceP(loadInterface(topLevelThing))
              case "Function" => TopLevelFunctionP(loadFunction(topLevelThing))
              case "Impl" => TopLevelImplP(loadImpl(topLevelThing))
              case "Import" => TopLevelImportP(loadImport(topLevelThing))
              case "ExportAs" => TopLevelExportAsP(loadExportAs(topLevelThing))
              case x => vimpl(x.toString)
            }
          })))
    } catch {
      case BadVPSTException(err) => ParseFailure(err)
    }
  }

  private def loadFunction(topLevelThing: JObject) = {
    FunctionP(
      loadRange(getObjectField(topLevelThing, "range")),
      loadFunctionHeader(getObjectField(topLevelThing, "header")),
      loadOptionalObject(getObjectField(topLevelThing, "body"), loadBlock))
  }

  private def loadImpl(jobj: JObject) = {
    ImplP(
      loadRange(getObjectField(jobj, "range")),
      loadOptionalObject(getObjectField(jobj, "identifyingRunes"), loadIdentifyingRunes),
      loadOptionalObject(getObjectField(jobj, "rules"), loadTemplateRules),
      loadTemplex(getObjectField(jobj, "struct")),
      loadTemplex(getObjectField(jobj, "interface")))
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
      getArrayField(jobj, "attributes").map(expectObject).map(loadCitizenAttribute),
      loadTemplex(getObjectField(jobj, "mutability")),
      loadOptionalObject(getObjectField(jobj, "identifyingRunes"), loadIdentifyingRunes),
      loadOptionalObject(getObjectField(jobj, "templateRules"), loadTemplateRules),
      loadStructMembers(getObjectField(jobj, "members")))
  }

  private def loadInterface(topLevelThing: JObject) = {
    InterfaceP(
      loadRange(getObjectField(topLevelThing, "range")),
      loadName(getObjectField(topLevelThing, "name")),
      getArrayField(topLevelThing, "attributes").map(expectObject).map(loadCitizenAttribute),
      loadTemplex(getObjectField(topLevelThing, "mutability")),
      loadOptionalObject(getObjectField(topLevelThing, "maybeIdentifyingRunes"), loadIdentifyingRunes),
      loadOptionalObject(getObjectField(topLevelThing, "templateRules"), loadTemplateRules),
      getArrayField(topLevelThing, "members").map(expectObject).map(loadFunction))
  }

  def loadFunctionHeader(jobj: JObject): FunctionHeaderP = {
    FunctionHeaderP(
      loadRange(getObjectField(jobj, "range")),
      loadOptionalObject(getObjectField(jobj, "name"), loadName),
      getArrayField(jobj, "attributes").map(expectObject).map(loadFunctionAttribute),
      loadOptionalObject(getObjectField(jobj, "maybeUserSpecifiedIdentifyingRunes"), loadIdentifyingRunes),
      loadOptionalObject(getObjectField(jobj, "templateRules"), loadTemplateRules),
      loadOptionalObject(getObjectField(jobj, "params"), loadParams),
      loadFunctionReturn(getObjectField(jobj, "ret")))
  }

  def loadParams(jobj: JObject): ParamsP = {
    ParamsP(
      loadRange(getObjectField(jobj, "range")),
      getArrayField(jobj, "patterns").map(expectObject).map(loadPattern))
  }

  def loadPattern(jobj: JObject): PatternPP = {
    PatternPP(
      loadRange(getObjectField(jobj, "range")),
      loadOptionalObject(getObjectField(jobj, "preBorrow"), loadUnit),
      loadOptionalObject(getObjectField(jobj, "capture"), loadCapture),
      loadOptionalObject(getObjectField(jobj, "templex"), loadTemplex),
      loadOptionalObject(getObjectField(jobj, "destructure"), loadDestructure),
      loadOptionalObject(getObjectField(jobj, "virtuality"), loadVirtuality))
  }

  def loadDestructure(jobj: JObject): DestructureP = {
    DestructureP(
      loadRange(getObjectField(jobj, "range")),
      getArrayField(jobj, "patterns").map(expectObject).map(loadPattern))
  }

  def loadCapture(jobj: JObject): CaptureP = {
    CaptureP(
      loadRange(getObjectField(jobj, "range")),
      loadCaptureName(getObjectField(jobj, "captureName")))
  }

  def loadCaptureName(jobj: JObject): ICaptureNameP = {
    getType(jobj) match {
      case "LocalName" => {
        LocalNameP(
          loadName(getObjectField(jobj, "name")))
      }
      case "ConstructingMemberName" => {
        ConstructingMemberNameP(
          loadName(getObjectField(jobj, "name")))
      }
    }
  }

  def loadBlock(jobj: JObject): BlockPE = {
    BlockPE(
      loadRange(getObjectField(jobj, "range")),
      getArrayField(jobj, "elements").map(expectObject).map(loadExpression))
  }

  def loadFunctionReturn(jobj: JObject): FunctionReturnP = {
    FunctionReturnP(
      loadRange(getObjectField(jobj, "range")),
      loadOptionalObject(getObjectField(jobj, "inferRet"), loadUnit),
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
          loadOptionalObject(getObjectField(jobj, "inline"), loadUnit),
          loadRange(getObjectField(jobj, "operatorRange")),
          getBooleanField(jobj, "isMapCall"),
          loadExpression(getObjectField(jobj, "callableExpr")),
          getArrayField(jobj, "argExprs").map(expectObject).map(loadExpression),
          loadLoadAs(getObjectField(jobj, "callableTargetOwnership")))
      }
      case "MethodCall" => {
        MethodCallPE(
          loadRange(getObjectField(jobj, "range")),
          loadOptionalObject(getObjectField(jobj, "inline"), loadUnit),
          loadExpression(getObjectField(jobj, "subjectExpr")),
          loadRange(getObjectField(jobj, "operatorRange")),
          loadLoadAs(getObjectField(jobj, "subjectTargetOwnership")),
          getBooleanField(jobj, "isMapCall"),
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
          getIntField(jobj, "bits"))
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
          getBooleanField(jobj, "isMapAccess"),
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
          loadOptionalObject(getObjectField(jobj, "templateRules"), loadTemplateRules),
          loadPattern(getObjectField(jobj, "pattern")),
          loadExpression(getObjectField(jobj, "source")))
      }
      case "BadLet" => {
        BadLetPE(
          loadRange(getObjectField(jobj, "range")))
      }
      case "Point" => {
        LoadPE(
          loadRange(getObjectField(jobj, "range")),
          loadExpression(getObjectField(jobj, "inner")),
          loadLoadAs(getObjectField(jobj, "targetOwnership")))
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
      case "Block" => {
        BlockPE(
          loadRange(getObjectField(jobj, "range")),
          getArrayField(jobj, "elements").map(expectObject).map(loadExpression))
      }
      case "If" => {
        IfPE(
          loadRange(getObjectField(jobj, "range")),
          loadBlock(getObjectField(jobj, "condition")),
          loadBlock(getObjectField(jobj, "thenBody")),
          loadBlock(getObjectField(jobj, "elseBody")))
      }
      case "While" => {
        WhilePE(
          loadRange(getObjectField(jobj, "range")),
          loadBlock(getObjectField(jobj, "condition")),
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
      case "Or" => {
        OrPE(
          loadRange(getObjectField(jobj, "range")),
          loadBlock(getObjectField(jobj, "left")),
          loadBlock(getObjectField(jobj, "right")))
      }
      case "And" => {
        AndPE(
          loadRange(getObjectField(jobj, "range")),
          loadBlock(getObjectField(jobj, "left")),
          loadBlock(getObjectField(jobj, "right")))
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
      loadOptionalObject(getObjectField(jobj, "mutability"), loadTemplex),
      loadOptionalObject(getObjectField(jobj, "variability"), loadTemplex),
      loadArraySize(getObjectField(jobj, "size")),
      getBooleanField(jobj, "initializingIndividualElements"),
      getArrayField(jobj, "args").map(expectObject).map(loadExpression))
  }

  private def loadLookup(jobj: JObject) = {
    LookupPE(
      loadName(getObjectField(jobj, "name")),
      loadOptionalObject(getObjectField(jobj, "templateArgs"), loadTemplateArgs))
  }

  def loadTemplateArgs(jobj: JObject): TemplateArgsP = {
    TemplateArgsP(
      loadRange(getObjectField(jobj, "range")),
      getArrayField(jobj, "args").map(expectObject).map(loadTemplex))
  }

  def loadLoadAs(jobj: JObject): LoadAsP = {
    getType(jobj) match {
      case "Move" => MoveP
      case "Use" => UseP
      case "LoadAsPointer" => {
        LoadAsPointerP(
          loadOptionalObject(getObjectField(jobj, "permission"), loadPermission))
      }
      case "LoadAsBorrow" => {
        LoadAsBorrowP(
          loadOptionalObject(getObjectField(jobj, "permission"), loadPermission))
      }
      case "LoadAsBorrowOrIfContainerIsPointerThenPointer" => {
        LoadAsBorrowOrIfContainerIsPointerThenPointerP(
          loadOptionalObject(getObjectField(jobj, "permission"), loadPermission))
      }
      case "LoadAsWeak" => {
        LoadAsWeakP(
          loadPermission(getObjectField(jobj, "permission")))
      }
      case other => vwat(other)
    }
  }

  def loadVirtuality(jobj: JObject): IVirtualityP = {
    getType(jobj) match {
      case "Override" => {
        OverrideP(
          loadRange(getObjectField(jobj, "range")),
          loadTemplex(getObjectField(jobj, "type")))
      }
      case "Abstract" => {
        AbstractP(
          loadRange(getObjectField(jobj, "range")))
      }
    }
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
    TemplateRulesP(
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
          loadTypedPR(getObjectField(jobj, "container")),
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
      case "PermissionTypePR" => PermissionTypePR
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

  def loadCitizenAttribute(jobj: JObject): ICitizenAttributeP = {
    getType(jobj) match {
      case "ExportAttribute" => ExportP(loadRange(getObjectField(jobj, "range")))
      case "SealedAttribute" => SealedP(loadRange(getObjectField(jobj, "range")))
      case "WeakableAttribute" => WeakableP(loadRange(getObjectField(jobj, "range")))
      case "MacroCall" => {
        MacroCallP(
          loadRange(getObjectField(jobj, "range")),
          if (getBooleanField(jobj, "dontCall")) DontCallMacro else CallMacro,
          loadName(getObjectField(jobj, "name")))
      }
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
      case "PoolRuneAttribute" => PoolRuneAttributeP(loadRange(getObjectField(jobj, "range")))
      case "ArenaRuneAttribute" => ArenaRuneAttributeP(loadRange(getObjectField(jobj, "range")))
      case "BumpRuneAttribute" => BumpRuneAttributeP(loadRange(getObjectField(jobj, "range")))
      case x => vimpl(x.toString)
    }
  }

  def loadFunctionAttribute(jobj: JObject): IFunctionAttributeP = {
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

  def loadPermission(jobj: JObject): PermissionP = {
    getType(jobj) match {
      case "Readonly" => ReadonlyP
      case "Readwrite" => ReadwriteP
    }
  }

  def loadOwnership(jobj: JObject): OwnershipP = {
    getType(jobj) match {
      case "Own" => OwnP
      case "Pointer" => PointerP
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
      case "OwnershipT" => {
        OwnershipPT(
          loadRange(getObjectField(jobj, "range")),
          loadOwnership(getObjectField(jobj, "ownership")))
      }
      case "InterpretedT" => {
        InterpretedPT(
          loadRange(getObjectField(jobj, "range")),
          loadOwnership(getObjectField(jobj, "ownership")),
          loadPermission(getObjectField(jobj, "permission")),
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
        ManualSequencePT(
          loadRange(getObjectField(jobj, "range")),
          getArrayField(jobj, "members").map(expectObject).map(loadTemplex))
      }
      case "RepeaterSequenceT" => {
        RepeaterSequencePT(
          loadRange(getObjectField(jobj, "range")),
          loadTemplex(getObjectField(jobj, "mutability")),
          loadTemplex(getObjectField(jobj, "variability")),
          loadTemplex(getObjectField(jobj, "size")),
          loadTemplex(getObjectField(jobj, "element")))
      }
      case "BorrowT" => {
        BorrowPT(
          loadRange(getObjectField(jobj, "range")),
          loadTemplex(getObjectField(jobj, "inner")))
      }
      case "PermissionT" => {
        PermissionPT(
          loadRange(getObjectField(jobj, "range")),
          loadPermission(getObjectField(jobj, "permission")))
      }
      case "InlineT" => {
        InlinePT(
          loadRange(getObjectField(jobj, "range")),
          loadTemplex(getObjectField(jobj, "inner")))
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
