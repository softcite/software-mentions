package org.grobid.core.utilities;

/**
 * Generate a random hexadecimal key of selected length
 *
 */
public class HexaKeyGen {

    /**
     * The random number generator
     */
    protected static java.util.Random r = new java.util.Random();

    /**
     * Set of characters that is valid, in our case the hexadecimal codes.
     */
    protected static final char[] goodChar = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    'a', 'b', 'c', 'd', 'e', 'f' };

    protected static final char[] nCNameStartGoodChar = { 'a', 'b', 'c', 'd', 'e', 'f' };

    /**
     * Generate a random hexadecimal key of selected length
     * @param length the selected length of the key
     * @param nCName if true, the key must be a valid NCName (start with a non-digit, 
     * and avoid an extended list of special charcaters)
     * @return a generated key
     */
    public static String getHexaKey(int length, boolean nCName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i == 0 && nCName)
                sb.append(nCNameStartGoodChar[r.nextInt(nCNameStartGoodChar.length)]);
            else
                sb.append(goodChar[r.nextInt(goodChar.length)]);
        }
        return sb.toString();
    }

}
