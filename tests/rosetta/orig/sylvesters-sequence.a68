BEGIN # calculate elements of Sylvester's Sequence                         #
    PR precision 220 PR # set the number of digits for LONG LONG modes     #
    # returns an array set to the first n elements of Sylvester's Sequence #
    #    starting from 2, the elements are the product of the previous     #
    #                     elements plus 1                                  #
    OP SYLVESTER = ( INT n )[]LONG LONG INT:
       BEGIN
           [ 1 : n ]LONG LONG INT result;
           LONG LONG INT product := 2;
           result[ 1 ] := 2;
           FOR i FROM 2 TO n DO
               result[ i ] := product + 1;
               product *:= result[ i ]
           OD;
           result
       END;
    # find the first 10 elements of Sylvester's Seuence #
    []LONG LONG INT seq = SYLVESTER 10;
    # show the sequence and sum the reciprocals #
    LONG LONG REAL reciprocal sum := 0;
    FOR i FROM LWB seq TO UPB seq DO
        print( ( whole( seq[ i ], 0 ), newline ) );
        reciprocal sum +:= 1 / seq[ i ]
    OD;
    print( ( "Sum of reciprocals: ", reciprocal sum, newline ) )
END
