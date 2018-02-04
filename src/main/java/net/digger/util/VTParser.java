package net.digger.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import net.digger.util.VTParserTables.Action;
import net.digger.util.VTParserTables.State;
import net.digger.util.VTParserTables.Transition;

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
 * VT/ANSI parser.
 * This class parses text for escape sequences, and runs the caller-provided callback
 * with the details of each sequence.  That callback will need to implement whichever
 * sequences it chooses to support.
 * 
 * Implements Paul Flo Williams' state machine (https://vt100.net/emu/dec_ansi_parser).
 * Loosely based on Joshua Haberman's vtparse (https://github.com/haberman/vtparse).
 * @author walton
 */
public class VTParser {
	/**
	 * Maximum number of private markers and intermediate characters to collect.
	 * If more than this many private markers or intermediate characters are
	 * encountered by Action.COLLECT, the control sequence is considered 
	 * malformed, and ignored.
	 * <p>
	 * This effects the following states, which contain Action.COLLECT:<br>
	 * State.ESCAPE_INTERMEDIATE:<br>
	 * 		When transitioning to State.GROUND, Action.ESC_DISPATCH is ignored.<br>
	 * State.CSI_INTERMEDIATE:<br>
	 * 		When transitioning to State.GROUND, Action.CSI_DISPATCH is ignored.<br>
	 * 		(Effectively, behaves as State.CSI_IGNORE instead.)<br>
	 * State.DCS_INTERMEDIATE:<br>
	 * 		After transitioning to State.DCS_PASSTHROUGH, these actions are ignored:<br>
	 * 			Action.DCS_HOOK, Action.DCS_PUT, Action.DCS_UNHOOK<br>
	 * 		(Effectively, behaves as State.DCS_IGNORE instead.)<br>
	 */
	private static final int MAX_INTERMEDIATE_CHARS = 2;
	/**
	 * Maximum number of parameters to collect.
	 * If more than this many parameters are encountered by Action.PARAM,
	 * the extra parameters are simply ignored.
	 */
	private static final int MAX_PARAMS = 16;
	/**
	 * Current state of the state machine.
	 */
	private State currentState;
	/**
	 * Caller-provided emulator implementation.
	 */
	private VTEmulator emulator;
	/**
	 * Current number of private markers and intermediate characters.
	 */
	private int intermediateCharCount;
	/**
	 * Array of private markers and intermediate characters.
	 */
	private char[] intermediateChars;
	/**
	 * Flag to indicate too many private markers and intermediate characters.
	 */
	private boolean tooManyIntermediateChars;
	/**
	 * Current number of parameters.
	 */
	private int paramCount;
	/**
	 * Array of parameters.
	 */
	private Integer[] params;
	/**
	 * Flag to indicate too many parameters.
	 */
	private boolean tooManyParams;
	/**
	 * Flag to indicate using 7-bit transitions.
	 */
	private boolean is7bit;
	
	/**
	 * Create a new instance of VTParser, and provide a VTEmulator implementation.
	 * @param callback Called when an escape sequence is parsed.
	 */
	public VTParser(VTEmulator emulator) {
		currentState = State.GROUND;
		this.emulator = emulator;
		is7bit = false;
		clear();
	}

	/**
	 * Create a new instance of VTParser using only 7-bit transitions, and provide a VTEmulator implementation.
	 * This avoids the special handling of 0x80-0x9f, when working with a character set (such as
	 * CP-437) where those are printable.  Any chars >0x7F will be treated as 0x7F.
	 * @param callback Called when an escape sequence is parsed.
	 */
	public VTParser(VTEmulator emulator, boolean use7bits) {
		currentState = State.GROUND;
		this.emulator = emulator;
		is7bit = use7bits;
		clear();
	}

	/**
	 * Process a character.
	 * @param ch Character to process.
	 * @return Character sent to PRINT or EXECUTE, or null.
	 */
	public Character parse(char ch) {
		return processCharEvent(ch);
	}
	
