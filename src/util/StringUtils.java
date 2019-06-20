
package util;

import java.util.Comparator;

public class StringUtils {

    // This comparator was taken directly from com/xilinx/rapidwright/util/StringTools.java
    static Comparator<String> naturalComparator;
	static {
		naturalComparator = new Comparator<String>() {
			private boolean isDigit(char c){
				return 0x30 <= c && c <= 0x39;
			}
			
			@Override
			public int compare(String a, String b){
				int ai = 0, bi = 0;
				while(ai < a.length() && bi < b.length()){
					if(isDigit(a.charAt(ai)) && isDigit(b.charAt(bi))){
						int aStart = ai, bStart = bi;
						while(ai < a.length() && isDigit(a.charAt(ai))) ai++;
						while(bi < b.length() && isDigit(b.charAt(bi))) bi++;
						int aInt = Integer.parseInt(a.substring(aStart,ai));
						int bInt = Integer.parseInt(b.substring(bStart,bi));
						if(aInt != bInt) return aInt - bInt;
					} else if(a.charAt(ai) != b.charAt(bi)) 
						return a.charAt(ai) - b.charAt(bi);
					ai++; bi++;
				}
				return a.length() - b.length();
			}
		};
    }
    

}