package net.digger.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright © 2017  David Walton
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

/**
 * State tables for VT/ANSI parser.
 * 
 * Implements Paul Flo Williams' state machine (http://vt100.net/emu/dec_ansi_parser).
 * Loosely based on Joshua Haberman's vtparse (https://github.com/haberman/vtparse).
 * @author walton
 */
public class VTParserTables {
	/**
	 * List of parser states.
	 */
	public enum State {
		NO_STATE,
		CSI_ENTRY,
		CSI_IGNORE,
		CSI_INTERMEDIATE,
		CSI_PARAM,
		DCS_ENTRY,
		DCS_IGNORE,
		DCS_INTERMEDIATE,
		DCS_PARAM,
		DCS_PASSTHROUGH,
		ESCAPE,
		ESCAPE_INTERMEDIATE,
		GROUND,
		OSC_STRING,
		SOS_PM_APC_STRING,
	};

	/**
	 * List of parser actions.
	 */
	public enum Action {
		NO_ACTION,
		CLEAR,
		COLLECT,
		CSI_DISPATCH,
		DCS_HOOK,
		DCS_PUT,
		DCS_UNHOOK,
		ESC_DISPATCH,
		EXECUTE,
		IGNORE,
		OSC_END,
		OSC_PUT,
		OSC_START,
		PARAM,
		PRINT,
		ERROR,
	};

	/**
	 * Class to hold the Action to take and State to transition to for a character event.
	 */
	public static class Transition {
		/**
		 * The Action to take for this event.
		 * NO_ACTION means do nothing.
		 */
		public final Action action;
		/**
		 * The State to transition to for this event.
		 * NO_STATE means stay in the current state.
		 */
		public final State state;
		
		public Transition(Action action, State state) {
			this.action = action;
			this.state = state;
		}
	}
	
	/**
	 * Data for a parser state.
	 */
	public static class StateTable {
		/**
		 * Action to take when entering this state.
		 */
		public final Action onEntry;
		/**
		 * Action to take when leaving this state.
		 */
		public final Action onExit;
		/**
		 * List of character events recognized by this state, and the associated Actions and State transitions.
		 */
		private Map<Character, Transition> transitions;
		
		public StateTable(Action onEntry, Action onExit) {
			this.onEntry = onEntry;
			this.onExit = onExit;
			transitions = new HashMap<>();
		}
		
		/**
		 * Add a character event to this state.
		 * @param ch Character to react to.  Must be 0x00-0xff.
		 * 		(This is int not char, only to avoid lots of casting in the static block)
		 * @param action Action to take, or NO_ACTION.
		 * @param state State to transition to, or NO_STATE.
		 * @throws IllegalArgumentException If ch is out of range.
		 */
		private void addTransition(int ch, Action action, State state) throws IllegalArgumentException {
			if ((ch < 0x00) || (ch > 0xff)) {
				throw new IllegalArgumentException("Key must be 0x00-0xff. Received 0x" + Integer.toHexString(ch) + "(" + (char)ch + ").");
			}
			transitions.put((char)ch, new Transition(action, state));
		}
		
		/**
		 * Add a block of character events to this state.
		 * @param first First character to react to.  Must be 0x00-0xff.
		 * 		(This is int not char, only to avoid lots of casting in the static block)
		 * @param last Last character to react to.  Must be 0x00-0xff.
		 * 		(This is int not char, only to avoid lots of casting in the static block)
		 * @param action Action to take, or NO_ACTION.
		 * @param state State to transition to, or NO_STATE.
		 * @throws IllegalArgumentException If first or last are out of range.
		 */
		private void addTransitions(int first, int last, Action action, State state) throws IllegalArgumentException {
			if ((first < 0x00) || (first > 0xff) || (last < 0x00) || (last > 0xff)) {
				throw new IllegalArgumentException("Key must be 0x00-0xff. Received range 0x"
						+ Integer.toHexString(first) + "(" + (char)first + ") to 0x"
						+ Integer.toHexString(last) + "(" + (char)last + ").");
			}
			Transition trans = new Transition(action, state);
			for (char ch=(char)first; ch<=(char)last; ch++) {
				transitions.put(ch, trans);
			}
		}
		
