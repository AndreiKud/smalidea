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

import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.LocalVariablesUtil;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.*;
import com.jetbrains.jdi.SlotLocalVariable;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import dev.resmali.SmaliFileType;
import dev.resmali.debugging.SmaliCodeFragmentFactory;
import dev.resmali.debugging.value.LazyValue;

public class SmaliRegisterValue extends XNamedValue {
    private final LazyValue<?> lazyValue;
    private final EvaluationContext evaluationContext;
    private Value value;

    public SmaliRegisterValue(
            @NlsSafe @NotNull String name,
            LazyValue<?> lazyValue,
            EvaluationContext evaluationContext
    ) {
        super(name);
        this.lazyValue = lazyValue;
        this.evaluationContext = evaluationContext;
    }

    @Override public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        var managerThread = ((DebugProcessImpl) evaluationContext.getDebugProcess()).getManagerThread();
        managerThread.schedule(new DebuggerCommandImpl() {
            @Override protected void action() throws Exception {
                lazyValue.setEvaluationContext(evaluationContext);
                value = lazyValue.getNullableValue(true);

                if (value == null) {
                    node.setPresentation(null, null, "null", false);
                    return;
                }

                Type type = value.type();
                boolean hasChildren = value instanceof ObjectReference;
                node.setPresentation(null, type.name(), value.toString(), hasChildren);
            }
        });
    }

    @Override public void computeChildren(@NotNull XCompositeNode node) {
        var managerThread = ((DebugProcessImpl) evaluationContext.getDebugProcess()).getManagerThread();
        managerThread.schedule(new DebuggerCommandImpl() {
            @Override protected void action() {
                assert value != null;
                if (!(value instanceof ObjectReference objRef)) {
                    node.addChildren(XValueChildrenList.EMPTY, true);
                    return;
                }

                DebugProcessImpl debugProcess = (DebugProcessImpl) evaluationContext.getDebugProcess();
                NodeManagerImpl nodeManager = debugProcess.getXdebugProcess().getNodeManager();
                SmaliValueDescriptorImpl descriptor = new SmaliValueDescriptorImpl(
                        evaluationContext.getProject(),
                        lazyValue.getRegisterName(),
                        objRef
                );

                // TODO: SmaliRegisterModifier for children to use registers in evaluations
                JavaValue javaValue = JavaValue.create(
                        null,
                        descriptor,
                        (EvaluationContextImpl) evaluationContext,
                        nodeManager,
                        true
                );
                javaValue.computeChildren(node);
            }
        });
    }

    @Override public @Nullable XValueModifier getModifier() {

        // TODO: Signature without an actual value?
        if (value == null) {
            return null;
        }
        return new SmaliRegisterModifier();
    }

    class SmaliRegisterModifier extends XValueModifier {

        @Override public void calculateInitialValueEditorText(XInitialValueCallback callback) {
            if (value == null) {
                callback.setValue("null");
            } else if (value instanceof CharValue charValue) {
                char c = charValue.value();
                callback.setValue("'" + c + "'");
            } else if (value instanceof PrimitiveValue primitiveValue) {
                callback.setValue(primitiveValue.toString());
            } else if (value instanceof StringReference stringReference) {
                String str = stringReference.value();
                callback.setValue("\"" + str + "\"");
            } else {
                callback.setValue(null);
            }
        }

        @Override public void setValue(@NotNull XExpression expression, @NotNull XModificationCallback callback) {
            var managerThread = ((DebugProcessImpl) evaluationContext.getDebugProcess()).getManagerThread();
            managerThread.schedule(new SuspendContextCommandImpl((SuspendContextImpl) evaluationContext.getSuspendContext()) {
                @Override public void contextAction(@NotNull SuspendContextImpl suspendContext) {
                    try {
                        //noinspection ExtractMethodRecommender
                        String expr = expression.getExpression();
                        TextWithImports textWithImports = new TextWithImportsImpl(
                                CodeFragmentKind.CODE_BLOCK,
                                expr,
                                "java.lang.Boolean," +
                                "java.lang.Byte," +
                                "java.lang.Character," +
                                "java.lang.Double," +
                                "java.lang.Float," +
                                "java.lang.Integer," +
                                "java.lang.Long," +
                                "java.lang.Short," +
                                "java.lang.String",
                                SmaliFileType.INSTANCE
                        );
                        var project = evaluationContext.getProject();
                        var codeFragmentFactory = new SmaliCodeFragmentFactory();
                        var contextElement = DebuggerUtils.getInstance().getContextElement(evaluationContext);
                        var ctx = RegistersContext.wrap(project, contextElement);
                        var codeFragment = codeFragmentFactory.createPsiCodeFragmentImpl(textWithImports, ctx, project);

                        var evaluator = codeFragmentFactory.getEvaluatorBuilder()
                                .build(codeFragment, ContextUtil.getSourcePosition(evaluationContext));

                        Value newValue = evaluator.evaluate(evaluationContext);
                        StackFrameProxyImpl frameProxy = (StackFrameProxyImpl) evaluationContext.getFrameProxy();
                        StackFrame frame = frameProxy.getStackFrame();

                        int slot = RegisterSlotUtils.mapForVirtualMachine(
                                frame.virtualMachine(),
                                lazyValue.getMethod(),
                                lazyValue.getRegisterNumber()
                        );
                        var slotVariable = new SlotLocalVariable() {
                            @Override public int slot() {
                                return slot;
                            }

                            @Override public String signature() {
                                return lazyValue.type().signature();
                            }
                        };

                        if (newValue instanceof LazyValue<?>) {
                            newValue = ((LazyValue<?>) newValue).getNullableValue(true);
                        }

                        LocalVariablesUtil.setValue(frame, slotVariable, newValue);
                        callback.valueModified();

                    } catch (Exception e) {
                        callback.errorOccurred(e.getMessage());
                    }
                }
            });
        }
    }
}
