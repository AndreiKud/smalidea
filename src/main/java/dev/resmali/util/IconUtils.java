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

package dev.resmali.util;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.intellij.ui.RowIcon;
import com.intellij.util.PlatformIcons;
import dev.resmali.psi.iface.SmaliModifierListOwner;
import dev.resmali.psi.impl.SmaliModifierList;

import javax.swing.*;

public class IconUtils {
    public static Icon getElementIcon(SmaliModifierListOwner modifierListOwner, Icon leftIcon) {
        SmaliModifierList modifierList = modifierListOwner.getModifierList();
        int accessFlags = modifierList == null ? 0 : modifierList.getAccessFlags();
        Icon rightIcon = IconUtils.getAccessibilityIcon(accessFlags);
        if (rightIcon != null) {
            return new RowIcon(leftIcon, rightIcon);
        } else {
            return leftIcon;
        }
    }

    public static Icon getAccessibilityIcon(int accessFlags) {
        if (AccessFlags.PUBLIC.isSet(accessFlags)) {
            return PlatformIcons.PUBLIC_ICON;
        } else if (AccessFlags.PRIVATE.isSet(accessFlags)) {
            return PlatformIcons.PRIVATE_ICON;
        } else if (AccessFlags.PROTECTED.isSet(accessFlags)) {
            return PlatformIcons.PROTECTED_ICON;
        } else {
            return PlatformIcons.PACKAGE_LOCAL_ICON;
        }
    }
}