		/**
		 * Checks if this state will respond to the given character event.
		 * @param ch Character to check for.
		 * @return
		 */
		public boolean hasTransition(char ch) {
			// This protocol is really only designed for 8-bit characters.
			// If something higher (Unicode) comes in, treat it as 0xff.
			ch = (char)Math.min(0xff, ch);
			// "There are no explicit actions shown for incoming codes in 
			// the GR area (A0-FF). In all states, these codes are treated 
			// identically to GL codes 20-7F.
			if (ch > 0x9f) {
				ch &= 0x7f;
			}
			return transitions.containsKey(ch);
		}

		/**
		 * Returns the Transition (if any) for the given character event, or null.
		 * @param ch Character to look up.
		 * @return
		 */
		public Transition getTransition(char ch) {
			// This protocol is really only designed for 8-bit characters.
			// If something higher (Unicode) comes in, treat it as 0xff.
			ch = (char)Math.min(0xff, ch);
			// "There are no explicit actions shown for incoming codes in 
			// the GR area (A0-FF). In all states, these codes are treated 
			// identically to GL codes 20-7F.
			if (ch > 0x9f) {
				ch &= 0x7f;
			}
			return transitions.get(ch);
		}
	}

	private static final Map<State, StateTable> states;
	static {
		Map<State, StateTable> st = new HashMap<>();
		StateTable table;
		
		// ANYWHERE transitions
		table = new StateTable(Action.NO_ACTION, Action.NO_ACTION);
		table.addTransition(0x18, Action.EXECUTE, State.GROUND);
		table.addTransition(0x1a, Action.EXECUTE, State.GROUND);
		table.addTransition(0x1b, Action.NO_ACTION, State.ESCAPE);
		table.addTransitions(0x80, 0x8f, Action.EXECUTE, State.GROUND);
		table.addTransition(0x90, Action.NO_ACTION, State.DCS_ENTRY);
		table.addTransitions(0x91, 0x97, Action.EXECUTE, State.GROUND);
		table.addTransition(0x98, Action.NO_ACTION, State.SOS_PM_APC_STRING);
		table.addTransition(0x99, Action.EXECUTE, State.GROUND);
		table.addTransition(0x9a, Action.EXECUTE, State.GROUND);
		table.addTransition(0x9b, Action.NO_ACTION, State.CSI_ENTRY);
		table.addTransition(0x9c, Action.NO_ACTION, State.GROUND);
		table.addTransition(0x9d, Action.NO_ACTION, State.OSC_STRING);
		table.addTransition(0x9e, Action.NO_ACTION, State.SOS_PM_APC_STRING);
		table.addTransition(0x9f, Action.NO_ACTION, State.SOS_PM_APC_STRING);
		st.put(State.NO_STATE, table);
		
		// GROUND state transitions
		//FIXME: should GROUND have onEntry = CLEAR, to avoid passing old data to EXECUTE and PRINT?
		table = new StateTable(Action.NO_ACTION, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.EXECUTE, State.NO_STATE);
		table.addTransition(0x19, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x20, 0x7f, Action.PRINT, State.NO_STATE);
		st.put(State.GROUND, table);
		
		// ESCAPE state transitions
		table = new StateTable(Action.CLEAR, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.EXECUTE, State.NO_STATE);
		table.addTransition(0x19, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x20, 0x2f, Action.COLLECT, State.ESCAPE_INTERMEDIATE);
		table.addTransitions(0x30, 0x4f, Action.ESC_DISPATCH, State.GROUND);
		table.addTransition(0x50, Action.NO_ACTION, State.DCS_ENTRY);
		table.addTransitions(0x51, 0x57, Action.ESC_DISPATCH, State.GROUND);
		table.addTransition(0x58, Action.NO_ACTION, State.SOS_PM_APC_STRING);
		table.addTransition(0x59, Action.ESC_DISPATCH, State.GROUND);
		table.addTransition(0x5a, Action.ESC_DISPATCH, State.GROUND);
		table.addTransition(0x5b, Action.NO_ACTION, State.CSI_ENTRY);
		table.addTransition(0x5c, Action.ESC_DISPATCH, State.GROUND);
		table.addTransition(0x5d, Action.NO_ACTION, State.OSC_STRING);
		table.addTransition(0x5e, Action.NO_ACTION, State.SOS_PM_APC_STRING);
		table.addTransition(0x5f, Action.NO_ACTION, State.SOS_PM_APC_STRING);
		table.addTransitions(0x60, 0x7e, Action.ESC_DISPATCH, State.GROUND);
		table.addTransition(0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.ESCAPE, table);
		
		// ESCAPE_INTERMEDIATE state transitions
		table = new StateTable(Action.NO_ACTION, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.EXECUTE, State.NO_STATE);
		table.addTransition(0x19, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x20, 0x2f, Action.COLLECT, State.NO_STATE);
		table.addTransitions(0x30, 0x7e, Action.ESC_DISPATCH, State.GROUND);
		table.addTransition(0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.ESCAPE_INTERMEDIATE, table);
		
		// CSI_ENTRY state transitions
		table = new StateTable(Action.CLEAR, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.EXECUTE, State.NO_STATE);
		table.addTransition(0x19, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x20, 0x2f, Action.COLLECT, State.CSI_INTERMEDIATE);
		table.addTransitions(0x30, 0x39, Action.PARAM, State.CSI_PARAM);
		table.addTransition(0x3a, Action.NO_ACTION, State.CSI_IGNORE);
		table.addTransition(0x3b, Action.PARAM, State.CSI_PARAM);
		table.addTransitions(0x3c, 0x3f, Action.COLLECT, State.CSI_PARAM);
		table.addTransitions(0x40, 0x7e, Action.CSI_DISPATCH, State.GROUND);
		table.addTransition(0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.CSI_ENTRY, table);
		
		// CSI_IGNORE state transitions
		table = new StateTable(Action.NO_ACTION, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.EXECUTE, State.NO_STATE);
		table.addTransition(0x19, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x20, 0x3f, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x40, 0x7e, Action.NO_ACTION, State.GROUND);
		table.addTransition(0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.CSI_IGNORE, table);
		
		// CSI_PARAM state transitions
		table = new StateTable(Action.NO_ACTION, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.EXECUTE, State.NO_STATE);
		table.addTransition(0x19, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x20, 0x2f, Action.COLLECT, State.CSI_INTERMEDIATE);
		table.addTransitions(0x30, 0x39, Action.PARAM, State.NO_STATE);
		table.addTransition(0x3a, Action.NO_ACTION, State.CSI_IGNORE);
		table.addTransition(0x3b, Action.PARAM, State.NO_STATE);
		table.addTransitions(0x3c, 0x3f, Action.NO_ACTION, State.CSI_IGNORE);
		table.addTransitions(0x40, 0x7e, Action.CSI_DISPATCH, State.GROUND);
		table.addTransition(0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.CSI_PARAM, table);
		
		// CSI_INTERMEDIATE state transitions
		table = new StateTable(Action.NO_ACTION, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.EXECUTE, State.NO_STATE);
		table.addTransition(0x19, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.EXECUTE, State.NO_STATE);
		table.addTransitions(0x20, 0x2f, Action.COLLECT, State.NO_STATE);
		table.addTransitions(0x30, 0x3f, Action.NO_ACTION, State.CSI_IGNORE);
		table.addTransitions(0x40, 0x7e, Action.CSI_DISPATCH, State.GROUND);
		table.addTransition(0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.CSI_INTERMEDIATE, table);
		
		// DCS_ENTRY state transitions
		table = new StateTable(Action.CLEAR, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.IGNORE, State.NO_STATE);
		table.addTransition(0x19, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x20, 0x2f, Action.COLLECT, State.DCS_INTERMEDIATE);
		table.addTransitions(0x30, 0x39, Action.PARAM, State.DCS_PARAM);
		table.addTransition(0x3a, Action.NO_ACTION, State.DCS_IGNORE);
		table.addTransition(0x3b, Action.PARAM, State.DCS_PARAM);
		table.addTransitions(0x3c, 0x3f, Action.COLLECT, State.DCS_PARAM);
		table.addTransitions(0x40, 0x7e, Action.NO_ACTION, State.DCS_PASSTHROUGH);
		table.addTransition(0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.DCS_ENTRY, table);
		
		// DCS_INTERMEDIATE state transitions
		table = new StateTable(Action.NO_ACTION, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.IGNORE, State.NO_STATE);
		table.addTransition(0x19, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x20, 0x2f, Action.COLLECT, State.NO_STATE);
		table.addTransitions(0x30, 0x3f, Action.NO_ACTION, State.DCS_IGNORE);
		table.addTransitions(0x40, 0x7e, Action.NO_ACTION, State.DCS_PASSTHROUGH);
		table.addTransition(0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.DCS_INTERMEDIATE, table);
		
		// DCS_IGNORE state transitions
		table = new StateTable(Action.NO_ACTION, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.IGNORE, State.NO_STATE);
		table.addTransition(0x19, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x20, 0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.DCS_IGNORE, table);
		
		// DCS_PARAM state transitions
		table = new StateTable(Action.NO_ACTION, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.IGNORE, State.NO_STATE);
		table.addTransition(0x19, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x20, 0x2f, Action.COLLECT, State.DCS_INTERMEDIATE);
		table.addTransitions(0x30, 0x39, Action.PARAM, State.NO_STATE);
		table.addTransition(0x3a, Action.NO_ACTION, State.DCS_IGNORE);
		table.addTransition(0x3b, Action.PARAM, State.NO_STATE);
		table.addTransitions(0x3c, 0x3f, Action.NO_ACTION, State.DCS_IGNORE);
		table.addTransitions(0x40, 0x7e, Action.NO_ACTION, State.DCS_PASSTHROUGH);
		table.addTransition(0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.DCS_PARAM, table);
		
		// DCS_PASSTHROUGH state transitions
		table = new StateTable(Action.DCS_HOOK, Action.DCS_UNHOOK);
		table.addTransitions(0x00, 0x17, Action.DCS_PUT, State.NO_STATE);
		table.addTransition(0x19, Action.DCS_PUT, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.DCS_PUT, State.NO_STATE);
		table.addTransitions(0x20, 0x7e, Action.DCS_PUT, State.NO_STATE);
		table.addTransition(0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.DCS_PASSTHROUGH, table);
		
		// SOS_PM_APC_STRING state transitions
		table = new StateTable(Action.NO_ACTION, Action.NO_ACTION);
		table.addTransitions(0x00, 0x17, Action.IGNORE, State.NO_STATE);
		table.addTransition(0x19, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x20, 0x7f, Action.IGNORE, State.NO_STATE);
		st.put(State.SOS_PM_APC_STRING, table);
		
		// OSC_STRING state transitions
		table = new StateTable(Action.OSC_START, Action.OSC_END);
		table.addTransitions(0x00, 0x17, Action.IGNORE, State.NO_STATE);
		table.addTransition(0x19, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x1c, 0x1f, Action.IGNORE, State.NO_STATE);
		table.addTransitions(0x20, 0x7f, Action.OSC_PUT, State.NO_STATE);
		st.put(State.OSC_STRING, table);
		
		states = Collections.unmodifiableMap(st);
	}

