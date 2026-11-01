BEGIN
    # find the numbers the string 123456789 ( with "+/-" optionally inserted  #
    # before each digit ) can generate                                        #

    PR read "sort.incl.a68" PR                    # include sorting utilities #

    # calculate the numbers we can generate and the strings leading to them   #
    # as noted by other solutions, at most 13122 (2*3^8) can be generated     #

    INT max expr = 2 * 3^8;
    [ 1 : max expr ]INT    value;
    [ 1 : max expr ]STRING expression;
    INT expr pos := 0;
    [ 1 : 18 ]CHAR sum string := ".1.2.3.4.5.6.7.8.9";
    []CHAR sign char = []CHAR( "-", " ", "+" )[ AT -1 ];
    FOR s1 FROM -1 TO 0 DO # we don't distinguish between strings starting "+1" and starting " 1" #
       sum string[  1 ] := sign char[ s1 ];
       FOR s2 FROM -1 TO 1 DO
          sum string[  3 ] := sign char[ s2 ];
          FOR s3 FROM -1 TO 1 DO
             sum string[  5 ] := sign char[ s3 ];
             FOR s4 FROM -1 TO 1 DO
                sum string[  7 ] := sign char[ s4 ];
                FOR s5 FROM -1 TO 1 DO
                   sum string[  9 ] := sign char[ s5 ];
                   FOR s6 FROM -1 TO 1 DO
                      sum string[ 11 ] := sign char[ s6 ];
                      FOR s7 FROM -1 TO 1 DO
                         sum string[ 13 ] := sign char[ s7 ];
                         FOR s8 FROM -1 TO 1 DO
                            sum string[ 15 ] := sign char[ s8 ];
                            FOR s9 FROM -1 TO 1 DO
                               sum string[ 17 ] := sign char[ s9 ];
                               INT number := 0;
                               INT part   := IF s1 < 0 THEN -1 ELSE 1 FI;
                               IF s2 = 0 THEN part *:= 10 +:= 2 * SIGN part ELSE number +:= part; part := 2 * s2 FI;
                               IF s3 = 0 THEN part *:= 10 +:= 3 * SIGN part ELSE number +:= part; part := 3 * s3 FI;
                               IF s4 = 0 THEN part *:= 10 +:= 4 * SIGN part ELSE number +:= part; part := 4 * s4 FI;
                               IF s5 = 0 THEN part *:= 10 +:= 5 * SIGN part ELSE number +:= part; part := 5 * s5 FI;
                               IF s6 = 0 THEN part *:= 10 +:= 6 * SIGN part ELSE number +:= part; part := 6 * s6 FI;
                               IF s7 = 0 THEN part *:= 10 +:= 7 * SIGN part ELSE number +:= part; part := 7 * s7 FI;
                               IF s8 = 0 THEN part *:= 10 +:= 8 * SIGN part ELSE number +:= part; part := 8 * s8 FI;
                               IF s9 = 0 THEN part *:= 10 +:= 9 * SIGN part ELSE number +:= part; part := 9 * s9 FI;
                               number   +:= part;
                               expr pos +:= 1;
                               expression[ expr pos ] := sum string;
                               value[      expr pos ] := number
                            OD
                         OD
                      OD
                   OD
                OD
             OD
          OD
       OD
    OD;

    # show the solutions for 100 #
    INT count 100 := 0;
    FOR x pos FROM LWB value TO UPB value DO
        IF value[ x pos ] = 100 THEN count 100 +:= 1 FI
    OD;
    print( ( "100 has ", whole( count 100, 0 ), " solutions:", newline ) );
    FOR x pos FROM LWB value TO UPB value DO
        IF value[ x pos ] = 100 THEN
            print( ( "        " ) );
            STRING s = expression[ x pos ];
            FOR s pos FROM LWB s TO UPB s DO IF s[ s pos ] /= " " THEN print( ( s[ s pos ] ) ) FI OD;
            print( ( newline ) )
        FI
    OD;

    QUICKSORT value; # sort the values (the expressions will now be out of order) #

    # find the number with the most solutions #
    INT this count := 1, max count := 1, max count value := value[ LWB value ];
    FOR v pos FROM LWB value + 1 TO UPB value DO
        IF value[ v pos ] = value[ v pos - 1 ] THEN
            this count +:= 1
        ELSE
            IF this count > max count AND value[ v pos - 1 ] >= 0 THEN
                max count       := this count;
                max count value := value[ v pos - 1 ]
            FI;
            this count := 1
        FI
    OD;
    IF this count > max count THEN
        # the final value has the maximum count #
        max count       := this count;
        max count value := value[ UPB value ]
    FI;
    print( ( whole( max count value, 0 ), " has the maximum number of solutions: " ) );
    print( ( whole( max count, 0 ), newline ) );

    # find the smallest positive number that has no solutions #
    BOOL have solution := TRUE;
    INT  missing value := 1;
    FOR v pos FROM LWB value TO UPB value WHILE have solution DO
        IF value[ v pos ] > 0 THEN
            IF value[ v pos ] /= value[ v pos - 1 ] THEN
                have solution := value[ v pos ] = missing value;
                IF have solution THEN
                    missing value +:= 1
                FI
            FI
        FI
    OD;
    print( ( whole( missing value, 0 ), " is the lowest positive number with no solutions", newline ) );

    # show the ten largest numbers that can be generated #
    print( ( "The 10 largest numbers that can be generated are:", newline ) );
    INT shown := 0;
    FOR v pos FROM UPB value - 1 BY -1 TO LWB value WHILE shown < 10 DO
        IF value[ v pos ] /= value[ v pos + 1 ] THEN
            print( ( " ", whole( value[ v pos + 1 ], 0 ) ) );
            shown +:= 1
        FI
    OD;
    print( ( newline ) )

END
