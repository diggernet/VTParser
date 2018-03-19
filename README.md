# VTParser
VTParser is an ANSI and DEC VT escape sequence parser.
It enables implementing an ANSI or VT-xxx emulator (or a subset), without
needing to do the hard part of parsing the text for escape sequences and
correctly handling the subtle complications found there.

It implements [Paul Flo Williams' state machine](https://vt100.net/emu/dec_ansi_parser),
and is loosely based on [Joshua Haberman's vtparse](https://github.com/haberman/vtparse).


## Maven configuration

		<dependency>
			<groupId>net.digger</groupId>
			<artifactId>vt-parser</artifactId>
			<version>1.0.0</version>
		</dependency>


## Usage
1. Implement the VTEmulator interface, which contains handlers for all the
possible ANSI/VT actions.  Implement only those actions you care about, and
ignore the rest.
2. Pass an instance of your VTEmulator implementation to the VTParser constructor.
3. Send your text to VTParser.parse().

For example, if all you care about is printing text with color (using the SGR command),
you can implement these handlers in VTEmulator:

* actionPrint(): ch is a printable character, to be printed in the current text color.
* actionExecute(): ch is a control character (or printable in some character sets).
* actionCSIDispatch(): If ch equals 'm' and intermediateChars is empty, this is an SGR
command, and params contains the list of codes for setting text color.

[JScreen](https://github.com/diggernet/JScreen) contains a VTEmulator implementation 
in ANSI.java.

## Dependencies
* [Java 8](https://www.oracle.com/java)
* [StateMachine](https://github.com/diggernet/StateMachine)

## License
VTParser is provided under the terms of the GNU LGPLv3.
