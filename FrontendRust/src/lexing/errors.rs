use crate::utils::code_hierarchy::FileCoordinate;

/// Failed parse with context
#[derive(Debug)]
pub struct FailedParse<'p> {
  pub code: String,
  pub file_coord: FileCoordinate<'p>,
  pub error: ParseError,
}

/// Parse error types
#[derive(Debug)]
pub enum ParseError {
  RangedInternalError { pos: i32, msg: String },
  UnrecognizableExpressionAfterAugment(i32),
  OnlyRegionRunesCanHaveMutability(i32),
  BadMemberEnd(i32),
  BadLambdaBegin(i32),
  BadLambdaBodyBegin(i32),
  EmptyParameter(i32),
  CantUseThatLocalName { pos: i32, name: String },
  LightFunctionMustHaveParamTypes { pos: i32, param_index: i32 },
  EmptyPattern(i32),
  BadNameBeforeDestructure(i32),
  BadLocalNameInUnlet(i32),
  FoundBothAbstractAndOverride(i32),
  FoundBothImmutableAndMutabilityInArray(i32),
  BadStringInTemplex(i32),
  BadPrototypeName(i32),
  BadPrototypeParams(i32),
  BadRuleCallParam(i32),
  BadTypeExpression(i32),
  BadTemplateCallParam(i32),
  BadTupleElement(i32),
  BadDestructureError(i32),
  ShareCantBeReadwrite(i32),
  BadRuneEnd(i32),
  BadRegionName(i32),
  BadRuneNameError(i32),
  RegionRuneHasType(i32),
  BadRuneTypeError(i32),
  UnrecognizedDenizenError(i32),
  BadStartOfStatementError(i32),
  BadExpressionEnd(i32),
  BadRule(i32),
  UnexpectedAttributes(i32),
  IfBlocksMustBothOrNeitherReturn(i32),
  BadExpressionBegin(i32),
  BadStringChar { string_begin_pos: i32, pos: i32 },
  BadFunctionName(i32),
  BadParamEnd(i32),
  ForgotSetKeyword(i32),
  FuncBoundWithoutWhere(i32),
  BadBinaryFunctionName(i32),
  BadTemplateCallee(i32),
  UnknownTupleOrSubExpression(i32),
  BadRangeOperand(i32),
  CantUseBreakInExpression(i32),
  CantUseReturnInExpression(i32),
  CantUseWhileInExpression(i32),
  NeedSemicolon(i32),
  DontNeedSemicolon(i32),
  BadDot(i32),
  CantTemplateCallMember(i32),
  NeedWhitespaceAroundBinaryOperator(i32),
  BadFunctionBodyError(i32),
  BadUnicodeChar(i32),
  BadStringInterpolationEnd(i32),
  BadStartOfBlock(i32),
  BadEndOfBlock(i32),
  BadStartOfWhileCondition(i32),
  BadEndOfWhileCondition(i32),
  BadStartOfWhileBody(i32),
  BadEndOfWhileBody(i32),
  BadStartOfIfCondition(i32),
  BadEndOfIfCondition(i32),
  BadStartOfIfBody(i32),
  BadEndOfIfBody(i32),
  BadStartOfElseBody(i32),
  BadEndOfElseBody(i32),
  BadLetEqualsError(i32),
  BadMutateEqualsError(i32),
  BadLetEndError(i32),
  BadVPSTError { message: String },
  BadArraySizerEnd(i32),
  BadArraySizer(i32),
  BadStructName(i32),
  BadInterfaceName(i32),
  BadStructContentsBegin(i32),
  BadStructContentsEnd(i32),
  BadStructMember(i32),
  BadStructMemberType(i32),
  VariadicStructMemberHasName(i32),
  BadInterfaceMember(i32),
  BadInterfaceContentsBegin(i32),
  BadInterfaceHeader(i32),
  BadImpl(i32),
  BadImplStruct(i32),
  BadImplInterface(i32),
  BadImplEnd(i32),
  BadImplFor(i32),
  BadExportAs(i32),
  BadImportEnd(i32),
  BadImportName(i32),
  BadFunctionParamsBegin(i32),
  BadFunctionAfterParam(i32),
  BadAttributeError(i32),
  BadExternAttribute(i32),
  BadExportName(i32),
  BadExportEnd(i32),
  BadForeachIterableError(i32),
  BadArraySpecifier(i32),
  BadLocalName(i32),
  BadThingAfterTypeInPattern(i32),
  BadLetSourceError { pos: i32, cause: Box<ParseError> },
  BadForeachInError(i32),
}

