/**
 * Copyright © 2017-2018  David Walton
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.digger.util.vt;

import net.digger.util.fsm.ActionHandler;
import net.digger.util.fsm.EventData;
import net.digger.util.fsm.EventMapper;
import net.digger.util.fsm.StateData;
import net.digger.util.fsm.StateMachine;

/**
 * State machine for VT/ANSI parser.
 * 
 * Implements Paul Flo Williams' state machine (https://vt100.net/emu/dec_ansi_parser).
 * Loosely based on Joshua Haberman's vtparse (https://github.com/haberman/vtparse).
 * @author walton
 */
public class VTParserStateMachine extends StateMachine<Action, State, Character> {
	public VTParserStateMachine(ActionHandler<Action, State, Character> handler, EventMapper<Character> mapper) {
		super(State.GROUND, handler, mapper, (char)0x00, (char)0xFF);
		initStateMachine();
	}
	
	private void initStateMachine() {
		// ANYWHERE transitions
		addState(ANYWHERE, NO_ACTION, NO_ACTION, (state) -> {
			state.addEvent((char)0x18, Action.EXECUTE, State.GROUND);
			state.addEvent((char)0x1a, Action.EXECUTE, State.GROUND);
			state.addEvent((char)0x1b, NO_ACTION, State.ESCAPE);
			state.addEvents((char)0x80, (char)0x8f, Action.EXECUTE, State.GROUND);
			state.addEvent((char)0x90, NO_ACTION, State.DCS_ENTRY);
			state.addEvents((char)0x91, (char)0x97, Action.EXECUTE, State.GROUND);
			state.addEvent((char)0x98, NO_ACTION, State.SOS_PM_APC_STRING);
			state.addEvent((char)0x99, Action.EXECUTE, State.GROUND);
			state.addEvent((char)0x9a, Action.EXECUTE, State.GROUND);
			state.addEvent((char)0x9b, NO_ACTION, State.CSI_ENTRY);
			state.addEvent((char)0x9c, NO_ACTION, State.GROUND);
			state.addEvent((char)0x9d, NO_ACTION, State.OSC_STRING);
			state.addEvent((char)0x9e, NO_ACTION, State.SOS_PM_APC_STRING);
			state.addEvent((char)0x9f, NO_ACTION, State.SOS_PM_APC_STRING);
		}); 
		// GROUND state transitions
		addState(State.GROUND, NO_ACTION, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.EXECUTE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x7f, Action.PRINT, NO_TRANSITION);
		});
		// ESCAPE state transitions
		addState(State.ESCAPE, Action.CLEAR, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.EXECUTE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x2f, Action.COLLECT, State.ESCAPE_INTERMEDIATE);
			state.addEvents((char)0x30, (char)0x4f, Action.ESC_DISPATCH, State.GROUND);
			state.addEvent((char)0x50, NO_ACTION, State.DCS_ENTRY);
			state.addEvents((char)0x51, (char)0x57, Action.ESC_DISPATCH, State.GROUND);
			state.addEvent((char)0x58, NO_ACTION, State.SOS_PM_APC_STRING);
			state.addEvent((char)0x59, Action.ESC_DISPATCH, State.GROUND);
			state.addEvent((char)0x5a, Action.ESC_DISPATCH, State.GROUND);
			state.addEvent((char)0x5b, NO_ACTION, State.CSI_ENTRY);
			state.addEvent((char)0x5c, Action.ESC_DISPATCH, State.GROUND);
			state.addEvent((char)0x5d, NO_ACTION, State.OSC_STRING);
			state.addEvent((char)0x5e, NO_ACTION, State.SOS_PM_APC_STRING);
			state.addEvent((char)0x5f, NO_ACTION, State.SOS_PM_APC_STRING);
			state.addEvents((char)0x60, (char)0x7e, Action.ESC_DISPATCH, State.GROUND);
			state.addEvent((char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// ESCAPE_INTERMEDIATE state transitions
		addState(State.ESCAPE_INTERMEDIATE, NO_ACTION, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.EXECUTE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x2f, Action.COLLECT, NO_TRANSITION);
			state.addEvents((char)0x30, (char)0x7e, Action.ESC_DISPATCH, State.GROUND);
			state.addEvent((char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// CSI_ENTRY state transitions
		addState(State.CSI_ENTRY, Action.CLEAR, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.EXECUTE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x2f, Action.COLLECT, State.CSI_INTERMEDIATE);
			state.addEvents((char)0x30, (char)0x39, Action.PARAM, State.CSI_PARAM);
			state.addEvent((char)0x3a, NO_ACTION, State.CSI_IGNORE);
			state.addEvent((char)0x3b, Action.PARAM, State.CSI_PARAM);
			state.addEvents((char)0x3c, (char)0x3f, Action.COLLECT, State.CSI_PARAM);
			state.addEvents((char)0x40, (char)0x7e, Action.CSI_DISPATCH, State.GROUND);
			state.addEvent((char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// CSI_IGNORE state transitions
		addState(State.CSI_IGNORE, NO_ACTION, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.EXECUTE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x3f, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x40, (char)0x7e, NO_ACTION, State.GROUND);
			state.addEvent((char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// CSI_PARAM state transitions
		addState(State.CSI_PARAM, NO_ACTION, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.EXECUTE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x2f, Action.COLLECT, State.CSI_INTERMEDIATE);
			state.addEvents((char)0x30, (char)0x39, Action.PARAM, NO_TRANSITION);
			state.addEvent((char)0x3a, NO_ACTION, State.CSI_IGNORE);
			state.addEvent((char)0x3b, Action.PARAM, NO_TRANSITION);
			state.addEvents((char)0x3c, (char)0x3f, NO_ACTION, State.CSI_IGNORE);
			state.addEvents((char)0x40, (char)0x7e, Action.CSI_DISPATCH, State.GROUND);
			state.addEvent((char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// CSI_INTERMEDIATE state transitions
		addState(State.CSI_INTERMEDIATE, NO_ACTION, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.EXECUTE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.EXECUTE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x2f, Action.COLLECT, NO_TRANSITION);
			state.addEvents((char)0x30, (char)0x3f, NO_ACTION, State.CSI_IGNORE);
			state.addEvents((char)0x40, (char)0x7e, Action.CSI_DISPATCH, State.GROUND);
			state.addEvent((char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// DCS_ENTRY state transitions
		addState(State.DCS_ENTRY, Action.CLEAR, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.IGNORE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x2f, Action.COLLECT, State.DCS_INTERMEDIATE);
			state.addEvents((char)0x30, (char)0x39, Action.PARAM, State.DCS_PARAM);
			state.addEvent((char)0x3a, NO_ACTION, State.DCS_IGNORE);
			state.addEvent((char)0x3b, Action.PARAM, State.DCS_PARAM);
			state.addEvents((char)0x3c, (char)0x3f, Action.COLLECT, State.DCS_PARAM);
			state.addEvents((char)0x40, (char)0x7e, NO_ACTION, State.DCS_PASSTHROUGH);
			state.addEvent((char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// DCS_INTERMEDIATE state transitions
		addState(State.DCS_INTERMEDIATE, NO_ACTION, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.IGNORE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x2f, Action.COLLECT, NO_TRANSITION);
			state.addEvents((char)0x30, (char)0x3f, NO_ACTION, State.DCS_IGNORE);
			state.addEvents((char)0x40, (char)0x7e, NO_ACTION, State.DCS_PASSTHROUGH);
			state.addEvent((char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// DCS_IGNORE state transitions
		addState(State.DCS_IGNORE, NO_ACTION, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.IGNORE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// DCS_PARAM state transitions
		addState(State.DCS_PARAM, NO_ACTION, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.IGNORE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x2f, Action.COLLECT, State.DCS_INTERMEDIATE);
			state.addEvents((char)0x30, (char)0x39, Action.PARAM, NO_TRANSITION);
			state.addEvent((char)0x3a, NO_ACTION, State.DCS_IGNORE);
			state.addEvent((char)0x3b, Action.PARAM, NO_TRANSITION);
			state.addEvents((char)0x3c, (char)0x3f, NO_ACTION, State.DCS_IGNORE);
			state.addEvents((char)0x40, (char)0x7e, NO_ACTION, State.DCS_PASSTHROUGH);
			state.addEvent((char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// DCS_PASSTHROUGH state transitions
		addState(State.DCS_PASSTHROUGH, Action.DCS_HOOK, Action.DCS_UNHOOK, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.DCS_PUT, NO_TRANSITION);
			state.addEvent((char)0x19, Action.DCS_PUT, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.DCS_PUT, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x7e, Action.DCS_PUT, NO_TRANSITION);
			state.addEvent((char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// SOS_PM_APC_STRING state transitions
		addState(State.SOS_PM_APC_STRING, NO_ACTION, NO_ACTION, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.IGNORE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x7f, Action.IGNORE, NO_TRANSITION);
		});
		// OSC_STRING state transitions
		addState(State.OSC_STRING, Action.OSC_START, Action.OSC_END, (state) -> {
			state.addEvents((char)0x00, (char)0x17, Action.IGNORE, NO_TRANSITION);
			state.addEvent((char)0x19, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x1c, (char)0x1f, Action.IGNORE, NO_TRANSITION);
			state.addEvents((char)0x20, (char)0x7f, Action.OSC_PUT, NO_TRANSITION);
		});
	}

	/**
	 * Test program to ensure all states and transitions are defined.
	 * @param args
	 */
	public static void main(String[] args) {
		VTParserStateMachine stateMachine = new VTParserStateMachine(null, null);
		// check tables
		for (State state : State.values()) {
			StateData<Action, State, Character> stateData = stateMachine.getStateData(state);
			if (stateData == null) {
				throw new RuntimeException("No data table defined for state " + state);
			}
			if (state == null) {
				// don't bother looking up chars in ANYWHERE table
				continue;
			}
			for (char ch=0; ch<0x7f; ch++) {
				EventData<Action, State> eventData = stateMachine.getEventData(state, ch);
				if (eventData == null) {
					throw new RuntimeException("No transition defined for state " + state + ", char 0x" + Integer.toHexString(ch));
				}
			}
		}
		System.out.println("All necessary transitions defined.");
		System.out.println(stateMachine.getDOT(true));
	}
}
