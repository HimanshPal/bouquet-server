/*******************************************************************************
 * Copyright © Squid Solutions, 2016
 *
 * This file is part of Open Bouquet software.
 *  
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * There is a special FOSS exception to the terms and conditions of the 
 * licenses as they are applied to this program. See LICENSE.txt in
 * the directory of this program distribution.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Squid Solutions also offers commercial licenses with additional warranties,
 * professional functionalities or services. If you purchase a commercial
 * license, then it supersedes and replaces any other agreement between
 * you and Squid Solutions (above licenses and LICENSE.txt included).
 * See http://www.squidsolutions.com/EnterpriseBouquet/
 *******************************************************************************/
package com.squid.kraken.v4.model;

import java.util.List;

/**
 *  This is a simple pojo that wraps the navigation reply
 * @author sergefantino
 *
 */
public class NavigationReply {
	
	private NavigationQuery query;
	
	private List<NavigationItem> result;

	public NavigationReply(NavigationQuery query, List<NavigationItem> result) {
		super();
		this.query = query;
		this.result = result;
	}

	public NavigationQuery getQuery() {
		return query;
	}

	public List<NavigationItem> getResult() {
		return result;
	}

}