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

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import net.digger.util.fsm.ActionHandler;

/**
 * VT/ANSI parser.
 * <p>
 * This class parses text for escape sequences, and runs the caller-provided callback
 * with the details of each sequence.  That callback will need to implement whichever
 * sequences it chooses to support.
 * <p>
 * Implements Paul Flo Williams' state machine (https://vt100.net/emu/dec_ansi_parser).
 * Loosely based on Joshua Haberman's vtparse (https://github.com/haberman/vtparse).
 * 
 * @author walton
 */
public class VTParser implements ActionHandler<Action, State, Character> {
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
	 * Parser state machine.
	 */
	private VTParserStateMachine stateMachine;

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
	 * Create a new instance of VTParser, and provide a VTEmulator implementation.
	 * <p>
	 * Event values 0xA0-0xFF are treated as 0x20-0x7F and values above 0xFF are treated as 0x7F.
	 * 
	 * @param emulator VTEmulator implementation called when an escape sequence is parsed.
	 */
	public VTParser(VTEmulator emulator) {
		this(emulator, false);
	}

	/**
	 * Create a new instance of VTParser using only 7-bit transitions, and provide a VTEmulator implementation.
	 * If use7bits=true, event values above 0x7F are treated as 0x7F.
	 * This avoids the special handling of 0x80-0x9f, when working with a character set (such as
	 * CP-437) where those are printable.
	 * Otherwise, values 0xA0-0xFF are treated as 0x20-0x7F and values above 0xFF are treated as 0x7F.
	 * 
	 * @param emulator VTEmulator implementation called when an escape sequence is parsed.
	 * @param use7bits Flag to process the text as 7-bit ASCII instead of 8-bit.
	 */
	public VTParser(VTEmulator emulator, boolean use7bits) {
		if (use7bits) {
			stateMachine = new VTParserStateMachine(this, (event) -> {
				// Any chars >0x7F will be looked up as 0x7F.
				return (char)Math.min(0x7f, event);
			});
		} else {
			stateMachine = new VTParserStateMachine(this, (event) -> {
				// This protocol is really only designed for 8-bit characters.
				// If something higher (Unicode) comes in, look it up as 0xff.
				event = (char)Math.min(0xff, event);
				// "There are no explicit actions shown for incoming codes in 
				// the GR area (A0-FF). In all states, these codes are treated 
				// identically to GL codes 20-7F."
				if (event > 0x9f) {
					event = (char)(event & 0x7f);
				}
				return event;
			});
		}
		this.emulator = emulator;
		clear();
	}

	/**
	 * Process a character.
	 * 
	 * @param ch Character to process.
	 */
	public void parse(char ch) {
		stateMachine.handleEvent(ch);
	}
	
	/**
	 * Process a string.
	 * 
	 * @param str String to process.
	 */
	public void parse(String str) {
		for (int i=0; i<str.length(); i++) {
			stateMachine.handleEvent(str.charAt(i));
		}
	}

	/**
	 * Returns the given text with all escape sequences stripped out of it.
	 * If use7bits=true, event values above 0x7F are treated as 0x7F.
	 * This avoids the special handling of 0x80-0x9f, when working with a character set (such as
	 * CP-437) where those are printable.
	 * Otherwise, values 0xA0-0xFF are treated as 0x20-0x7F and values above 0xFF are treated as 0x7F.
	 * 
	 * @param text Text to process.
	 * @param use7bits Flag to process the text as 7-bit ASCII instead of 8-bit.
	 * @return Text with all escape sequences removed.
	 */
	public static String stripString(String text, boolean use7bits) {
		StringBuilder sb = new StringBuilder();
		VTParser vtp = new VTParser(new VTEmulator() {
			@Override
			public void actionExecute(char ch) {
				sb.append(ch);
			}
			@Override
			public void actionPrint(char ch) {
				sb.append(ch);
			}
		}, use7bits);
		vtp.parse(text);
		return sb.toString();
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
	
	@Override
	public void onEntry(State state, Action action) {
		doAction(action, (char)0x00);
	}

	@Override
	public void onEvent(State state, Character event, Action action) {
		doAction(action, event);
	}

	@Override
	public void onExit(State state, Action action) {
		doAction(action, (char)0x00);
	}
	
	/**
	 * Handle the given Action with the given char.
	 * 
	 * @param action Action to perform.
	 * @param ch Character event being handled
	 */
	private void doAction(Action action, char ch) {
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
				if (emulator != null) {
					emulator.actionPrint(ch);
				}
				break;

			// Actions to handle internally:
			case IGNORE:
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
	}
	
	/**
	 * Convert intermediateChars array to a List.
	 * 
	 * @return List of intermediate chars.
	 */
	private List<Character> intermediateCharList() {
		List<Character> charlist = new ArrayList<>();
		for (int i=0; i<intermediateCharCount; i++) {
			charlist.add(intermediateChars[i]);
		}
		return charlist;
	}

	/**
	 * Convert params array to a List.
	 * 
	 * @return List of params.
	 */
	private List<Integer> paramList() {
		List<Integer> paramlist = new ArrayList<>();
		for (int i=0; i<paramCount; i++) {
			paramlist.add(params[i]);
		}
		return paramlist;
	}

	/**
	 * Test program, parses text from STDIN.
	 * @param args Command-line arguments for test program.
	 */
	public static void main(String[] args) {
		StringBuilder sb = new StringBuilder();
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
				sb.append(ch);
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
				sb.append(ch);
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
			sb.setLength(0);
			parser.parse(line);
			System.out.println(sb.toString());
		}
		in.close();
	}
}
