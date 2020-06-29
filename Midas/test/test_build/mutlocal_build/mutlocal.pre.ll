; ModuleID = 'mutlocal'
source_filename = "mutlocal"

define i64 @"F(\22main\22)"() {
block1:
  %0 = alloca i64
  store i64 73, i64* %0
  %1 = load i64, i64* %0
  %2 = alloca i64
  store i64 %1, i64* %2
  %"F(\22main\22):CodeVarName(\22x\22)" = load i64, i64* %2
  %3 = alloca i64
  store i64 42, i64* %3
  %4 = load i64, i64* %3
  store i64 %4, i64* %2
  %"F(\22main\22):CodeVarName(\22x\22)1" = load i64, i64* %2
  %5 = alloca i64
  store i64 %"F(\22main\22):CodeVarName(\22x\22)1", i64* %5
  %6 = load i64, i64* %2
  %7 = load i64, i64* %5
  ret i64 %7
}

define dllexport x86_stdcallcc i64 @main(i64, i8**) {
thebestblock:
  %valeMainCall = call i64 @"F(\22main\22)"()
  ret i64 %valeMainCall
}
