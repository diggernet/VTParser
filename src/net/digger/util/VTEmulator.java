package net.digger.util;

import java.util.List;

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
 * Interface definition for an emulator based on VTParser.
 * Action handlers do nothing by default, and can be overridden to provide functionality.
 */
public interface VTEmulator {
	/**
	 * Called for the CSI_DISPATCH action.
	 * @param ch Final character of the escape sequence.
	 * @param intermediateChars List of private marker and intermediate characters.
	 * @param params List of parameters.
	 */
	default public void actionCSIDispatch(char ch, List<Character> intermediateChars, List<Integer> params) {};
	/**
	 * Called for the DCS_HOOK action.
	 * @param ch Final character of the escape sequence.
	 * @param intermediateChars List of private marker and intermediate characters.
	 * @param params List of parameters.
	 */
	default public void actionDCSHook(char ch, List<Character> intermediateChars, List<Integer> params) {};
	/**
	 * Called for the DCS_PUT action.
	 * @param ch Final character of the escape sequence.
	 */
	default public void actionDCSPut(char ch) {};
	/**
	 * Called for the DCS_UNHOOK action.
	 */
	default public void actionDCSUnhook() {};
	/**
	 * Called for the ERROR action.
	 */
	default public void actionError() {};
	/**
	 * Called for the ESC_DISPATCH action.
	 * @param ch Final character of the escape sequence.
	 * @param intermediateChars List of private marker and intermediate characters.
	 */
	default public void actionEscapeDispatch(char ch, List<Character> intermediateChars) {};
	/**
	 * Called for the EXECUTE action.
	 * @param ch Final character of the escape sequence.
	 */
	default public void actionExecute(char ch) {};
	/**
	 * Called for the OSC_END action.
	 */
	default public void actionOSCEnd() {};
	/**
	 * Called for the OSC_PUT action.
	 * @param ch Final character of the escape sequence.
	 */
	default public void actionOSCPut(char ch) {};
	/**
	 * Called for the OSC_START action.
	 */
	default public void actionOSCStart() {};
	/**
	 * Called for the PRINT action.
	 * @param ch Final character of the escape sequence.
	 */
	default public void actionPrint(char ch) {};
}
