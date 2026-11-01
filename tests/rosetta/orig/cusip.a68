BEGIN # validate CUSIP codes #

    # returns TRUE if cusip string is a valid CUSIP code, FALSE otherwise #
    OP   ISCUSIP = ( STRING cusip string )BOOL:
         IF UPB cusip string - LWB cusip string /= 8
         THEN FALSE # CUSIP code is not 9 characters long #
         ELSE       # CUSIP code has the correct length #

            # returns the base 39 digit corresponding to a character of a CUSIP code #
            PROC cusip digit = ( CHAR c char )INT:
                 IF   STRING cusip digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ*@#"[ AT 0 ];
                      INT digit := 0;
                      char in string( c char, digit, cusip digits )
                 THEN digit
                 ELSE -999 # invalid digit #
                 FI # cusip digit # ;

            []CHAR cusip = cusip string[ AT 1 ];
            INT sum := 0;
            INT check digit = cusip digit( cusip[ UPB cusip ] );
            FOR c pos TO UPB cusip - 1 DO
                INT digit := cusip digit( cusip[ c pos ] );
                IF NOT ODD c pos THEN digit *:= 2 FI;
                sum +:= ( digit OVER 10 ) + ( digit MOD 10 )
            OD;
            ( 10 - ( sum MOD 10 ) ) MOD 10 = check digit
         FI # is cusip # ;

    # task test cases #

    PROC test cusip = ( STRING cusip )VOID:
         print( ( cusip, IF ISCUSIP cusip THEN " valid" ELSE " invalid" FI, newline ) );

    test cusip( "037833100" );
    test cusip( "17275R102" );
    test cusip( "38259P508" );
    test cusip( "594918104" );
    test cusip( "68389X106" );
    test cusip( "68389X105" )
END
