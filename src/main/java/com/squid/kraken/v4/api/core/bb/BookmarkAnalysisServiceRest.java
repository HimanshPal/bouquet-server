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
package com.squid.kraken.v4.api.core.bb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squid.core.expression.scope.ScopeException;
import com.squid.kraken.v4.api.core.AccessRightsUtils;
import com.squid.kraken.v4.api.core.bb.NavigationQuery.Style;
import com.squid.kraken.v4.api.core.bb.NavigationQuery.Visibility;
import com.squid.kraken.v4.api.core.customer.CoreAuthenticatedServiceRest;
import com.squid.kraken.v4.caching.redis.RedisCacheManager;
import com.squid.kraken.v4.caching.redis.queryworkerserver.QueryWorkerJobStatus;
import com.squid.kraken.v4.core.analysis.engine.hierarchy.DomainHierarchyManager;
import com.squid.kraken.v4.core.analysis.engine.processor.ComputingException;
import com.squid.kraken.v4.core.analysis.engine.project.ProjectManager;
import com.squid.kraken.v4.model.AnalysisQuery;
import com.squid.kraken.v4.model.AnalysisQueryImpl;
import com.squid.kraken.v4.model.AnalysisResult;
import com.squid.kraken.v4.model.Bookmark;
import com.squid.kraken.v4.model.Expression;
import com.squid.kraken.v4.model.ExpressionSuggestion;
import com.squid.kraken.v4.model.Facet;
import com.squid.kraken.v4.model.ObjectType;
import com.squid.kraken.v4.model.Project;
import com.squid.kraken.v4.model.ProjectPK;
import com.squid.kraken.v4.model.ProjectAnalysisJob.OrderBy;
import com.squid.kraken.v4.model.ProjectAnalysisJob.Position;
import com.squid.kraken.v4.model.ProjectAnalysisJob.RollUp;
import com.squid.kraken.v4.model.ValueType;
import com.squid.kraken.v4.model.AccessRight.Role;
import com.squid.kraken.v4.persistence.AppContext;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.Authorization;
import com.wordnik.swagger.annotations.AuthorizationScope;

/**
 * The new BB API
 * 
 * @author sergefantino
 *
 */
@Path("/bb")
@Api(
		value = "bookmark-analysis", 
		hidden = false, 
		description = "this is the new bookmark API intented to provide all the fun without the pain",
		authorizations = { @Authorization(value = "kraken_auth", type = "oauth2", scopes = { @AuthorizationScope(scope = "access", description = "Access") }) })
@Produces({ MediaType.APPLICATION_JSON })
public class BookmarkAnalysisServiceRest  extends CoreAuthenticatedServiceRest implements BookmarkAnalysisServiceConstants {

	static final Logger logger = LoggerFactory.getLogger(BookmarkAnalysisServiceRest.class);

	private final static String BBID_PARAM_NAME = "BBID";
	private final static String FACETID_PARAM_NAME = "FACETID";
	
	@Context
	UriInfo uriInfo;
	
	private BookmarkAnalysisServiceBaseImpl getDelegate() {
		return new BookmarkAnalysisServiceBaseImpl(uriInfo);
	}
	
	public BookmarkAnalysisServiceRest() {
	}

