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
 * Implements Paul Flo Williams' state machine (http://vt100.net/emu/dec_ansi_parser).
 * Loosely based on Joshua Haberman's vtparse (https://github.com/haberman/vtparse).
 * @author walton
 */
public class VTParser {
	/**
	 * Interface definition for the escape sequence callback.
	 */
	public interface VTParserCallback {
		/**
		 * Called when an escape sequence is parsed.
		 * @param action Escape sequence action.  One of CSI_DISPATCH, ERROR, ESC_DISPATCH, EXECUTE, HOOK, 
		 * 		OSC_END, OSC_PUT, OSC_START, PRINT, PUT, UNHOOK
		 * @param ch Final character of the escape sequence.
		 * @param intermediateChars List of private marker and intermediate characters. (Ignore for EXECUTE and PRINT.)
		 * @param params List of parameters. (Ignore for EXECUTE and PRINT.)
		 */
		public void call(Action action, char ch, List<Character> intermediateChars, List<Integer> params);
	}
	
	/**
	 * Maximum number of private markers and intermediate characters to collect.
	 */
	private static final int MAX_INTERMEDIATE_CHARS = 2;
	/**
	 * Maximum number of parameters to collect.
	 */
	private static final int MAX_PARAMS = 16;
	/**
	 * Current state of the state machine.
	 */
	private State currentState;
	/**
	 * Caller-provided escape sequence callback.
	 */
	private VTParserCallback callback;
	/**
	 * Current number of private markers and intermediate characters.
	 */
	private int intermediateCharCount;
	/**
	 * Array of private markers and intermediate characters.
	 */
	private char[] intermediateChars;
	/**
	 * Flag to ignore additional private markers and intermediate characters.
	 */
	private boolean ignoreIntermediateChars;
	/**
	 * Current number of parameters.
	 */
	private int paramCount;
	/**
	 * Array of parameters.
	 */
	private Integer[] params;
	/**
	 * Flag to ignore additional parameters.
	 */
	private boolean ignoreParams;
	
	/**
	 * Create a new instance of VTParser, and provide an escape sequence callback.
	 * @param callback Called when an escape sequence is parsed.
	 */
	public VTParser(VTParserCallback callback) {
		currentState = State.GROUND;
		this.callback = callback;
		clear();
	}

	/**
	 * Process a character.
	 * @param ch Character to process.  Must be 0x00-0xff.
	 * @return Character sent to PRINT or EXECUTE, or null.
	 * @throws IllegalArgumentException If ch is out of range.
	 */
	public Character parse(char ch) throws IllegalArgumentException {
		return processCharEvent(ch);
	}
	
	/**
	 * Process a string.
	 * @param str String to process.  Characters in string must be 0x00-0xff.
	 * @return String of characters sent to PRINT or EXECUTE, or empty String.
	 * @throws IllegalArgumentException If any character in str is out of range.
	 */
	public String parse(String str) throws IllegalArgumentException {
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
	 * @param text Text to process.  Characters in text must be 0x00-0xff.
	 * @return Text with all escape sequences removed.
	 * @throws IllegalArgumentException If any character in text is out of range.
	 */
	public static String stripString(String text) throws IllegalArgumentException {
		VTParser vtp = new VTParser(null);
		return vtp.parse(text);
	}
	
	/**
	 * Clear private markers, intermediate characters, and parameters.
	 */
	private void clear() {
		intermediateCharCount = 0;
		intermediateChars = new char[MAX_INTERMEDIATE_CHARS];
		ignoreIntermediateChars = false;
		paramCount = 0;
		params = new Integer[MAX_PARAMS];
		ignoreParams = false;
	}
	
	/**
	 * Process a character through the parser.
	 * @param ch Character to process.  Must be 0x00-0xff.
	 * @return Character sent to PRINT or EXECUTE, or null.
	 * @throws IllegalArgumentException If ch is out of range.
	 */
	private Character processCharEvent(char ch) throws IllegalArgumentException {
		Character printed = null;
		Transition trans = VTParserTables.getTransition(currentState, ch);

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
			case PRINT:
			case EXECUTE:
				printed = ch;
				// fall through...
			case CSI_DISPATCH:
			case ESC_DISPATCH:
			case DCS_HOOK:
			case DCS_PUT:
			case DCS_UNHOOK:
			case OSC_END:
			case OSC_PUT:
			case OSC_START:
				if (callback != null) {
					List<Character> charlist = new ArrayList<>();
					for (int i=0; i<intermediateCharCount; i++) {
						charlist.add(intermediateChars[i]);
					}
					List<Integer> paramlist = new ArrayList<>();
					for (int i=0; i<paramCount; i++) {
						paramlist.add(params[i]);
					}
					callback.call(action, ch, charlist, paramlist);
				}
				break;

			case IGNORE:
			case NO_ACTION:
				// do nothing
				break;

			case CLEAR:
				clear();
				break;
				
			case COLLECT:
				// From vt100.net:
				// 		"If more than two intermediate characters arrive, the parser can just flag this so that the dispatch can be turned into a null operation."
				// 		FIXME: So should CSI_DISPATCH, ESC_DISPATCH, and DCS_PUT do nothing if ignoreIntermediateChars?
				// Append the character to the intermediate params
				if ((intermediateCharCount + 1) > MAX_INTERMEDIATE_CHARS) {
					ignoreIntermediateChars = true;
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
						ignoreParams = true;
					} else {
						// go to next param
						// note that if param string starts with ';' then we jump to 2nd param
						paramCount++;
					}
				} else if (!ignoreParams) {
					// the character is a digit, and we haven't reached MAX_PARAMS
					int current_param = paramCount - 1;
					if (params[current_param] == null) {
						params[current_param] = 0;
					}
					params[current_param] *= 10;
					params[current_param] += (ch - '0');
				}
				break;
				
			case ERROR:
				callback.call(Action.ERROR, (char)0x0, new ArrayList<Character>(), new ArrayList<Integer>());
				break;
		}
		return printed;
	}

	/**
	 * Test program, parses text from STDIN.
	 * @param args
	 */
	public static void main(String[] args) {
		VTParser parser = new VTParser((action, ch, intermediateChars, params) -> {
			System.out.printf("Received action %s", action);
			if (ch != 0) {
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
