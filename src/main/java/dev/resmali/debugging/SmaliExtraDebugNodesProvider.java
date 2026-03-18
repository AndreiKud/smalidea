package dev.resmali.debugging;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.ui.tree.ExtraDebugNodesProvider;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import dev.resmali.util.SmaliLogger;
import org.jetbrains.annotations.NotNull;
import dev.resmali.debugging.utils.RegistersContext;
import dev.resmali.debugging.utils.SmaliRegisterValue;
import dev.resmali.debugging.value.LazyValue;

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
                        if (node.isObsolete()) {
                            return;
                        }
                        XValueChildrenList paramRegisters = collectRegisters(
                                allLazyRegisters,
                                evaluationContext,
                                calcName
                        );
                        if (paramRegisters != null) {
                            node.addChildren(paramRegisters, true);
                        }
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
            SmaliLogger.INSTANCE.error(ex);
            return null;
        }
        return result;
    }
}
