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

package org.jf.smalidea.debugging;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.ui.tree.ExtraDebugNodesProvider;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.jf.smalidea.debugging.utils.RegistersContext;
import org.jf.smalidea.debugging.utils.SmaliRegisterValue;
import org.jf.smalidea.debugging.value.LazyValue;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SmaliExtraDebugNodesProvider implements ExtraDebugNodesProvider {

    @Override
    public void addExtraNodes(@NotNull EvaluationContext evaluationContext, @NotNull XValueChildrenList children) {
        var contextElement = DebuggerUtils.getInstance().getContextElement(evaluationContext);
        if (contextElement == null) {
            return;
        }

        var registersContext = RegistersContext.wrap(evaluationContext.getProject(), contextElement);
        var values = registersContext.getUserData(RegistersContext.SMALI_LAZY_VALUES_KEY);
        if (values == null) {
            return;
        }

        int totalRegistersCount = 0;
        int paramRegistersCount = 0;
        for (LazyValue<?> lv : values) {
            if (lv.getRegisterName().startsWith("p")) {
                paramRegistersCount++;
            } else {
                totalRegistersCount = Integer.max(totalRegistersCount, lv.getRegisterNumber() + 1);
            }
        }
        int firstParamRegister = totalRegistersCount - paramRegistersCount;
        var allLazyRegisters = values.stream()
                .filter((lv) -> lv.getRegisterName().startsWith("v"))
                .collect(Collectors.toList());

        children.addTopGroup(createGroup(
                ".params", allLazyRegisters, evaluationContext, (LazyValue<?> lazyRegister) -> {
                    if (lazyRegister.getRegisterNumber() >= firstParamRegister) {
                        return "p" + (lazyRegister.getRegisterNumber() - firstParamRegister) + " (" +
                               lazyRegister.getRegisterName() + ")";
                    } else {
                        return null;
                    }
                }
        ));
        children.addTopGroup(createGroup(
                ".locals", allLazyRegisters, evaluationContext, (LazyValue<?> lazyRegister) -> {
                    if (lazyRegister.getRegisterNumber() < firstParamRegister) {
                        return lazyRegister.getRegisterName();
                    } else {
                        return null;
                    }
                }
        ));
    }

    private XValueGroup createGroup(
            String name,
            List<LazyValue<?>> allLazyRegisters,
            EvaluationContext evaluationContext,
            Function<LazyValue<?>, String> calcName
    ) {
        var managerThread = (DebuggerManagerThreadImpl) evaluationContext.getDebugProcess().getManagerThread();
        return new XValueGroup(name) {
            @Override public void computeChildren(@NotNull XCompositeNode node) {
                node.addChildren(XValueChildrenList.EMPTY, false);
                managerThread.schedule(new DebuggerCommandImpl() {
                    @Override protected void action() throws Exception {
                        XValueChildrenList paramRegisters = collectRegisters(
                                allLazyRegisters,
                                evaluationContext,
                                calcName
                        );
                        node.addChildren(paramRegisters, true);
                    }
                });
            }

            @Override public boolean isAutoExpand() {
                return true;
            }
        };
    }

    private XValueChildrenList collectRegisters(
            List<LazyValue<?>> allLazyRegisters,
            EvaluationContext evaluationContext,
            Function<LazyValue<?>, String> calcName
    ) {
        var result = new XValueChildrenList();
        try {
            for (LazyValue<?> lazyRegister : allLazyRegisters) {
                lazyRegister.setEvaluationContext(evaluationContext);
                String name = calcName.apply(lazyRegister);
                if (name == null) {
                    continue;
                }

                SmaliRegisterValue registerValue = new SmaliRegisterValue(name, lazyRegister, evaluationContext);
                result.add(registerValue);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return result;
    }
}
