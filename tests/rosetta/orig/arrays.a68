(
  [1:10]INT a;
  a := (1,2,3,4,5,6,7,8,9,10);               # assign whole array #
  a[1] := -1;                                # assign individual element #
  []INT slice = a[3:5];                      # copy a slice #
  a[3:5] := (2, 4, -1);                      # assign a slice #

  REF []INT rslice = a[3:5];                 # create a reference to a slice #
  print((LWB rslice, UPB rslice,newline));   # query the bounds of the slice #
  rslice := (2, 4, -1);                      # assign to the slice, modifying original array #
  print((slice,newline,rslice,newline,a[3:5],newline));

  [1:3, 1:3]INT matrix;                      # create a two dimensional array #
  REF []INT hvector = matrix[2,];            # create a reference to a row #
  REF []INT vvector = matrix[,2];            # create a reference to a column #
  REF [,]INT block = matrix[1:2, 1:2];       # create a reference to an area of the array #

  FLEX [1:13]CHAR string := "Hello, world!"; # create an array with variable bounds #
  print((LWB string, UPB string,newline));   # query the bounds of the flexible array #
  string := "shorter";                       # flexible arrays automatically resize themselves on assignment #
  print((LWB string, UPB string,newline))    # query the new bounds of the flexible array #
)