	@GET
	@Path("")
	@ApiOperation(
			value = "List available content",
			notes = "It provides a comprehensive view including projects, domains, folders and bookmarks."
					+ "You can use it to navigate the entire available content, or access a specific content by defining the parent parameter."
					+ "The root parents are /PROJECTS for listing projects and domains, /MYBOOKMARKS to list the user bookmarks and folders, and /SHARED to list the shared bookmarks and folders."
					+ "By default it lists ony the content directly under the parent, but you can set the hierarchy parameter to view content recursively.")
	public NavigationReply listContent(
			@Context HttpServletRequest request,
			@ApiParam(value="filter the content under the parent path") @QueryParam("parent") String parent,
			@ApiParam(value="filter the content by name; q can be a multi-token search string separated by comma") 
			@QueryParam("q") String search,
			@ApiParam(
					value="define the hierarchy mode. FLAT mode return the hierarchy as a flat list, whereas TREE returns it as a folded structure (NIY)",
					allowableValues="TREE, FLAT") 
			@QueryParam("hierarchy") HierarchyMode hierarchyMode,
			@ApiParam(
					value="define the result style. If HUMAN, the API will try to use natural reference for objects, like 'My First Project', 'Account', 'Total Sales'... If MACHINE the API will use canonical references that are invariant, e.g. @'5603ca63c531d744b50823a3bis'. If LEGACY the API will also provide internal compound key to lookup objects in the management API.", allowableValues="LEGACY, MACHINE, HUMAN", defaultValue="HUMAN")
			@QueryParam(STYLE_PARAM) Style style,
			@ApiParam(
					value="filter the result depending on the object visibility", allowableValues="VISIBLE, ALL, HIDDEN", defaultValue="VISIBLE")
			@QueryParam(VISIBILITY_PARAM) Visibility visibility
		) throws ScopeException {
		AppContext userContext = getUserContext(request);
		return getDelegate().listContent(userContext, parent, search, hierarchyMode, style, visibility);
	}

	@GET
	@Path("{" + BBID_PARAM_NAME + "}")
	@ApiOperation(value = "Get an item, can be a Domain or a Bookmark")
	public Object getItem(
			@Context HttpServletRequest request, 
			@PathParam(BBID_PARAM_NAME) String BBID) throws ScopeException {
		AppContext userContext = getUserContext(request);
		return getDelegate().getItem(userContext, BBID);
	}

	@POST
	@Path("{" + BBID_PARAM_NAME + "}")
	@ApiOperation(
			value = "create a new bookmark",
			notes = "")
	public Bookmark createBookmark(
			@Context HttpServletRequest request,
			@ApiParam(value="the analysis query definition", required=true) AnalysisQuery query,
			@PathParam(BBID_PARAM_NAME) String BBID,
			@ApiParam(value="the new bookmark name", required=true) @QueryParam("name") String name,
			@ApiParam(value="the new bookmark folder, can be /MYBOOKMARKS, /MYBOOKMARKS/any/folders or /SHARED/any/folders") @QueryParam("parent") String parent)
	{
		AppContext userContext = getUserContext(request);
		return getDelegate().createBookmark(userContext, query, BBID, name, parent);
	}
	
	/*
	@GET
	@Path("{" + PARAM_NAME + "}/dimensions")
	@ApiOperation(
			value = "Gets the bookmark's dimensions", 
			notes = "This is only usefull in case we want to provide editing capabilities from the bookmark. But it can be ambiguous how to use it in conjonction with the /facets operation",
			response = Dimension.class)
	public List<Dimension> getDimensions(@Context HttpServletRequest request, @PathParam(PARAM_NAME) String BBID) {
		AppContext userContext = getUserContext(request);
		Bookmark bookmark = getBookmark(userContext, BBID);
		BookmarkConfig config = readConfig(bookmark);
		String domainId = config.getDomain();
		DomainPK domainPk = new DomainPK(bookmark.getId().getParent(), domainId);
		List<Dimension> dimensions = DimensionServiceBaseImpl.getInstance().readAll(userContext, domainPk);
		return dimensions;
	}
	*/
	
	/*
	@GET
	@Path("{" + BBID_PARAM_NAME + "}/metrics")
	@ApiOperation(value = "Get the bookmark's metrics", response = MetricExt.class)
	public List<MetricExt> getMetrics(@Context HttpServletRequest request, @PathParam(BBID_PARAM_NAME) String BBID) {
		AppContext userContext = getUserContext(request);
		Bookmark bookmark = getBookmark(userContext, BBID);
		BookmarkConfig config = readConfig(bookmark);
		String domainId = config.getDomain();
		DomainPK domainPk = new DomainPK(bookmark.getId().getParent(), domainId);
		List<MetricExt> metrics = MetricServiceBaseImpl.getInstance().readAll(userContext, domainPk);
		return metrics;
	}
	*/
	
