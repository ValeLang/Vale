// AST module - re-exports all parsing AST types
// Mirrors Frontend/ParsingPass/src/dev/vale/parsing/ast/

pub mod ast;
pub mod expressions;
pub mod pattern;
pub mod rules;
pub mod templex;

// Re-export everything from ast.rs
pub use ast::{
  AbstractAttributeP, AdditiveAttributeP, BuiltinAttributeP, ExportAsP, ExportAttributeP,
  ExternAttributeP, FileP, FunctionHeaderP, FunctionP, FunctionReturnP, GenericParameterP,
  GenericParameterTypeP, GenericParametersP, IAttributeP, IDenizenP, IMacroInclusionP,
  IRuneAttributeP, IStructContent, ImplP, ImportP, InterfaceP, LinearAttributeP, LoadAsP,
  LocationP, MacroCallP, MutabilityP, NameP, NormalStructMemberP, OwnershipP, ParamsP,
  PureAttributeP, SealedAttributeP, StructMembersP, StructP, TemplateRulesP, UnitP, VariabilityP,
  VariadicStructMemberP, WeakableAttributeP,
};

// Re-export everything from expressions.rs
pub use expressions::{
  AndPE, AugmentPE, BinaryCallPE, BlockPE, BraceCallPE, BreakPE, ConsecutorPE, ConstantBoolPE,
  ConstantFloatPE, ConstantIntPE, ConstantStrPE, ConstructArrayPE, DestructPE, DotPE, EachPE,
  FunctionCallPE, IArraySizeP, IExpressionPE, IImpreciseNameP, IfPE, IndexPE, LambdaPE, LetPE,
  LookupPE, MagicParamLookupPE, MethodCallPE, MutatePE, NotPE, OrPE, PackPE, RangePE, ReturnPE,
  ShortcallPE, StaticSizedArraySizeP, StrInterpolatePE, SubExpressionPE, TemplateArgsP,
  TransmigratePE, TuplePE, UnletPE, VoidPE, WhilePE,
};

// Re-export everything from pattern.rs
pub use pattern::{
  AbstractP, DestinationLocalP, DestructureP, INameDeclarationP, ParameterP, PatternPP,
};

// Re-export everything from rules.rs
pub use rules::{
  BuiltinCallPR, ComponentsPR, DotPR, EqualsPR, IRulexPR, ITypePR, OrPR, PackPR, TypedPR,
};

// Re-export everything from templex.rs
pub use templex::{
  AnonymousRunePT, BoolPT, CallPT, FuncPT, FunctionPT, ITemplexPT, InlinePT, IntPT, InterpretedPT,
  LocationPT, MutabilityPT, NameOrRunePT, OwnershipPT, PackPT, PointPT, RegionRunePT,
  RuntimeSizedArrayPT, SharePT, StaticSizedArrayPT, StringPT, TuplePT, TypedRunePT, VariabilityPT,
};
