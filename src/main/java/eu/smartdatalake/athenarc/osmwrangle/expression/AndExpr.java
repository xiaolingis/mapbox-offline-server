/*
 * @(#) AndExpr.java	version 2.0   10/7/2019
 *
 * Copyright (C) 2013-2019 Information Management Systems  Institute, Athena R.C., Greece.
 *
 * This library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.smartdatalake.athenarc.osmwrangle.expression;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles sub-expressions connected with a logical AND operation.
 * @author Kostas Patroumpas
 * @version 2.0
 */

/* DEVELOPMENT HISTORY
 * Created by: Kostas Patroumpas, 4/7/2019
 * Last modified: 10/7/2019
 */
public class AndExpr implements Expr {
	
	//List of sub-expressions connected with a logical AND.
    private final List<Expr> children = new ArrayList<>();

    /**
     * Constructor of the class.
     * @param stream   Input stream of tokens.
     * @throws ParseException
     */
    public AndExpr(TokenStream stream) throws ParseException {
        do {
            children.add(new SubExpr(stream));
        } while(stream.consumeIf(TokenType.AND) != null);
    }

    @Override
    public String toString() {
        return children.stream().map(Object::toString).collect(Collectors.joining(" AND "));
    }

    @Override
    public boolean evaluate(Map<String, String> data) {
        for(Expr child : children) 
        {
            if(!child.evaluate(data))		//At least one sub-expression is false
                return false;
        }
        return true;
    }
}