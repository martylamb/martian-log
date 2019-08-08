package com.martiansoftware.log;

//   Copyright 2017 Martian Software, Inc.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

/**
 * Utility class used to peek up the stack at its invoker
 * 
 * @author <a href="http://martylamb.com">Marty Lamb</a>
 */
class CallerOf {
    private final static SecurityManager MY_SECURITY_MANAGER = new SecurityManager();

    public static Class stackAncestor(int generationsBack) {
        if (generationsBack < 0) throw new IllegalArgumentException("CallingClass.get() requires an offset >= 0");
        return MY_SECURITY_MANAGER.getCallingClass(generationsBack);
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
