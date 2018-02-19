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

/**
 * List of VTParser States.
 * 
 * @author walton
 */
public enum State {
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