	/*
	@GET
	@Path("{" + BBID_PARAM_NAME + "}/facets")
	@ApiOperation(value = "Get the bookmark's facets using the default BB selection")
	public FacetSelection getFacets(
			@Context HttpServletRequest request, 
			@PathParam(BBID_PARAM_NAME) String BBID
		) throws ComputingException {
		AppContext userContext = getUserContext(request);
		return runFacets(userContext, BBID, null);
	}
	
	@POST
	@Path("{" + BBID_PARAM_NAME + "}/facets")
	@ApiOperation(value = "Get the bookmark's facets using a custom selection")
	public FacetSelection postFacets(
			@Context HttpServletRequest request, 
			@PathParam(BBID_PARAM_NAME) String BBID,
			@ApiParam(value="the selection", required=true) FacetSelection selection
		) throws ComputingException {
		AppContext userContext = getUserContext(request);
		return runFacets(userContext, BBID, selection);
	}
	*/
	
	@GET
	@Path("{" + BBID_PARAM_NAME + "}/scope")
	@ApiOperation(
			value = "Provide information about the expressions available in the bookmark scope",
			notes = "It also allows to check if a given expression is valid in the scope, and further explore the scope if the expression is an object. Using the offset parameter you can get suggestion at the caret position instead of the complete expression value.")
	public ExpressionSuggestion evaluateExpression(
			@Context HttpServletRequest request, 
			@PathParam(BBID_PARAM_NAME) String BBID,
			@ApiParam(value="(optional) the expression to check and get suggestion for, or null in order to get scope level suggestions") 
			@QueryParam("value") String expression,
			@ApiParam(value="(optionnal) caret position in the expression value in order to provide relevant suggestions based on the caret position. By default the suggestion are based on the full expression if provided, or else the entire bookmark scope.") 
			@QueryParam("offset") Integer offset,
			@ApiParam(
					value="(optional) the expression type to filter the suggestions. If undefined all valid expression in the context are returned. ",
					allowMultiple=true,
					allowableValues="DIMENSION, METRIC, RELATION, COLUMN, FUNCTION") 
			@QueryParam("types") ObjectType[] types,
			@ApiParam(
					value="(optional) the expression value to filter the suggestions. If undefined all valid expression in the context are returned. ",
					allowMultiple=true,
					allowableValues="OBJECT, NUMERIC, AGGREGATE, DATE, STRING, CONDITION, DOMAIN, OTHER, ERROR") 
			@QueryParam("values") ValueType[] values
			) throws ScopeException
	{
		AppContext userContext = getUserContext(request);
		return getDelegate().evaluateExpression(userContext, BBID, expression, offset, types, values);
	}

	@GET
	@Path("{" + BBID_PARAM_NAME + "}/facets/{" + FACETID_PARAM_NAME + "}")
	@ApiOperation(value = "Get facet content using the default BB selection")
	public Facet getFacet(
			@Context HttpServletRequest request, 
			@PathParam(BBID_PARAM_NAME) String BBID,
			@PathParam(FACETID_PARAM_NAME) String facetId,
			@ApiParam(value="search the facet values using a list of tokens")@QueryParam("q") String search,
			@ApiParam(
					value = "Define the filters to apply to results. A filter must be a valid conditional expression. If empty, the subject default parameters will apply. You can use the * token to extend the subject default parameters.",
					allowMultiple = true) 
			@QueryParam(FILTERS_PARAM) String[] filters,
			@ApiParam(value="maximum number of items to return per page") @QueryParam(MAX_RESULTS_PARAM) Integer maxResults,
			@ApiParam(value="index of the first item to start the page") @QueryParam(START_INDEX_PARAM) Integer startIndex,
			@ApiParam(value="optional timeout in milliseconds") @QueryParam(TIMEOUT_PARAM) Integer timeoutMs
			) throws ComputingException {

		AppContext userContext = getUserContext(request);
		return getDelegate().getFacet(userContext, BBID, facetId, search, filters, maxResults, startIndex, timeoutMs);
	}

