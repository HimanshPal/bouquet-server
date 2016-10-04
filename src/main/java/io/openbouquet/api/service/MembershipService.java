package io.openbouquet.api.service;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.openbouquet.api.model.Membership;

@Path("/membership")
@Produces(MediaType.APPLICATION_JSON)
public interface MembershipService {

	@GET
	public Membership get(@HeaderParam("Authorization") String authorization, @QueryParam("access_token") String token);
}
