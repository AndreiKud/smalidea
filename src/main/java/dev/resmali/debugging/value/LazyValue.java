/*
 * Copyright 2016, Google Inc.
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

package dev.resmali.debugging.value;

import com.google.common.collect.Lists;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.jetbrains.jdi.LocalVariableImpl;
import com.jetbrains.jdi.SlotLocalVariable;
import com.jetbrains.jdi.StackFrameImpl;
import com.sun.jdi.*;
import dev.resmali.debugging.utils.RegisterSlotUtils;
import dev.resmali.psi.impl.SmaliInstruction;
import dev.resmali.psi.impl.SmaliMethod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

public class LazyValue<T extends Value> implements Value {
    protected final String registerName;
    protected final int registerNumber;
    protected final Project project;
    protected final SmaliMethod method;
    protected final String type;

    private EvaluationContext evaluationContext;
    private Value value;
    private static volatile java.lang.reflect.Field slotField = null;
    private static final Logger LOG = Logger.getInstance(LazyValue.class);

    private static final Value EMPTY_SENTINEL = new Value() {
        @Override
        public Type type() {
            return null;
        }

        @Override
        public VirtualMachine virtualMachine() {
            return null;
        }
    };

    public LazyValue(SmaliMethod method, Project project, int registerNumber, String registerName, String type) {
        this.method = method;
        this.project = project;
        this.registerNumber = registerNumber;
        this.registerName = registerName;
        this.type = type;
    }

    public static LazyValue<?> create(
            @Nonnull SmaliMethod method,
            @Nonnull Project project,
            int registerNumber,
            String registerName,
            @Nonnull String type
    ) {
        if (type.equals("B")) {
            return new LazyByteValue(method, project, registerNumber, registerName, type);
        } else if (type.equals("S")) {
            return new LazyShortValue(method, project, registerNumber, registerName, type);
        } else if (type.equals("J")) {
            return new LazyLongValue(method, project, registerNumber, registerName, type);
        } else if (type.equals("I")) {
            return new LazyIntegerValue(method, project, registerNumber, registerName, type);
        } else if (type.equals("F")) {
            return new LazyFloatValue(method, project, registerNumber, registerName, type);
        } else if (type.equals("D")) {
            return new LazyDoubleValue(method, project, registerNumber, registerName, type);
        } else if (type.equals("Z")) {
            return new LazyBooleanValue(method, project, registerNumber, registerName, type);
        } else if (type.equals("C")) {
            return new LazyCharValue(method, project, registerNumber, registerName, type);
        } else if (type.equals("V")) {
            return new LazyVoidValue(method, project, registerNumber, registerName, type);
        } else if (type.startsWith("[")) {
            return new LazyArrayReference(method, project, registerNumber, registerName, type);
        } else if (type.equals("Ljava/lang/String;")) {
            return new LazyStringReference(method, project, registerNumber, registerName, type);
        } else if (type.equals("Ljava/lang/Class;")) {
            return new LazyClassObjectReference(method, project, registerNumber, registerName, type);
        } else if (type.equals("Ljava/lang/ThreadGroup;")) {
            return new LazyThreadGroupReference(method, project, registerNumber, registerName, type);
        } else if (type.equals("Ljava/lang/Thread;")) {
            return new LazyThreadReference(method, project, registerNumber, registerName, type);
        } else if (type.equals("Ljava/lang/ClassLoader;")) {
            return new LazyClassLoaderReference(method, project, registerNumber, registerName, type);
        } else if (type.startsWith("L")) {
            return new LazyObjectReference<>(method, project, registerNumber, registerName, type);
        }
        return new LazyValue<>(method, project, registerNumber, registerName, type);
    }

    @Nullable
    public T getNullableValue(boolean allowNull) {
        if (value == null) {
            try {
                if (evaluationContext == null) {
                    final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(project).getContext();
                    evaluationContext = debuggerContext.createEvaluationContext();
                    if (evaluationContext == null) {
                        if (!allowNull) {
                            throw new IllegalStateException("Can't create evaluation context for " + this);
                        }
                        return null;
                    }
                }
                value = evaluateRegister(evaluationContext, method, registerNumber);
                evaluationContext = null;
            } catch (EvaluateException ex) {
                LOG.debug("Failed to evaluate register " + registerName, ex);
            }
        }
        if (value == null && !allowNull) {
            throw new IllegalStateException("Null value is not allowed for " + this);
        }
        return (T) value;
    }

    @Nonnull
    protected T getValue() {
        T value = getNullableValue(false);
        assert value != null;
        return value;
    }

    @Override
    public Type type() {
        return getValue().type();
    }

    @Override
    public VirtualMachine virtualMachine() {
        if (evaluationContext != null) {
            return ((VirtualMachineProxyImpl) evaluationContext.getDebugProcess()
                    .getVirtualMachineProxy()).getVirtualMachine();
        } else {
            final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(project).getContext();
            final DebugProcessImpl process = debuggerContext.getDebugProcess();
            if (process != null) {
                return process.getVirtualMachineProxy().getVirtualMachine();
            }
        }
        return null;
    }

    public void setEvaluationContext(@Nonnull EvaluationContext evaluationContext) {
        this.evaluationContext = evaluationContext;
    }

    @Override
    public boolean equals(Object obj) {
        Value value = getNullableValue(true);
        if (value != null) {
            return value.equals(obj);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        Value value = getNullableValue(true);
        if (value != null) {
            return value.hashCode();
        }
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "LazyValue(name " + registerName + ", slot " + registerNumber + ", type \"" + type + "\")";
    }

    public String getRegisterName() {
        return registerName;
    }

    public int getRegisterNumber() {
        return registerNumber;
    }

    public SmaliMethod getMethod() {
        return method;
    }

    @Nullable
    public Value evaluateRegister(EvaluationContext context, final SmaliMethod smaliMethod, final int registerNum)
            throws EvaluateException {
        int registerCount = ApplicationManager.getApplication()
                .runReadAction((ThrowableComputable<Integer, EvaluateException>) smaliMethod::getRegisterCount);
        if (registerNum >= registerCount) {
            return null;
        }

        final StackFrameProxy frameProxy = context.getFrameProxy();
        if (frameProxy == null) {
            return null;
        }

        Location currentLocation = frameProxy.location();
        if (currentLocation == null) {
            return null;
        }

        Method method = currentLocation.method();

        int methodSize = 0;
        for (SmaliInstruction instruction : smaliMethod.getInstructions()) {
            methodSize += instruction.getInstructionSize();
        }
        Location endLocation = null;
        for (int endCodeIndex = (methodSize / 2) - 1; endCodeIndex >= 0; endCodeIndex--) {
            endLocation = method.locationOfCodeIndex(endCodeIndex);
            if (endLocation != null) {
                break;
            }
        }
        if (endLocation == null) {
            return null;
        }

        StackFrameImpl frameImpl = (StackFrameImpl) frameProxy.getStackFrame();
        int slotNumber = RegisterSlotUtils.mapForVirtualMachine(
                frameProxy.getStackFrame().virtualMachine(),
                smaliMethod,
                registerNum
        );

        var results = Stream.<Callable<Value>>of(
                () -> tryGetValueByMatchingVisibleVariable(frameImpl, slotNumber),
                () -> tryGetValueByDexlibType(frameImpl, slotNumber),
                () -> tryGetValueByGuessingPrimitive(frameImpl, slotNumber)
        ).map(strategy -> {
            try {
                return strategy.call();
            } catch (Exception e) {
                if (!(e instanceof ValueNotFoundException)) {
                    LOG.debug(e);
                }
                return EMPTY_SENTINEL;
            }
        }).filter(val -> val != EMPTY_SENTINEL).limit(1).toList();

        if (results.isEmpty()) {
            return null;
        } else {
            return results.get(0);
        }
    }

    private Value tryGetValueByMatchingVisibleVariable(StackFrameImpl frameImpl, int slotNumber)
            throws NoSuchFieldException, IllegalAccessException, AbsentInformationException, ValueNotFoundException {
        try {
            String signature = null;
            for (LocalVariable frameLocalVariable : frameImpl.visibleVariables()) {
                int slot = (int) (getSlotField().get(frameLocalVariable));
                if (slot == slotNumber) {
                    signature = frameLocalVariable.signature();
                    break;
                }
            }
            if (signature != null) {
                String finalSignature = signature;
                SlotLocalVariable slotLocalVariable = new SlotLocalVariable() {
                    @Override
                    public int slot() {
                        return slotNumber;
                    }

                    @Override
                    public String signature() {
                        return finalSignature;
                    }
                };
                Value[] values = frameImpl.getSlotsValues(Lists.newArrayList(slotLocalVariable));
                return values[0];
            }
            throw new ValueNotFoundException();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOG.error(e);
            throw e;
        }
    }

    private Value tryGetValueByDexlibType(StackFrameImpl frameImpl, int slotNumber) {
        SlotLocalVariable slotLocalVariable = new SlotLocalVariable() {
            @Override
            public int slot() {
                return slotNumber;
            }

            @Override
            public String signature() {
                return type;
            }
        };
        Value[] values = frameImpl.getSlotsValues(Lists.newArrayList(slotLocalVariable));
        return values[0];
    }

    // Dexlib's primitive types are guessed by literal value, not always accurate.
    private Value tryGetValueByGuessingPrimitive(StackFrameImpl frameImpl, int slotNumber) {
        SlotLocalVariable slotLocalVariable = new SlotLocalVariable() {
            @Override
            public int slot() {
                return slotNumber;
            }

            @Override
            public String signature() {
                String guess;
                if (type.equals("I")) {
                    guess = "F";
                } else if (type.equals("J")) {
                    guess = "D";
                } else {
                    guess = "I";
                }
                return guess;
            }
        };
        Value[] values = frameImpl.getSlotsValues(Lists.newArrayList(slotLocalVariable));
        return values[0];
    }

    private static java.lang.reflect.Field getSlotField() throws NoSuchFieldException {
        if (slotField == null) {
            synchronized (LazyValue.class) {
                if (slotField == null) {
                    java.lang.reflect.Field f = LocalVariableImpl.class.getDeclaredField("slot");
                    f.setAccessible(true);
                    slotField = f;
                }
            }
        }
        return slotField;
    }

    private static class ValueNotFoundException extends Exception {
        public ValueNotFoundException() {
            super();
        }
    }
}