	@POST
	@Path("{" + BBID_PARAM_NAME + "}/analysis")
	@ApiOperation(value = "Run a new Analysis based on the Bookmark scope")
	public AnalysisResult postAnalysis(
			@Context HttpServletRequest request, 
			@ApiParam(value="the analysis query definition", required=true) AnalysisQuery query,
			@PathParam(BBID_PARAM_NAME) String BBID,
			@ApiParam(value = "response timeout in milliseconds. If no timeout set, the method will return according to current job status.") 
			@QueryParam(TIMEOUT_PARAM) Integer timeout
			) throws ComputingException, ScopeException, InterruptedException {
		AppContext userContext = getUserContext(request);
		return getDelegate().runAnalysis(userContext, BBID, query, timeout);
	}

	@GET
	@Path("{" + BBID_PARAM_NAME + "}/analysis")
	@ApiOperation(value = "Compute an analysis for the subject")
	public AnalysisResult runAnalysis(
			@Context HttpServletRequest request, 
			@PathParam(BBID_PARAM_NAME) String BBID,
			// groupBy parameter
			@ApiParam(
					value = "Define the group-by facets to apply to results. Facet can be defined using it's ID or any valid expression. If empty, the subject default parameters will apply. You can use the * token to extend the subject default parameters.",
					allowMultiple = true
					) 
			@QueryParam(GROUP_BY_PARAM) String[] groupBy, 
			// metric parameter
			@ApiParam(
					value = "Define the metrics to compute. Metric can be defined using it's ID or any valid expression. If empty, the subject default parameters will apply. You can use the * token to extend the subject default parameters.",
					allowMultiple = true) 
			@QueryParam(METRICS_PARAM) String[] metrics, 
			@ApiParam(
					value = "Define the filters to apply to results. A filter must be a valid conditional expression. If empty, the subject default parameters will apply. You can use the * token to extend the subject default parameters.",
					allowMultiple = true) 
			@QueryParam(FILTERS_PARAM) String[] filterExpressions,
			@ApiParam(value="define the main period. It must be a valid expression of type Date, Time or Timestamp. If not set the API will use the default one or try to figure out a sensible choice.") 
			@QueryParam(PERIOD_PARAM) String period,
			@ApiParam(value="define the timeframe for the period. It can be a date range [lower,upper] or a special alias: ____ALL, ____LAST_DAY, ____LAST_7_DAYS, __CURRENT_MONTH, __PREVIOUS_MONTH, __CURRENT_MONTH, __PREVIOUS_YEAR", allowMultiple = true) 
			@QueryParam(TIMEFRAME_PARAM) String[] timeframe,
			@ApiParam(value="activate and define the compare to period. It can be a date range [lower,upper] or a special alias: __COMPARE_TO_PREVIOUS_PERIOD, __COMPARE_TO_PREVIOUS_MONTH, __COMPARE_TO_PREVIOUS_YEAR", allowMultiple = true) 
			@QueryParam(COMPAREFRAME_PARAM) String[] compareframe,
			@ApiParam(allowMultiple = true) 
			@QueryParam(ORDERBY_PARAM) String[] orderExpressions, 
			@ApiParam(allowMultiple = true) 
			@QueryParam(ROLLUP_PARAM) String[] rollupExpressions,
			@ApiParam(value="limit the resultset size as computed by the database. Note that this is independant from the paging size.")
			@QueryParam(LIMIT_PARAM) Long limit,
			@ApiParam(
					value="define the analysis data format.",
					allowableValues="LEGACY,SQL,CSV")
			@QueryParam(FORMAT_PARAM) String format,
			@ApiParam(value = "paging size") @QueryParam(MAX_RESULTS_PARAM) Integer maxResults,
			@ApiParam(value = "paging start index") @QueryParam(START_INDEX_PARAM) Integer startIndex,
			@ApiParam(value = "if true, get the analysis only if already in cache, else throw a NotInCacheException; if noError returns a null result if the analysis is not in cache ; else regular analysis", defaultValue = "false") 
			@QueryParam(LAZY_PARAM) String lazy,
			@ApiParam(
					value="define the result style. If HUMAN, the API will try to use natural reference for objects, like 'My First Project', 'Account', 'Total Sales'... If MACHINE the API will use canonical references that are invariant, e.g. @'5603ca63c531d744b50823a3bis'. If LEGACY the API will also provide internal compound key to lookup objects in the management API.", allowableValues="LEGACY, MACHINE, HUMAN", defaultValue="HUMAN")
			@QueryParam(STYLE_PARAM) Style style,
			@ApiParam(value = "response timeout in milliseconds. If no timeout set, the method will return according to current job status.") 
			@QueryParam(TIMEOUT_PARAM) Integer timeout
			) throws ComputingException, ScopeException, InterruptedException {
		AppContext userContext = getUserContext(request);
		AnalysisQuery analysis = createAnalysisFromParams(BBID, groupBy, metrics, filterExpressions, period, timeframe, compareframe, orderExpressions, rollupExpressions, limit, format, maxResults, startIndex, lazy, style);
		return getDelegate().runAnalysis(userContext, BBID, analysis, timeout);
	}