impl ParseError {
  pub fn pos(&self) -> i32 {
    match self {
      ParseError::RangedInternalError { pos, .. } => *pos,
      ParseError::UnrecognizableExpressionAfterAugment(p) => *p,
      ParseError::OnlyRegionRunesCanHaveMutability(p) => *p,
      ParseError::BadMemberEnd(p) => *p,
      ParseError::BadLambdaBegin(p) => *p,
      ParseError::BadLambdaBodyBegin(p) => *p,
      ParseError::EmptyParameter(p) => *p,
      ParseError::CantUseThatLocalName { pos, .. } => *pos,
      ParseError::LightFunctionMustHaveParamTypes { pos, .. } => *pos,
      ParseError::EmptyPattern(p) => *p,
      ParseError::BadNameBeforeDestructure(p) => *p,
      ParseError::BadLocalNameInUnlet(p) => *p,
      ParseError::FoundBothAbstractAndOverride(p) => *p,
      ParseError::FoundBothImmutableAndMutabilityInArray(p) => *p,
      ParseError::BadStringInTemplex(p) => *p,
      ParseError::BadPrototypeName(p) => *p,
      ParseError::BadPrototypeParams(p) => *p,
      ParseError::BadRuleCallParam(p) => *p,
      ParseError::BadTypeExpression(p) => *p,
      ParseError::BadTemplateCallParam(p) => *p,
      ParseError::BadTupleElement(p) => *p,
      ParseError::BadDestructureError(p) => *p,
      ParseError::ShareCantBeReadwrite(p) => *p,
      ParseError::BadRuneEnd(p) => *p,
      ParseError::BadRegionName(p) => *p,
      ParseError::BadRuneNameError(p) => *p,
      ParseError::RegionRuneHasType(p) => *p,
      ParseError::BadRuneTypeError(p) => *p,
      ParseError::UnrecognizedDenizenError(p) => *p,
      ParseError::BadStartOfStatementError(p) => *p,
      ParseError::BadExpressionEnd(p) => *p,
      ParseError::BadRule(p) => *p,
      ParseError::UnexpectedAttributes(p) => *p,
      ParseError::IfBlocksMustBothOrNeitherReturn(p) => *p,
      ParseError::BadExpressionBegin(p) => *p,
      ParseError::BadStringChar { pos, .. } => *pos,
      ParseError::BadFunctionName(p) => *p,
      ParseError::BadParamEnd(p) => *p,
      ParseError::ForgotSetKeyword(p) => *p,
      ParseError::FuncBoundWithoutWhere(p) => *p,
      ParseError::BadBinaryFunctionName(p) => *p,
      ParseError::BadTemplateCallee(p) => *p,
      ParseError::UnknownTupleOrSubExpression(p) => *p,
      ParseError::BadRangeOperand(p) => *p,
      ParseError::CantUseBreakInExpression(p) => *p,
      ParseError::CantUseReturnInExpression(p) => *p,
      ParseError::CantUseWhileInExpression(p) => *p,
      ParseError::NeedSemicolon(p) => *p,
      ParseError::DontNeedSemicolon(p) => *p,
      ParseError::BadDot(p) => *p,
      ParseError::CantTemplateCallMember(p) => *p,
      ParseError::NeedWhitespaceAroundBinaryOperator(p) => *p,
      ParseError::BadFunctionBodyError(p) => *p,
      ParseError::BadUnicodeChar(p) => *p,
      ParseError::BadStringInterpolationEnd(p) => *p,
      ParseError::BadStartOfBlock(p) => *p,
      ParseError::BadEndOfBlock(p) => *p,
      ParseError::BadStartOfWhileCondition(p) => *p,
      ParseError::BadEndOfWhileCondition(p) => *p,
      ParseError::BadStartOfWhileBody(p) => *p,
      ParseError::BadEndOfWhileBody(p) => *p,
      ParseError::BadStartOfIfCondition(p) => *p,
      ParseError::BadEndOfIfCondition(p) => *p,
      ParseError::BadStartOfIfBody(p) => *p,
      ParseError::BadEndOfIfBody(p) => *p,
      ParseError::BadStartOfElseBody(p) => *p,
      ParseError::BadEndOfElseBody(p) => *p,
      ParseError::BadLetEqualsError(p) => *p,
      ParseError::BadMutateEqualsError(p) => *p,
      ParseError::BadLetEndError(p) => *p,
      ParseError::BadVPSTError { .. } => 0,
      ParseError::BadArraySizerEnd(p) => *p,
      ParseError::BadArraySizer(p) => *p,
      ParseError::BadStructName(p) => *p,
      ParseError::BadInterfaceName(p) => *p,
      ParseError::BadStructContentsBegin(p) => *p,
      ParseError::BadStructContentsEnd(p) => *p,
      ParseError::BadStructMember(p) => *p,
      ParseError::BadStructMemberType(p) => *p,
      ParseError::VariadicStructMemberHasName(p) => *p,
      ParseError::BadInterfaceMember(p) => *p,
      ParseError::BadInterfaceContentsBegin(p) => *p,
      ParseError::BadInterfaceHeader(p) => *p,
      ParseError::BadImpl(p) => *p,
      ParseError::BadImplStruct(p) => *p,
      ParseError::BadImplInterface(p) => *p,
      ParseError::BadImplEnd(p) => *p,
      ParseError::BadImplFor(p) => *p,
      ParseError::BadExportAs(p) => *p,
      ParseError::BadImportEnd(p) => *p,
      ParseError::BadImportName(p) => *p,
      ParseError::BadFunctionParamsBegin(p) => *p,
      ParseError::BadFunctionAfterParam(p) => *p,
      ParseError::BadAttributeError(p) => *p,
      ParseError::BadExternAttribute(p) => *p,
      ParseError::BadExportName(p) => *p,
      ParseError::BadExportEnd(p) => *p,
      ParseError::BadForeachIterableError(p) => *p,
      ParseError::BadArraySpecifier(p) => *p,
      ParseError::BadLocalName(p) => *p,
      ParseError::BadThingAfterTypeInPattern(p) => *p,
      ParseError::BadLetSourceError { pos, .. } => *pos,
      ParseError::BadForeachInError(p) => *p,
    }
  }

