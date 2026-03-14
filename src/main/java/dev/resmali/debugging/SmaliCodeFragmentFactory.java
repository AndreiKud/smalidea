/*
 * Copyright 2014, Google Inc.
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

package dev.resmali.debugging;

import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import dev.resmali.SmaliFileType;
import dev.resmali.SmaliLanguage;
import dev.resmali.debugging.utils.RegistersContext;
import dev.resmali.debugging.value.LazyValue;

import java.util.List;

import static dev.resmali.debugging.utils.RegistersContext.SMALI_LAZY_VALUES_KEY;

public class SmaliCodeFragmentFactory extends DefaultCodeFragmentFactory {

    @Override
    public JavaCodeFragment createPsiCodeFragmentImpl(
            TextWithImports item,
            PsiElement context,
            @NotNull Project project
    ) {
        return ApplicationManager.getApplication().runReadAction((Computable<JavaCodeFragment>) () -> {
            PsiElement wrapped = RegistersContext.wrap(project, context);
            JavaCodeFragment fragment = super.createPsiCodeFragmentImpl(item, wrapped, project);
            List<LazyValue<?>> lazyValues = wrapped.getUserData(SMALI_LAZY_VALUES_KEY);
            if (lazyValues != null) {
                assert fragment != null;
                fragment.putUserData(SMALI_LAZY_VALUES_KEY, lazyValues);
            }
            return fragment;
        });
    }

    @Override public boolean isContextAccepted(PsiElement contextElement) {
        if (contextElement == null) {
            return false;
        }
        return contextElement.getLanguage() == SmaliLanguage.INSTANCE;
    }

    @Override public @NotNull LanguageFileType getFileType() {
        return SmaliFileType.INSTANCE;
    }

    @Override public EvaluatorBuilder getEvaluatorBuilder() {
        final EvaluatorBuilder builder = super.getEvaluatorBuilder();
        return (codeFragment, position) -> {
            ExpressionEvaluator evaluator =
                    ApplicationManager.getApplication().runReadAction((Computable<ExpressionEvaluator>) () -> {
                        try {
                            return builder.build(codeFragment, position);
                        } catch (EvaluateException e) {
                            throw new RuntimeException(e);
                        }
                    });
            return new SmaliExpressionEvaluator(codeFragment, evaluator);
        };
    }
}
