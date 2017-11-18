/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.martiansoftware.log;

/**
 *
 * @author mlamb
 */
public class CallerOf {
    private final static SecurityManager mySecurityManager = new SecurityManager();

    public static Class stackAncestor(int generationsBack) {
        if (generationsBack < 0) throw new IllegalArgumentException("CallingClass.get() requires an offset >= 0");
        return mySecurityManager.getCallingClass(generationsBack);
    }
    
    public static Class thisMethod() {
        return stackAncestor(1);
    }
    
    private static class SecurityManager extends java.lang.SecurityManager {
        public Class getCallingClass(int offset) {
            Class[] ctx = getClassContext();
//            for (int i = 0; i < ctx.length; ++i) {
//                System.out.format("%d = [%s]%n", i, ctx[i]);
//            }
            return ctx[offset + 2];
        }
    }
}