	@GET
	@Path("{" + BBID_PARAM_NAME + "}/vegalite")
	@ApiOperation(value = "Generate vegalite specs from a query")
	public VegaliteReply getVegalite(
			@Context HttpServletRequest request, 
			@PathParam(BBID_PARAM_NAME) String BBID,
			@ApiParam(
					value="set the x axis channel. This must be a valid expression or the special alias __PERIOD to refer to the main period.")
			@QueryParam("x") String x,
			@ApiParam(
					value="set the y axis channel. This must be a valid expression or the special alias __PERIOD to refer to the main period.")
			@QueryParam("y") String y,
			@ApiParam(
					value="set a series channel, displayed using a color palette. This must be a valid expression or the special alias __PERIOD to refer to the main period.")
			@QueryParam("color") String color,
			@ApiParam(
					value="set a series channel, displayed using the marker size. This must be a valid expression or the special alias __PERIOD to refer to the main period.")
			@QueryParam("size") String size,
			@ApiParam(
					value = "Define the filters to apply to results. A filter must be a valid conditional expression. If empty, the subject default parameters will apply. You can use the * token to extend the subject default parameters.",
					allowMultiple = true) 
			@QueryParam(FILTERS_PARAM) String[] filterExpressions,
			@ApiParam(value="define the main period. It must be a valid expression of type Date, Time or Timestamp. If not set the API will use the default one or try to figure out a sensible choice.") 
			@QueryParam(PERIOD_PARAM) String period,
			@ApiParam(value="define the timeframe for the period. It can be a date range [lower,upper] or a special alias: ____ALL, ____LAST_DAY, ____LAST_7_DAYS, __CURRENT_MONTH, __PREVIOUS_MONTH, __CURRENT_MONTH, __PREVIOUS_YEAR", allowMultiple = true) 
			@QueryParam(TIMEFRAME_PARAM) String[] timeframe,
			@ApiParam(value="limit the resultset size as computed by the database. Note that this is independant from the paging size.")
			@QueryParam(LIMIT_PARAM) Long limit,
			@ApiParam(
					value="define how to provide the data, either EMBEDED or through an URL",
					allowableValues="EMBEDED,URL", defaultValue="EMBEDED")
			@QueryParam("data") String data
	) throws ScopeException, ComputingException, InterruptedException
	{
		AppContext userContext = getUserContext(request);
		AnalysisQuery query = createAnalysisFromParams(BBID, null, null, filterExpressions, period, timeframe, null, null, null, limit, null, null, null, null, null);
		return getDelegate().getVegalite(uriInfo, userContext, BBID, x, y, color, size, data, query);
	}

