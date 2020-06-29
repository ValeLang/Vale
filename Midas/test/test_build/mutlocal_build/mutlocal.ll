; ModuleID = 'mutlocal'
source_filename = "mutlocal"

define i64 @"F(\22main\22)"() {
block1:
  ret i64 42
}

define dllexport x86_stdcallcc i64 @main(i64, i8**) {
thebestblock:
  ret i64 42
}
