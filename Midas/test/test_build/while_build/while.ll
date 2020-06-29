; ModuleID = 'while'
source_filename = "while"

define i1 @"F(\22__lessThanInt\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %2 = icmp slt i64 %0, %1
  ret i1 %2
}

define i64 @"F(\22+\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %add.i = add i64 %1, %0
  ret i64 %add.i
}

define i64 @"F(\22main\22)"() {
block1:
  br label %block2

block2:                                           ; preds = %block6, %block1
  %.0 = phi i64 [ 1, %block1 ], [ %.1, %block6 ]
  %0 = icmp slt i64 %.0, 42
  br i1 %0, label %block4, label %block6

block3:                                           ; preds = %block6
  ret i64 %.1

block4:                                           ; preds = %block2
  %add.i.i = add i64 1, %.0
  br label %block6

block6:                                           ; preds = %block2, %block4
  %.1 = phi i64 [ %add.i.i, %block4 ], [ %.0, %block2 ]
  %1 = phi i1 [ true, %block4 ], [ false, %block2 ]
  br i1 %1, label %block2, label %block3
}

define i1 @"F(\22<\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %2 = icmp slt i64 %0, %1
  ret i1 %2
}

define i64 @"F(\22__addIntInt\22,[],[R(*,i),R(*,i)])"(i64, i64) {
block1:
  %add = add i64 %1, %0
  ret i64 %add
}

define dllexport x86_stdcallcc i64 @main(i64, i8**) {
thebestblock:
  br label %block2.i

block2.i:                                         ; preds = %block6.i, %thebestblock
  %.0.i = phi i64 [ 1, %thebestblock ], [ %.1.i, %block6.i ]
  %2 = icmp slt i64 %.0.i, 42
  br i1 %2, label %block4.i, label %block6.i

block4.i:                                         ; preds = %block2.i
  %add.i.i.i = add i64 1, %.0.i
  br label %block6.i

block6.i:                                         ; preds = %block4.i, %block2.i
  %.1.i = phi i64 [ %add.i.i.i, %block4.i ], [ %.0.i, %block2.i ]
  %3 = phi i1 [ true, %block4.i ], [ false, %block2.i ]
  br i1 %3, label %block2.i, label %"F(\22main\22).exit"

"F(\22main\22).exit":                             ; preds = %block6.i
  ret i64 %.1.i
}