  pub fn error_id(&self) -> &str {
    match self {
      ParseError::BadStartOfStatementError(_) => "P1002",
      ParseError::BadExpressionEnd(_)
      | ParseError::BadRule(_)
      | ParseError::UnexpectedAttributes(_)
      | ParseError::IfBlocksMustBothOrNeitherReturn(_)
      | ParseError::BadExpressionBegin(_)
      | ParseError::BadStringChar { .. }
      | ParseError::BadFunctionName(_)
      | ParseError::BadParamEnd(_)
      | ParseError::ForgotSetKeyword(_)
      | ParseError::BadBinaryFunctionName(_)
      | ParseError::BadTemplateCallee(_)
      | ParseError::UnknownTupleOrSubExpression(_)
      | ParseError::BadRangeOperand(_)
      | ParseError::CantUseBreakInExpression(_)
      | ParseError::CantUseReturnInExpression(_)
      | ParseError::CantUseWhileInExpression(_)
      | ParseError::NeedSemicolon(_)
      | ParseError::DontNeedSemicolon(_)
      | ParseError::BadDot(_)
      | ParseError::CantTemplateCallMember(_) => "P1005",
      ParseError::NeedWhitespaceAroundBinaryOperator(_) => "P1006",
      ParseError::BadFunctionBodyError(_) => "P1006",
      ParseError::BadStartOfWhileCondition(_) => "P1007",
      ParseError::BadEndOfWhileCondition(_) => "P1008",
      ParseError::BadUnicodeChar(_)
      | ParseError::BadStringInterpolationEnd(_)
      | ParseError::BadStartOfBlock(_)
      | ParseError::BadEndOfBlock(_)
      | ParseError::BadStartOfWhileBody(_) => "P1009",
      ParseError::BadEndOfWhileBody(_) => "P1010",
      ParseError::BadStartOfIfCondition(_) => "P1011",
      ParseError::BadEndOfIfCondition(_) => "P1012",
      ParseError::BadStartOfIfBody(_) => "P1013",
      ParseError::BadEndOfIfBody(_) => "P1014",
      ParseError::BadStartOfElseBody(_) => "P1015",
      ParseError::BadEndOfElseBody(_) => "P1016",
      ParseError::BadLetEqualsError(_) => "P1017",
      ParseError::BadMutateEqualsError(_) => "P1018",
      ParseError::BadLetEndError(_) => "P1019",
      ParseError::BadVPSTError { .. } => "P1020",
      ParseError::BadArraySizerEnd(_) | ParseError::BadArraySizer(_) => "P1022",
      ParseError::BadStructName(_)
      | ParseError::BadInterfaceName(_)
      | ParseError::BadStructContentsBegin(_)
      | ParseError::BadStructContentsEnd(_)
      | ParseError::BadStructMember(_)
      | ParseError::BadStructMemberType(_)
      | ParseError::VariadicStructMemberHasName(_)
      | ParseError::BadInterfaceMember(_)
      | ParseError::BadInterfaceContentsBegin(_) => "P1027",
      ParseError::BadInterfaceHeader(_) => "P1028",
      ParseError::BadImpl(_)
      | ParseError::BadImplStruct(_)
      | ParseError::BadImplInterface(_)
      | ParseError::BadImplEnd(_)
      | ParseError::BadImplFor(_)
      | ParseError::BadExportAs(_) => "P1029",
      ParseError::BadImportEnd(_) | ParseError::BadImportName(_) => "P1031",
      ParseError::BadFunctionParamsBegin(_)
      | ParseError::BadFunctionAfterParam(_)
      | ParseError::BadAttributeError(_)
      | ParseError::BadExternAttribute(_) => "P1032",
      ParseError::BadExportName(_)
      | ParseError::BadExportEnd(_)
      | ParseError::BadForeachIterableError(_) => "P1037",
      ParseError::BadArraySpecifier(_) => "P1039",
      ParseError::BadLocalName(_)
      | ParseError::BadThingAfterTypeInPattern(_)
      | ParseError::BadForeachInError(_) => "P1041",
      ParseError::BadLetSourceError { .. } => "P1042",
      _ => "P1001", // Default for all others
    }
  }
}