	/**
	 * Process a string.
	 * @param str String to process.
	 * @return String of characters sent to PRINT or EXECUTE, or empty String.
	 */
	public String parse(String str) {
		StringBuilder sb = new StringBuilder("");
		for (int i=0; i<str.length(); i++) {
			Character ch = processCharEvent(str.charAt(i));
			if (ch != null) {
				sb.append(ch);
			}
		}
		return sb.toString();
	}

	/**
	 * Returns the given text with all escape sequences stripped out of it. 
	 * @param text Text to process.
	 * @return Text with all escape sequences removed.
	 */
	public static String stripString(String text) {
		VTParser vtp = new VTParser(null);
		return vtp.parse(text);
	}
	
	/**
	 * Clear private markers, intermediate characters, and parameters.
	 */
	private void clear() {
		intermediateCharCount = 0;
		intermediateChars = new char[MAX_INTERMEDIATE_CHARS];
		tooManyIntermediateChars = false;
		paramCount = 0;
		params = new Integer[MAX_PARAMS];
		tooManyParams = false;
	}
	
	/**
	 * Process a character through the parser.
	 * @param ch Character to process.
	 * @return Character sent to PRINT or EXECUTE, or null.
	 */
	private Character processCharEvent(char ch) {
		Character printed = null;
		Transition trans;
		if (is7bit) {
			trans = VTParserTables.getTransition(currentState, (char)Math.min(0x7f, ch));
		} else {
			trans = VTParserTables.getTransition(currentState, ch);
		}

		/* Perform up to three actions:
		 *   1. the exit action of the old state
		 *   2. the action associated with the transition
		 *   3. the entry action of the new state
		 */
		if (trans.state != State.NO_STATE) {
			doAction(VTParserTables.getState(currentState).onExit, (char)0x0);
		}

		printed = doAction(trans.action, ch);

		if (trans.state != State.NO_STATE) {
			doAction(VTParserTables.getState(trans.state).onEntry, (char)0x0);
			currentState = trans.state;
		}

		return printed;
	}
	
	/**
	 * Handle the given Action with the given char.
	 * @param action
	 * @param ch
	 * @return
	 */
	private Character doAction(Action action, char ch) {
		Character printed = null;
		
		// Some actions we handle internally (like parsing parameters), others
		// we hand to our client for processing
		switch (action) {
			// Actions to hand off to emulator:
			case CSI_DISPATCH:
				if ((emulator != null) && (!tooManyIntermediateChars)) {
					emulator.actionCSIDispatch(ch, intermediateCharList(), paramList());
				}
				break;
			case DCS_HOOK:
				if ((emulator != null) && (!tooManyIntermediateChars)) {
					emulator.actionDCSHook(ch, intermediateCharList(), paramList());
				}
				break;
			case DCS_PUT:
				if ((emulator != null) && (!tooManyIntermediateChars)) {
					emulator.actionDCSPut(ch);
				}
				break;
			case DCS_UNHOOK:
				if ((emulator != null) && (!tooManyIntermediateChars)) {
					emulator.actionDCSUnhook();
				}
				break;
			case ERROR:
				if (emulator != null) {
					emulator.actionError();
				}
				break;
			case ESC_DISPATCH:
				if ((emulator != null) && (!tooManyIntermediateChars)) {
					emulator.actionEscapeDispatch(ch, intermediateCharList());
				}
				break;
			case EXECUTE:
				printed = ch;
				if (emulator != null) {
					emulator.actionExecute(ch);
				}
				break;
			case OSC_END:
				if (emulator != null) {
					emulator.actionOSCEnd();
				}
				break;
			case OSC_PUT:
				if (emulator != null) {
					emulator.actionOSCPut(ch);
				}
				break;
			case OSC_START:
				if (emulator != null) {
					emulator.actionOSCStart();
				}
				break;
			case PRINT:
				printed = ch;
				if (emulator != null) {
					emulator.actionPrint(ch);
				}
				break;

			// Actions to handle internally:
			case IGNORE:
			case NO_ACTION:
				// do nothing
				break;

			case CLEAR:
				clear();
				break;
				
			case COLLECT:
				// Append the character to the intermediate params
				if ((intermediateCharCount + 1) > MAX_INTERMEDIATE_CHARS) {
					tooManyIntermediateChars = true;
				} else {
					intermediateChars[intermediateCharCount] = ch;
					intermediateCharCount++;
				}
				break;
				
			case PARAM:
				// process the param character
				if (paramCount == 0) {
					// this is the first time we've been in here since CLEAR - set up for first param
					paramCount++;
				}
				if (ch == ';') {
					if ((paramCount + 1) > MAX_PARAMS) {
						tooManyParams = true;
					} else {
						// go to next param
						// note that if param string starts with ';' then we jump to 2nd param
						paramCount++;
					}
				} else if (!tooManyParams) {
					// the character is a digit, and we haven't reached MAX_PARAMS
					int current_param = paramCount - 1;
					if (params[current_param] == null) {
						params[current_param] = 0;
					}
					params[current_param] *= 10;
					params[current_param] += (ch - '0');
				}
				break;
		}
		return printed;
	}
	