	/**
	 * Returns the table data for a given State.
	 * @param state
	 * @return
	 */
	public static StateTable getState(State state) {
		return states.get(state);
	}
	
	/**
	 * Returns the Action and State transition details for a given State and character event.
	 * If the given State doesn't recognize the given character, looks for a transition
	 * which is valid for any State.
	 * @param state Starting state for character event.
	 * @param ch Character to get Transition for.
	 * @return
 */
	public static Transition getTransition(State state, char ch) {
		// if given state has a transition for this char, return it
		StateTable table = states.get(state);
		if ((table != null) && table.hasTransition(ch)) {
			return table.getTransition(ch);
		}
		// otherwise look for an ANYWHERE transition for this char
		table = states.get(State.NO_STATE);
		if ((table != null) && table.hasTransition(ch)) {
			return table.getTransition(ch);
		}
		return null;
	}

	/**
	 * Test program to ensure all states and transitions are defined.
	 * @param args
	 */
	public static void main(String[] args) {
		// check tables
		for (State state : State.values()) {
			StateTable table = getState(state);
			if (table == null) {
				throw new RuntimeException("No data table defined for state " + state);
			}
			if (state == State.NO_STATE) {
				// don't bother looking up chars in ANYWHERE table
				continue;
			}
			for (char ch=0; ch<0xff; ch++) {
				Transition trans = VTParserTables.getTransition(state, ch);
				if (trans == null) {
					throw new RuntimeException("No transition defined for state " + state + ", char 0x" + Integer.toHexString(ch));
				}
				if (trans.action == null) {
					throw new RuntimeException("No transition action defined for state " + state + ", char 0x" + Integer.toHexString(ch));
				}
				if (trans.state == null) {
					throw new RuntimeException("No transition state defined for state " + state + ", char 0x" + Integer.toHexString(ch));
				}
			}
		}
		System.out.println("All necessary transitions defined.");
	}
}
