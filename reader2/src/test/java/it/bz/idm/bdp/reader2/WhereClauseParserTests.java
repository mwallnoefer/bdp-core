package it.bz.idm.bdp.reader2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import it.bz.idm.bdp.reader2.utils.miniparser.Token;
import it.bz.idm.bdp.reader2.utils.querybuilder.WhereClauseParser;

public class WhereClauseParserTests {

	@Test
	public void testSpecialChars() {
		String input = "a.eq.";
		WhereClauseParser we = new WhereClauseParser(input);
		Token ast = we.parse();
		assertEquals("logical_op_and{clause_or_logical_op{clause{{alias=a}{operator=eq}list_or_value{{value=}}}}}", ast.format());

		we.setInput("a.eq.null");
		ast = we.parse();
		assertEquals("logical_op_and{clause_or_logical_op{clause{{alias=a}{operator=eq}list_or_value{{value=null}}}}}", ast.format());

		we.setInput("a.bbi.(1,2.3,7.0000)");
		ast = we.parse();
		assertEquals("logical_op_and{clause_or_logical_op{clause{{alias=a}{operator=bbi}list_or_value{list{{value=1}{value=2.3}{value=7.0000}}}}}}", ast.format());

		we.setInput("a.ire..*\\,3");
		ast = we.parse();
		assertEquals("logical_op_and{clause_or_logical_op{clause{{alias=a}{operator=ire}list_or_value{{value=.*,3}}}}}", ast.format());
	}

}
