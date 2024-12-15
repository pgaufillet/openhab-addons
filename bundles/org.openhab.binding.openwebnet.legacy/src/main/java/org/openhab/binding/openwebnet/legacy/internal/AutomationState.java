/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.openwebnet.legacy.internal;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 *
 * @author Antoine Laydier
 *
 */
@NonNullByDefault
public enum AutomationState {
    UP(2, UpDownType.UP, UpDownType.UP, "Up"),
    DOWN(1, UpDownType.DOWN, UpDownType.DOWN, "Down"),
    STOP(0, StopMoveType.STOP, UnDefType.UNDEF, "Stop");

    private static final List<AutomationState> RESPONSE_LIST = new ArrayList<AutomationState>() {

        private static final long serialVersionUID = 1L;

        {
            add(UP);
            add(DOWN);
            add(STOP);
        }
    };

    public final int id;
    public final Command cmd;
    public final State state;
    private final String text;

    AutomationState(int id, Command cmd, State state, String text) {
        this.id = id;
        this.cmd = cmd;
        this.text = text;
        this.state = state;
    }

    public static @Nullable AutomationState findByCommand(Command cmd) {
        for (AutomationState automationState : RESPONSE_LIST) {
            if (automationState.cmd.equals(cmd)) {
                return automationState;
            }
        }
        return null;
    }

    public static @Nullable AutomationState findCommandFromId(int id) {
        for (AutomationState automationState : RESPONSE_LIST) {
            if (automationState.id == id) {
                return automationState;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return text;
    }
}
