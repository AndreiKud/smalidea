/*
 * Copyright 2026, Andrei Kudryavtsev (andreikudrya1995@gmail.com).
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package dev.resmali.debugging.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.sun.jdi.VirtualMachine;
import dev.resmali.psi.impl.SmaliMethod;

public class RegisterSlotUtils {

    public static int mapForVirtualMachine(final VirtualMachine vm, final SmaliMethod smaliMethod, final int register) {
        if (vm.version().equals("1.5.0")) {
            return mapRegisterForDalvik(smaliMethod, register);
        } else if (vm.version().equals("0") || vm.version().equals("8")) {
            // Newer versions of art (P+? I think) use an openjdk jvmti implementation, that doesn't need any register
            // remapping
            return register;
        } else {
            // For older versions of art
            return mapRegisterForArt(smaliMethod, register);
        }
    }

    private static int mapRegisterForArt(final SmaliMethod smaliMethod, final int register) {
        return ApplicationManager.getApplication().runReadAction((Computable<Integer>) () -> {
            int totalRegisters = smaliMethod.getRegisterCount();
            int parameterRegisters = smaliMethod.getParameterRegisterCount();

            // For ART, the parameter registers are rotated to the front
            if (register >= (totalRegisters - parameterRegisters)) {
                return register - (totalRegisters - parameterRegisters);
            }
            return register + parameterRegisters;
        });
    }

    private static int mapRegisterForDalvik(final SmaliMethod smaliMethod, final int register) {
        return ApplicationManager.getApplication().runReadAction((Computable<Integer>) () -> {
            if (smaliMethod.getModifierList().hasModifierProperty("static")) {
                return register;
            }

            int totalRegisters = smaliMethod.getRegisterCount();
            int parameterRegisters = smaliMethod.getParameterRegisterCount();

            // For dalvik, p0 is mapped to register 1, and register 0 is mapped to register 1000
            if (register == (totalRegisters - parameterRegisters)) {
                return 0;
            }
            if (register == 0) {
                return 1000;
            }
            return register;
        });
    }
}
