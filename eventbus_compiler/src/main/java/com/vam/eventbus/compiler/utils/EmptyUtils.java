package com.vam.eventbus.compiler.utils;

import java.util.Collection;
import java.util.Map;

public class EmptyUtils {

    public static boolean isEmpty(CharSequence s) {return s==null||s.length()==0;}
    public static boolean isEmpty(Collection<?> coll) {return coll==null||coll.isEmpty();}
    public static boolean isEmpty(Map<?,?> map) {return map==null||map.isEmpty();}

}