	@GET
	@Path("{" + BBID_PARAM_NAME + "}/export/" + "{filename}")
	@ApiOperation(value = "Export an analysis results")
	public Response exportAnalysis(
			@Context HttpServletRequest request, 
			@PathParam(BBID_PARAM_NAME) String BBID,
			@PathParam("filename") String filename,
			// groupBy parameter
			@ApiParam(
					value = "Define the group-by facets to apply to results. Facet can be defined using it's ID or any valid expression. If empty, the subject default parameters will apply. You can use the * token to extend the subject default parameters.",
					allowMultiple = true
					) 
			@QueryParam(GROUP_BY_PARAM) String[] groupBy, 
			// metric parameter
			@ApiParam(
					value = "Define the metrics to compute. Metric can be defined using it's ID or any valid expression. If empty, the subject default parameters will apply. You can use the * token to extend the subject default parameters.",
					allowMultiple = true) 
			@QueryParam(METRICS_PARAM) String[] metrics, 
			@ApiParam(
					value = "Define the filters to apply to results. A filter must be a valid conditional expression. If empty, the subject default parameters will apply. You can use the * token to extend the subject default parameters.",
					allowMultiple = true) 
			@QueryParam(FILTERS_PARAM) String[] filterExpressions,
			@QueryParam(PERIOD_PARAM) String period,
			@ApiParam(value="define the timeframe for the period. It can be a date range [lower,upper] or a special alias: ____ALL, ____LAST_DAY, ____LAST_7_DAYS, __CURRENT_MONTH, __PREVIOUS_MONTH, __CURRENT_MONTH, __PREVIOUS_YEAR", allowMultiple = true) 
			@QueryParam(TIMEFRAME_PARAM) String[] timeframe,
			@ApiParam(value="activate and define the compare to period. It can be a date range [lower,upper] or a special alias: __COMPARE_TO_PREVIOUS_PERIOD, __COMPARE_TO_PREVIOUS_MONTH, __COMPARE_TO_PREVIOUS_YEAR", allowMultiple = true) 
			@QueryParam(COMPAREFRAME_PARAM) String[] compareframe,
			@ApiParam(allowMultiple = true) 
			@QueryParam(ORDERBY_PARAM) String[] orderExpressions, 
			@ApiParam(allowMultiple = true) 
			@QueryParam(ROLLUP_PARAM) String[] rollupExpressions,
			@QueryParam(LIMIT_PARAM) Long limit,
			@ApiParam(
					value="define the analysis data format.",
					allowableValues="JSON,SQL,CSV")
			@QueryParam(FORMAT_PARAM) String format
			) throws ComputingException, ScopeException, InterruptedException {
		AppContext userContext = getUserContext(request);
		String[] split = filename.split("\\.");
		String filepart = null;
		String fileext = null;
		String compression = null;
		if (split.length > 0) {
			filepart = split[0];
		}
		if (split.length > 1) {
			fileext = split[1];
		}
		if (split.length > 2) {
			compression = split[2];
			if (compression.equals("gz")) {
				compression = "gzip";
			}
		}
		AnalysisQuery analysis = createAnalysisFromParams(BBID, groupBy, metrics, filterExpressions, period, timeframe, compareframe, orderExpressions, rollupExpressions, limit, format, null, null, null, null);
		return getDelegate().exportAnalysis(userContext, BBID, analysis, filepart, fileext, compression);
	}
	
	@GET
    @Path("/status/{"+"QUERYID"+"}")
	@ApiOperation(value = "get the ongoing status of the analysis identified by its QueryID")
	public List<QueryWorkerJobStatus> getQuery(
			@Context HttpServletRequest request, 
			@PathParam("QUERYID") String key) {
		AppContext userContext = getUserContext(request);
		// first check if the query is available
		String customerId = userContext.getCustomerId();
		List<QueryWorkerJobStatus> queries = RedisCacheManager.getInstance().getQueryServer().getOngoingQueries(customerId);
		queries.addAll(DomainHierarchyManager.INSTANCE.getOngoingQueries(customerId));
		List<QueryWorkerJobStatus> results = new ArrayList<>();
		for (QueryWorkerJobStatus query : queries) {
			if (query.getJobID().equals(key)) {
				ProjectPK projectPK = query.getProjectPK();
				try {
					Project project = ProjectManager.INSTANCE.getProject(userContext, projectPK);
					// restrict to privileged user
					if (checkACL(userContext, project, query)) {
						results.add(query);
					}
				} catch (ScopeException e) {
					// ignore
				}
			}
		}
		//
		return results;
	}