	private List<Character> intermediateCharList() {
		List<Character> charlist = new ArrayList<>();
		for (int i=0; i<intermediateCharCount; i++) {
			charlist.add(intermediateChars[i]);
		}
		return charlist;
	}

	private List<Integer> paramList() {
		List<Integer> paramlist = new ArrayList<>();
		for (int i=0; i<paramCount; i++) {
			paramlist.add(params[i]);
		}
		return paramlist;
	}

	/**
	 * Test program, parses text from STDIN.
	 * @param args
	 */
	public static void main(String[] args) {
		VTParser parser = new VTParser(new VTEmulator() {
			@Override
			public void actionCSIDispatch(char ch, List<Character> intermediateChars, List<Integer> params) {
				printAction(Action.CSI_DISPATCH, ch, intermediateChars, params);
			};
			@Override
			public void actionDCSHook(char ch, List<Character> intermediateChars, List<Integer> params) {
				printAction(Action.DCS_HOOK, ch, intermediateChars, params);
			};
			@Override
			public void actionDCSPut(char ch) {
				printAction(Action.DCS_PUT, ch, null, null);
			};
			@Override
			public void actionDCSUnhook() {
				printAction(Action.DCS_UNHOOK, null, null, null);
			};
			@Override
			public void actionError() {
				printAction(Action.ERROR, null, null, null);
			};
			@Override
			public void actionEscapeDispatch(char ch, List<Character> intermediateChars) {
				printAction(Action.ESC_DISPATCH, ch, intermediateChars, null);
			};
			@Override
			public void actionExecute(char ch) {
				printAction(Action.EXECUTE, ch, null, null);
			};
			@Override
			public void actionOSCEnd() {
				printAction(Action.OSC_END, null, null, null);
			};
			@Override
			public void actionOSCPut(char ch) {
				printAction(Action.OSC_PUT, ch, null, null);
			};
			@Override
			public void actionOSCStart() {
				printAction(Action.OSC_START, null, null, null);
			};
			@Override
			public void actionPrint(char ch) {
				printAction(Action.PRINT, ch, null, null);
			};
			
			private void printAction(Action action, Character ch, List<Character> intermediateChars, List<Integer> params) {
				System.out.printf("Received action %s", action);
				if ((ch != null) && (ch != 0)) {
					System.out.printf(", Char: 0x%02x ('%c')\n", (int)ch, ch);
				}
				if ((intermediateChars != null) && !intermediateChars.isEmpty()) {
					System.out.printf("\t%d Intermediate chars: ", intermediateChars.size());
					for (Character intch : intermediateChars) {
						System.out.printf("0x%02x ('%c'), ", (int)intch, intch);
					}
					System.out.println();
				}
				if ((params != null) && !params.isEmpty()) {
					System.out.printf("\t%d Parameters: ", params.size());
					for (Integer param : params) {
						System.out.printf("%d, ", param);
					}
					System.out.println();
				}
				System.out.println();
			}
		});
		
		Scanner in = new Scanner(System.in);
		while (in.hasNextLine()) {
			String line = in.nextLine();
			String printed = parser.parse(line);
			System.out.println(printed);
		}
		in.close();
	}
}
