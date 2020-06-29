; ModuleID = 'if'
source_filename = "if"

define i64 @"F(\22main\22)"() {
block1:
  %0 = alloca i1
  store i1 true, i1* %0
  %1 = load i1, i1* %0
  br i1 %1, label %block2, label %block3

block2:                                           ; preds = %block1
  %2 = alloca i64
  store i64 42, i64* %2
  %3 = load i64, i64* %2
  br label %block4

block3:                                           ; preds = %block1
  %4 = alloca i64
  store i64 73, i64* %4
  %5 = load i64, i64* %4
  br label %block4

block4:                                           ; preds = %block3, %block2
  %6 = phi i64 [ %3, %block2 ], [ %5, %block3 ]
  ret i64 %6
}

define dllexport x86_stdcallcc i64 @main(i64, i8**) {
thebestblock:
  %valeMainCall = call i64 @"F(\22main\22)"()
  ret i64 %valeMainCall
}