	private boolean checkACL(AppContext userContext, Project project, QueryWorkerJobStatus query) {
		if (AccessRightsUtils.getInstance().hasRole(userContext, project, Role.WRITE)) {
			return true;
		}  else if (AccessRightsUtils.getInstance().hasRole(userContext, project, Role.READ)) {
			// or to the query owner
			if (query.getUserID().equals(userContext.getUser().getOid())) {
				return true;
			}
		}
		// else
		return false;
	}
	
	/**
	 * transform the GET query parameters into a AnalysisQuery similar to the one used for POST
	 * @param bBID 
	 * @param groupBy
	 * @param metrics
	 * @param filterExpressions
	 * @param orderExpressions
	 * @param rollupExpressions
	 * @param limit
	 * @return
	 * @throws ScopeException
	 */
	private AnalysisQuery createAnalysisFromParams(
			String BBID, 
			String[] groupBy, 
			String[] metrics, 
			String[] filterExpressions,
			String period,
			String[] timeframe,
			String[] compareframe,
			String[] orderExpressions, 
			String[] rollupExpressions, 
			Long limit,
			String format, 
			Integer maxResults, 
			Integer startIndex, 
			String lazy, 
			Style style
		) throws ScopeException {
		// init the analysis query using the query parameters
		AnalysisQuery query = new AnalysisQueryImpl();
		query.setBBID(BBID);
		int groupByLength = groupBy!=null?groupBy.length:0;
		if (groupByLength > 0) {
			query.setGroupBy(Arrays.asList(groupBy));
		}
		if ((metrics != null) && (metrics.length > 0)) {
			query.setMetrics(Arrays.asList(metrics));
		}
		if ((filterExpressions != null) && (filterExpressions.length > 0)) {
			query.setFilters(Arrays.asList(filterExpressions));
		}
		if (period!=null) {
			query.setPeriod(period);
		}
		if (timeframe != null && timeframe.length>0) {
			query.setTimeframe(timeframe);
		}
		if (compareframe != null && compareframe.length>0) {
			query.setCompareframe(compareframe);
		}
		if ((orderExpressions != null) && (orderExpressions.length > 0)) {
			List<OrderBy> orders = new ArrayList<OrderBy>();
			for (int i = 0; i < orderExpressions.length; i++) {
				OrderBy order = new OrderBy();
				order.setExpression(new Expression(orderExpressions[i]));
				orders.add(order);
			}
			query.setOrderBy(orders);
		}
		if ((rollupExpressions != null) && (rollupExpressions.length > 0)) {
			List<RollUp> rollups = new ArrayList<RollUp>();
			int pos = 1;
			for (int i = 0; i < rollupExpressions.length; i++) {
				// ok, do it quick...
				RollUp rollup = new RollUp();
				String expr = rollupExpressions[i].toLowerCase();
				Position position = Position.FIRST;// default
				if (expr.startsWith("last(")) {
					position = Position.LAST;
				}
				expr = expr.replaceAll("", "");
				try {
					int index = Integer.parseInt(expr);
					// rollup can use -1 to compute grand-total
					if (index < -1 || index >= groupByLength) {
						throw new ScopeException("invalid rollup expression at position " + pos
								+ ": the index specified (" + index + ") is not defined");
					}
					rollup.setCol(index);
					rollup.setPosition(position);
				} catch (NumberFormatException e) {
					throw new ScopeException("invalid rollup expression at position " + pos
							+ ": must be a valid indexe N or the expression FIRST(N) or LAST(N) to set the rollup position");
				}
				rollups.add(rollup);
			}
			query.setRollups(rollups);
		}
		if (limit!=null) query.setLimit(limit);
		if (format!=null) query.setFormat(format);
		if (maxResults!=null) query.setMaxResults(maxResults);
		if (startIndex!=null) query.setStartIndex(startIndex);
		if (lazy!=null) query.setLazy(lazy);
		if (style!=null) query.setStyle(style);
		return query;
	}
	
	public enum HierarchyMode {
		NONE, TREE, FLAT
	}

}